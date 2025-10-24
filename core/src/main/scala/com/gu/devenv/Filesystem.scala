package com.gu.devenv

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.given
import scala.util.Try

object Filesystem {
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
  
  def writeFile(path: Path, content: String): Try[FileSystemStatus] = Try {
    if (!Files.exists(path)) {
      Files.write(
        path,
        content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        StandardOpenOption.CREATE_NEW
      )
      FileSystemStatus.Created
    } else {
      FileSystemStatus.AlreadyExists
    }
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
        GitignoreStatus.AlreadyExistsWithoutExclusion
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
       |# Add your project-specific devcontainer settings here
       |""".stripMargin

  enum FileSystemStatus {
    case Created
    case AlreadyExists
  }

  enum GitignoreStatus {
    case Created
    case AlreadyExistsWithExclusion
    case AlreadyExistsWithoutExclusion
  }
}
