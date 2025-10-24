package com.gu.devenv

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.given
import scala.util.Try

object Filesystem {
  val PLACEHOLDER_PROJECT_NAME = "CHANGE_ME"

  def createDirIfNotExists(dir: Path): Try[FileSystemStatus] = Try {
    if (!Files.exists(dir)) {
      Files.createDirectory(dir)
      FileSystemStatus.Created
    } else {
      FileSystemStatus.AlreadyExists
    }
  }

  def readFile(path: Path): Try[String] = Try {
    val bytes = Files.readAllBytes(path)
    new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
  }

  /** Updates a file by overwriting its content, or creates it if it doesn't exist. Use this for
    * files that should be regenerated on each run (e.g., devcontainer.json). Do NOT use this for
    * init behavior where existing files should be preserved.
    */
  def updateFile(path: Path, content: String): Try[FileSystemStatus] = Try {
    val exists = Files.exists(path)
    Files.write(
      path,
      content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
    if (exists) FileSystemStatus.AlreadyExists else FileSystemStatus.Created
  }

  def setupGitignore(gitignoreFile: Path): Try[GitignoreStatus] = Try {
    if (!Files.exists(gitignoreFile)) {
      Files.write(
        gitignoreFile,
        gitignoreContents.getBytes,
        StandardOpenOption.CREATE_NEW
      )
      GitignoreStatus.Created
    } else {
      // check if the .gitignore file already includes the `user/` entry we want to add
      val hasUserExclusion = Files
        .lines(gitignoreFile)
        .iterator()
        .asScala
        .map(_.trim)
        .contains("user/")

      if (hasUserExclusion) {
        GitignoreStatus.AlreadyExistsWithExclusion
      } else {
        // Append our comment and user/ entry to the existing file
        Files.write(
          gitignoreFile,
          gitignoreContents.getBytes,
          StandardOpenOption.APPEND
        )
        GitignoreStatus.Updated
      }
    }
  }

  def setupDevenv(devenvFile: Path): Try[FileSystemStatus] = Try {
    if (!Files.exists(devenvFile)) {
      Files.write(
        devenvFile,
        devenvContents.getBytes,
        StandardOpenOption.CREATE_NEW
      )
      FileSystemStatus.Created
    } else {
      FileSystemStatus.AlreadyExists
    }
  }

  private val gitignoreContents =
    """|# User-specific devcontainer directory with merged personal preferences
       |user/
       |""".stripMargin

  private val devenvContents =
    """|# Devenv project configuration
       |# Edit this file to configure your project's devcontainer
       |
       |# REQUIRED: Change this to your project name
       |name: "CHANGE_ME"
       |
       |# Modules: Built-in functionality
       |# - apt-updates: Apply apt security updates during container creation
       |# - mise: Install mise for version management (https://mise.jdx.dev/)
       |# To disable, comment out or remove items from this list
       |modules:
       |  - apt-updates
       |  - mise
       |
       |# Optional: Container image to use (defaults to latest ubuntu LTS)
       |# image: "mcr.microsoft.com/devcontainers/base:ubuntu"
       |
       |# Optional: Ports to forward
       |# forwardPorts: []
       |
       |# Optional: IDE plugins
       |# plugins:
       |#   vscode: []
       |#   intellij: []
       |
       |# Optional: Commands to run after container creation
       |# postCreateCommand: []
       |# postStartCommand: []
       |""".stripMargin

  enum FileSystemStatus {
    case Created
    case AlreadyExists
  }

  enum GitignoreStatus {
    case Created
    case AlreadyExistsWithExclusion
    case Updated
  }
}
