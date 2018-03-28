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

private[closure] trait ClosureKeys {
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

  val closureFlags: SettingKey[Seq[String]] = settingKey[Seq[String]]("Command line flags to pass to the closure compiler, example: Seq(\"--formatting=PRETTY_PRINT\", \"--accept_const_keyword\")")
  val closureSuffix: SettingKey[String] = settingKey[String]("Suffix to append to compiled files, default: \".min.js\"")
  val closureCompilationLevel: SettingKey[CompilationLevel] = settingKey[CompilationLevel]("Compilation level of closure")
  val closureDefaultOptions: SettingKey[PartialFunction[String, (CompilerOptions => Unit)]] = {
    settingKey[PartialFunction[String, (CompilerOptions => Unit)]]("Default options for closure compiler")
  }
  val closureExtraOptions: SettingKey[PartialFunction[String, (CompilerOptions => Unit)]] = {
    settingKey[PartialFunction[String, (CompilerOptions => Unit)]]("Extra options for closure compiler")
  }
  val closureCreateCompilerOptions: SettingKey[String => CompilerOptions] = {
    settingKey[String => CompilerOptions]("Create compiler options for closure compiler")
  }

  val closureGroupFiles: SettingKey[Seq[(String, ListOfFiles)]] = {
    settingKey[Seq[(String, ListOfFiles)]]("Compile multiple files to one js file")
  }

  val closureExcludeOriginal: SettingKey[Boolean] = settingKey[Boolean]("Exclude original file from final pipeline map")
  val closureExcludeGrouped: SettingKey[Boolean] = settingKey[Boolean]("Exclude grouped files for normal compiling")
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
  override def requires: Plugins = SbtWeb

  override def trigger = AllRequirements

  object autoImport extends ClosureKeys

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    closureFlags := ListBuffer.empty[String],
    closureSuffix := ".min.js",
    closureCompilationLevel := CompilationLevel.SIMPLE,
    closureDefaultOptions := PartialFunction.empty,
    closureExtraOptions := PartialFunction.empty,
    closureCreateCompilerOptions := { file: String =>
      val options = new CompilerOptions()

      closureDefaultOptions.value.applyOrElse(file, { _: String => options: CompilerOptions =>
        import com.google.javascript.jscomp.{CompilationLevel => ClosureCompilationLevel}

        {
          closureCompilationLevel.value match {
            case CompilationLevel.WHITESPACE  => ClosureCompilationLevel.WHITESPACE_ONLY
            case CompilationLevel.SIMPLE      => ClosureCompilationLevel.SIMPLE_OPTIMIZATIONS
            case CompilationLevel.ADVANCED    => ClosureCompilationLevel.ADVANCED_OPTIMIZATIONS
          }
        }.setOptionsForCompilationLevel(options)
      })(options)

      val myExtraOptions = closureExtraOptions.value
      if (myExtraOptions.isDefinedAt(file)) {
        myExtraOptions.apply(file)(options)
      }

      options
    },
    includeFilter in closure := new UncompiledJsFileFilter(closureSuffix.value),
    excludeFilter in closure := HiddenFileFilter,

    closureGroupFiles := Seq.empty,
    closureExcludeOriginal := false,
    closureExcludeGrouped := false,

    closure := closureCompile.value
  )

  private def closureCompile: Def.Initialize[Task[Pipeline.Stage]] = Def.taskDyn {

    val taskStreams: TaskStreams = streams.value

    Def.task { mappings: Seq[PathMapping] =>

      val include = (includeFilter in closure).value
      val exclude = (excludeFilter in closure).value

      val targetDir = webTarget.value / closure.key.label
      val compileMappings = mappings.view
        .filter(m => include.accept(m._1))
        .filterNot(m => exclude.accept(m._1))
        .toMap

      val resolvedGroupFiles = closureGroupFiles.value.map { case (groupName, listOfFiles) =>
        val files = {
          listOfFiles.files.map { file =>
            mappings.find(_._2 == file).getOrElse {
              sys.error(s"Unable to find file: $file. Not found.")
            }
          } ++ {
            listOfFiles.paths
              .pair(Path.relativeTo((sourceDirectories in Assets).value ++ (webModuleDirectories in Assets).value) | Path.flat)
          }
        }

        (groupName, files.toMap)
      }

      val compiledGroupFiles = resolvedGroupFiles.flatMap { case (outputFileSubPath, listOfFiles) =>
        val outputFile = targetDir / outputFileSubPath

        val compiler = FileFunction.cached(
          taskStreams.cacheDirectory / closure.key.label / outputFileSubPath,
          FilesInfo.hash
        ) { files =>

          IO.createDirectory(outputFile.getParentFile)
          taskStreams.log.info(s"Closure compiler executing on file $outputFileSubPath")

          val compiler = new Compiler

          val options = closureCreateCompilerOptions.value(outputFileSubPath)

          import collection.JavaConverters._

          val result = compiler.compile(
            ImmutableList.of[SourceFile](),
            files.map(_.getAbsolutePath).map(SourceFile.fromFile).toList.asJava,
            options
          )

          if (result.success) {
            IO.write(outputFile, compiler.toSource, StandardCharsets.UTF_8)
          } else {
            compiler.getErrorManager.getErrors.map(_.description).foreach(taskStreams.log.error(_))
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
      val runCompiler = FileFunction.cached(taskStreams.cacheDirectory / closure.key.label, FilesInfo.hash) { files =>
        files.map { f =>
          val file = compileMappings(f)

          val outputFileSubPath = IO.split(file)._1 + closureSuffix.value
          val outputFile = targetDir / outputFileSubPath

          IO.createDirectory(outputFile.getParentFile)
          taskStreams.log.info(s"Closure compiler executing on file $file")

          val compiler = new Compiler

          val options = closureCreateCompilerOptions.value(file)

          val code = SourceFile.fromFile(f.getAbsolutePath)
          val result = compiler.compile(
            ImmutableList.of[SourceFile](),
            ImmutableList.of[SourceFile](code),
            options
          )

          if (result.success) {
            IO.write(outputFile, compiler.toSource, StandardCharsets.UTF_8)
          } else {
            compiler.getErrorManager.getErrors.map(_.description).foreach(taskStreams.log.error(_))
          }

          outputFile
        }
      }

      val excludeGroupedFiles = if (closureExcludeGrouped.value) {
        resolvedGroupFiles.flatMap(_._2.keys).toSet
      } else Set.empty[File]

      val compiled = runCompiler(compileMappings.keySet.diff(excludeGroupedFiles)).map { outputFile =>
        val relativePath = IO.relativize(targetDir, outputFile).getOrElse {
          sys.error(s"Cannot find $outputFile path relative to $targetDir")
        }
        (outputFile, relativePath)
      }.toSeq

      compiled ++ compiledGroupFiles ++ {
        if (closureExcludeOriginal.value) {
          mappings.diff(compileMappings.toSeq).diff(resolvedGroupFiles.flatMap(_._2))
        } else {
          mappings
        }
      }.filter {
        case (_, mappingName) =>
          val include = !compiled.exists(_._2 == mappingName)
          if (!include) {
            taskStreams.log.warn(
              s"Closure compiler encountered a duplicate mapping for $mappingName and will " +
                "prefer the closure compiled version instead. If you want to avoid this, make sure you aren't " +
                "including minified and non-minified sibling assets in the pipeline."
            )
          }
          include
      }
    }
  }
}
