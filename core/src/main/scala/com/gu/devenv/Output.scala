package com.gu.devenv

import com.gu.devenv.Devenv.{GenerateResult, InitResult}
import com.gu.devenv.Filesystem.GitignoreStatus
import fansi._

object Output {

  def initResultMessage(result: InitResult): String = {
    val rows = List(
      (".devcontainer/", formatInitStatus(result.devcontainerStatus)),
      (".devcontainer/user/", formatInitStatus(result.userStatus)),
      (".devcontainer/shared/", formatInitStatus(result.sharedStatus)),
      (
        ".devcontainer/.gitignore",
        formatGitignoreStatus(result.gitignoreStatus)
      ),
      (".devcontainer/.devenv", formatInitStatus(result.devenvStatus))
    )

    val header = Bold.On("Initialization Summary:")
    val divider = Color.LightBlue("━" * 60)

    val tableRows = rows
      .map { case (path, (emoji, text, colorFn)) =>
        val paddedPath = path.padTo(32, ' ')
        s"  $emoji ${Color.Cyan(paddedPath)} ${colorFn(text)}"
      }
      .mkString("\n")

    val table = s"$header\n$divider\n$tableRows\n$divider"

    val gitignoreWarning = result.gitignoreStatus match {
      case GitignoreStatus.AlreadyExistsWithoutExclusion =>
        Some(
          s"\n\n${Bold.On(Color.Yellow("⚠️  WARNING ⚠️"))}\n" +
            Color.Yellow("━" * 60) + "\n" +
            Color.Red(
              "The .devcontainer/.gitignore file exists but does not\n" +
                "contain the required 'user/' exclusion!\n\n" +
                "This means your user-specific settings could be\n" +
                "committed to the repository.\n\n"
            ) +
            "Please add the following line to .devcontainer/.gitignore:\n" +
            s"  ${Color.Green("user/")}\n" +
            Color.Yellow("━" * 60)
        )
      case _ => None
    }

    val nextSteps = result.gitignoreStatus match {
      case GitignoreStatus.AlreadyExistsWithoutExclusion => ""
      case _ =>
        "\n\n" + Bold.On("Next steps:") + "\n" +
          s"  1. Edit ${Color.Cyan(".devcontainer/devenv")} to configure your project\n" +
          s"  2. Run ${Bold.On("devenv generate")} to create devcontainer files"
    }

    val parts = List(Some(table), gitignoreWarning, Some(nextSteps)).flatten
    parts.mkString("")
  }

  def generateResultMessage(result: GenerateResult): String = {
    val rows = List(
      (
        ".devcontainer/user/devcontainer.json",
        formatGenerateStatus(result.userDevcontainerStatus)
      ),
      (
        ".devcontainer/shared/devcontainer.json",
        formatGenerateStatus(result.sharedDevcontainerStatus)
      )
    )

    val header = Bold.On("Generation Summary:")
    val divider = Color.LightBlue("━" * 60)

    val tableRows = rows
      .map { case (path, (emoji, text, colorFn)) =>
        val paddedPath = path.padTo(47, ' ')
        s"  $emoji ${Color.Cyan(paddedPath)} ${colorFn(text)}"
      }
      .mkString("\n")

    val table = s"$header\n$divider\n$tableRows\n$divider"

    val nextSteps = "\n\n" + Bold.On("You can now:") + "\n" +
      s"  ${Color.Green("•")} Open the project in your IDE and reopen in container\n" +
      s"  ${Color.Green("•")} Use the shared config for cloud-based development"

    table + nextSteps
  }

  private def formatInitStatus(
      status: Filesystem.FileSystemStatus
  ): (String, String, Str => Str) = {
    import Filesystem.FileSystemStatus
    status match {
      case FileSystemStatus.Created => ("✅", "Created", s => Color.Green(s))
      case FileSystemStatus.AlreadyExists =>
        ("⚪", "Already exists", s => Color.LightGray(s))
    }
  }

  private def formatGitignoreStatus(
      status: Filesystem.GitignoreStatus
  ): (String, String, Str => Str) = {
    import Filesystem.GitignoreStatus
    status match {
      case GitignoreStatus.Created => ("✅", "Created", s => Color.Green(s))
      case GitignoreStatus.AlreadyExistsWithExclusion =>
        ("⚪", "Already exists", s => Color.LightGray(s))
      case GitignoreStatus.AlreadyExistsWithoutExclusion =>
        ("❌", "No user/ entry", s => Color.Red(s))
    }
  }

  private def formatGenerateStatus(
      status: Filesystem.FileSystemStatus
  ): (String, String, Str => Str) = {
    import Filesystem.FileSystemStatus
    status match {
      case FileSystemStatus.Created => ("✅", "Created", s => Color.Green(s))
      case FileSystemStatus.AlreadyExists =>
        ("🔄", "Updated", s => Color.Green(s))
    }
  }
}
