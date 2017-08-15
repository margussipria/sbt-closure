package eu.sipria.sbt.closure

import java.nio.charset.StandardCharsets

import com.google.common.collect.ImmutableList
import com.google.javascript.jscomp.{Compiler, CompilerOptions, SourceFile}
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.web.pipeline.Pipeline.Stage
import com.typesafe.sbt.web.{PathMapping, SbtWeb}
import sbt.Keys._
import sbt._

import scala.collection.mutable.ListBuffer

private[closure] object Import {
  val closure: TaskKey[Stage] = taskKey[Stage]("Runs JavaScript web assets through the Google closure compiler")

  sealed trait CompilationLevel
  object CompilationLevel {
    case object WHITESPACE extends CompilationLevel
    case object SIMPLE extends CompilationLevel
    case object ADVANCED extends CompilationLevel
  }

  final case class ListOfFiles(files: Seq[String] = Nil, paths: PathFinder = PathFinder.empty)

  object ListOfFiles {
    def apply(paths: PathFinder, files: Seq[String]): ListOfFiles = {
      ListOfFiles(files, paths)
    }
    def apply(paths: PathFinder): ListOfFiles = {
      ListOfFiles(Nil, paths)
    }
  }

  object Closure {
    val flags: SettingKey[Seq[String]] = settingKey[Seq[String]]("Command line flags to pass to the closure compiler, example: Seq(\"--formatting=PRETTY_PRINT\", \"--accept_const_keyword\")")
    val suffix: SettingKey[String] = settingKey[String]("Suffix to append to compiled files, default: \".min.js\"")
    val compilationLevel: SettingKey[CompilationLevel] = settingKey[CompilationLevel]("Compilation level of closure")
    val defaultOptions: SettingKey[PartialFunction[String, (CompilerOptions => Unit)]] = {
      settingKey[PartialFunction[String, (CompilerOptions => Unit)]]("Default options for closure compiler")
    }
    val extraOptions: SettingKey[PartialFunction[String, (CompilerOptions => Unit)]] = {
      settingKey[PartialFunction[String, (CompilerOptions => Unit)]]("Extra options for closure compiler")
    }
    val createCompilerOptions: SettingKey[String => CompilerOptions] = {
      settingKey[String => CompilerOptions]("Create compiler options for closure compiler")
    }

    val groupFiles: SettingKey[Seq[(String, ListOfFiles)]] = {
      SettingKey[Seq[(String, ListOfFiles)]]("Compile multiple files to one js file")
    }

    val excludeOriginal: SettingKey[Boolean] = settingKey[Boolean]("Exclude original file from final pipeline map")
    val excludeGrouped: SettingKey[Boolean] = settingKey[Boolean]("Exclude grouped files for normal compiling")
  }
}

class UncompiledJsFileFilter(suffix: String) extends FileFilter {
  override def accept(file: File): Boolean = {
    // visible
    !HiddenFileFilter.accept(file) &&
    // not already compiled
    !file.getName.endsWith(suffix) &&
    // a JS file
    file.getName.endsWith(".js")
  }
}

object SbtClosure extends AutoPlugin {
  override def requires = SbtWeb

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._
  import Closure._

  override def projectSettings = Seq(
    flags := ListBuffer.empty[String],
    suffix := ".min.js",
    compilationLevel := CompilationLevel.SIMPLE,
    defaultOptions in closure := PartialFunction.empty,
    extraOptions in closure := PartialFunction.empty,
    createCompilerOptions in closure := { file: String =>
      val options = new CompilerOptions()

      (defaultOptions in closure).value.applyOrElse(file, { _: String => options: CompilerOptions =>
        import com.google.javascript.jscomp.{CompilationLevel => ClosureCompilationLevel}

        {
          compilationLevel.value match {
            case CompilationLevel.WHITESPACE  => ClosureCompilationLevel.WHITESPACE_ONLY
            case CompilationLevel.SIMPLE      => ClosureCompilationLevel.SIMPLE_OPTIMIZATIONS
            case CompilationLevel.ADVANCED    => ClosureCompilationLevel.ADVANCED_OPTIMIZATIONS
          }
        }.setOptionsForCompilationLevel(options)
      })(options)

      val myExtraOptions = (extraOptions in closure).value
      if (myExtraOptions.isDefinedAt(file)) {
        myExtraOptions.apply(file)(options)
      }

      options
    },
    includeFilter in closure := new UncompiledJsFileFilter(suffix.value),
    excludeFilter in closure := HiddenFileFilter,

    groupFiles := Seq.empty,
    excludeOriginal := false,
    excludeGrouped := false,

    closure := closureCompile.value
  )

