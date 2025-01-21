package codacy.shellcheck

import java.io.{File => JFile}
import com.codacy.plugins.api._
import com.codacy.plugins.api.results.{Pattern, Result, Tool}
import com.codacy.tools.scala.seed.utils._
import com.codacy.tools.scala.seed.utils.ToolHelper._
import play.api.libs.json.{Json, OFormat}

import scala.util.Try

case class ShellCheckResult(file: String, line: Int, column: Int, level: String, code: Int, message: String)

object ShellCheckResult {
  implicit val shellCheckResult: OFormat[ShellCheckResult] = Json.format[ShellCheckResult]
}

object ShellCheck extends Tool {

  def apply(
      source: Source.Directory,
      configuration: Option[List[Pattern.Definition]],
      files: Option[Set[Source.File]],
      options: Map[Options.Key, Options.Value]
  )(implicit specification: Tool.Specification): Try[List[Result]] = {
    Try {
      val filesToLint: Seq[String] = files.fold {
        File(source.path).listRecursively
          .filter(f => f.extension == Option(".sh"))
          .map(_.toString)
          .toList
      } { paths =>
        paths.map(_.toString).toList
      }

      val command = List("shellcheck", "-x", "-f", "json") ++ filesToLint
      CommandRunner.exec(command, Option(new JFile(source.path.toString))) match {
        case Right(resultFromTool) =>
          parseToolResult(resultFromTool.stdout, source, configuration)
        case Left(failure) =>
          throw failure
      }
    }
  }

  private[this] def parseToolResult(
      resultFromTool: List[String],
      path: Source.Directory,
      configuration: Option[List[Pattern.Definition]]
  )(implicit spec: Tool.Specification): List[Result] = {
    val results = Try(Json.parse(resultFromTool.mkString)).toOption
      .flatMap(_.asOpt[List[ShellCheckResult]])
      .getOrElse(List.empty)
      .map { result =>
        Result.Issue(
          Source.File(result.file),
          Result.Message(result.message),
          Pattern.Id(s"SC${result.code}"),
          Source.Line(result.line)
        )
      }

    configuration.withDefaultParameters.fold {
      results
    } { patterns =>
      results.filter { r =>
        patterns.map(_.patternId).contains(r.patternId)
      }
    }
  }

}
