package com.gu.devenv

import com.gu.devenv.Filesystem.{FileSystemStatus, GitignoreStatus}

import java.nio.file.Path
import scala.util.Try

object Devenv {
  def init(devcontainerDir: Path): Try[InitResult] = {
    val paths = resolveDevenvPaths(devcontainerDir)

    for {
      devcontainerStatus <- Filesystem.createDirIfNotExists(
        paths.devcontainerDir
      )
      userStatus <- Filesystem.createDirIfNotExists(paths.userDir)
      sharedStatus <- Filesystem.createDirIfNotExists(paths.sharedDir)
      gitignoreStatus <- Filesystem.setupGitignore(paths.gitignoreFile)
      devenvStatus <- Filesystem.setupDevenv(paths.devenvFile)
    } yield InitResult(
      devcontainerStatus,
      userStatus,
      sharedStatus,
      gitignoreStatus,
      devenvStatus
    )
  }

  def generate(
      devcontainerDir: Path,
      userConfigPath: Path
  ): Try[GenerateResult] = {
    val devEnvPaths = resolveDevenvPaths(devcontainerDir)
    val userPaths = resolveUserConfigPaths(userConfigPath)

    for {
      projectConfig <- Config.loadProjectConfig(devEnvPaths.devenvFile)
      maybeUserConfig <-
        Config.loadUserConfig(userPaths.devenvConf)
      mergedUserConfig = Config.mergeConfigs(projectConfig, maybeUserConfig)
      userJson = Config.configAsJson(mergedUserConfig)
      sharedJson = Config.configAsJson(projectConfig)
      userDevcontainerStatus <- Filesystem.writeFile(
        devEnvPaths.userDevcontainerFile,
        userJson.spaces2
      )
      sharedDevcontainerStatus <- Filesystem.writeFile(
        devEnvPaths.sharedDevcontainerFile,
        sharedJson.spaces2
      )
    } yield GenerateResult(
      userDevcontainerStatus,
      sharedDevcontainerStatus
    )
  }

  private def resolveDevenvPaths(devcontainerDir: Path): DevEnvPaths = {
    val userDir = devcontainerDir.resolve("user")
    val sharedDir = devcontainerDir.resolve("shared")
    DevEnvPaths(
      devcontainerDir = devcontainerDir,
      userDir = userDir,
      userDevcontainerFile = userDir.resolve("devcontainer.json"),
      sharedDir = sharedDir,
      sharedDevcontainerFile = sharedDir.resolve("devcontainer.json"),
      gitignoreFile = devcontainerDir.resolve(".gitignore"),
      devenvFile = devcontainerDir.resolve("devenv.conf")
    )
  }

  private def resolveUserConfigPaths(root: Path): UserConfigPaths = {
    UserConfigPaths(
      devenvConf = root.resolve("devenv.conf")
    )
  }

  private case class DevEnvPaths(
      devcontainerDir: Path,
      userDir: Path,
      userDevcontainerFile: Path,
      sharedDir: Path,
      sharedDevcontainerFile: Path,
      gitignoreFile: Path,
      devenvFile: Path
  )

  private case class UserConfigPaths(
      devenvConf: Path
  )

  case class InitResult(
      devcontainerStatus: FileSystemStatus,
      userStatus: FileSystemStatus,
      sharedStatus: FileSystemStatus,
      gitignoreStatus: GitignoreStatus,
      devenvStatus: FileSystemStatus
  )

  case class GenerateResult(
      userDevcontainerStatus: FileSystemStatus,
      sharedDevcontainerStatus: FileSystemStatus
  )
}