  private def closureCompile: Def.Initialize[Task[Pipeline.Stage]] = Def.task { mappings: Seq[PathMapping] =>
    val include = (includeFilter in closure).value
    val exclude = (excludeFilter in closure).value

    val targetDir = webTarget.value / closure.key.label
    val compileMappings = mappings.view
      .filter(m => include.accept(m._1))
      .filterNot(m => exclude.accept(m._1))
      .toMap

    val resolvedGroupFiles = (groupFiles in closure).value.map { case (groupName, listOfFiles) =>
      val files = {
        listOfFiles.files.map { file =>
          mappings.find(_._2 == file).getOrElse {
            sys.error(s"Unable to find file: $file. Not found.")
          }
        } ++ {
          listOfFiles.paths
            .pair(relativeTo((sourceDirectories in Assets).value ++ (webModuleDirectories in Assets).value) | flat)
        }
      }

      (groupName, files.toMap)
    }

    val compiledGroupFiles = resolvedGroupFiles.flatMap { case (outputFileSubPath, listOfFiles) =>
      val outputFile = targetDir / outputFileSubPath

      val compiler = FileFunction.cached(
        streams.value.cacheDirectory / closure.key.label / outputFileSubPath,
        FilesInfo.hash
      ) { files =>

        IO.createDirectory(outputFile.getParentFile)
        streams.value.log.info(s"Closure compiler executing on file $outputFileSubPath")

        val compiler = new Compiler

        val options = (createCompilerOptions in closure).value(outputFileSubPath)

        import collection.JavaConverters._

        val result = compiler.compile(
          ImmutableList.of[SourceFile](),
          files.map(_.getAbsolutePath).map(SourceFile.fromFile).toList.asJava,
          options
        )

        if (result.success) {
          IO.write(outputFile, compiler.toSource, StandardCharsets.UTF_8)
        } else {
          compiler.getErrorManager.getErrors.map(_.description).foreach(streams.value.log.error(_))
        }

        Set(outputFile)
      }

      compiler(listOfFiles.keySet).map { outputFile =>
        val relativePath = IO.relativize(targetDir, outputFile).getOrElse {
          sys.error(s"Cannot find $outputFile path relative to $targetDir")
        }
        (outputFile, relativePath)
      }.toSeq
    }

    // Only do work on files which have been modified
    val runCompiler = FileFunction.cached(streams.value.cacheDirectory / closure.key.label, FilesInfo.hash) { files =>
      files.map { f =>
        val file = compileMappings(f)

        val outputFileSubPath = IO.split(file)._1 + suffix.value
        val outputFile = targetDir / outputFileSubPath

        IO.createDirectory(outputFile.getParentFile)
        streams.value.log.info(s"Closure compiler executing on file $file")

        val compiler = new Compiler

        val options = (createCompilerOptions in closure).value(file)

        val code = SourceFile.fromFile(f.getAbsolutePath)
        val result = compiler.compile(
          ImmutableList.of[SourceFile](),
          ImmutableList.of[SourceFile](code),
          options
        )

        if (result.success) {
          IO.write(outputFile, compiler.toSource, StandardCharsets.UTF_8)
        } else {
          compiler.getErrorManager.getErrors.map(_.description).foreach(streams.value.log.error(_))
        }

        outputFile
      }
    }

    val excludeGroupedFiles = if ((excludeGrouped in closure).value) {
      resolvedGroupFiles.flatMap(_._2.keys).toSet
    } else Set.empty[File]

    val compiled = runCompiler(compileMappings.keySet.diff(excludeGroupedFiles)).map { outputFile =>
      val relativePath = IO.relativize(targetDir, outputFile).getOrElse {
        sys.error(s"Cannot find $outputFile path relative to $targetDir")
      }
      (outputFile, relativePath)
    }.toSeq

    compiled ++ compiledGroupFiles ++ {
      if ((excludeOriginal in closure).value) {
        mappings.diff(compileMappings.toSeq).diff(resolvedGroupFiles.flatMap(_._2))
      } else {
        mappings
      }
    }.filter {
      case (_, mappingName) =>
        val include = !compiled.exists(_._2 == mappingName)
        if (!include) {
          streams.value.log.warn(
            s"Closure compiler encountered a duplicate mapping for $mappingName and will " +
              "prefer the closure compiled version instead. If you want to avoid this, make sure you aren't " +
              "including minified and non-minified sibling assets in the pipeline."
          )
        }
        include
    }
  }
}
