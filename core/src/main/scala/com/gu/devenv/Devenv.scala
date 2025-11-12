package com.gu.devenv

import com.gu.devenv.Filesystem.{FileSystemStatus, GitignoreStatus, PLACEHOLDER_PROJECT_NAME}

import java.nio.file.Path
import scala.util.{Success, Try}

object Devenv {
  def init(devcontainerDir: Path): Try[InitResult] = {
    val paths = resolveDevenvPaths(devcontainerDir)

    for {
      devcontainerStatus <- Filesystem.createDirIfNotExists(
        paths.devcontainerDir
      )
      userStatus      <- Filesystem.createDirIfNotExists(paths.userDir)
      sharedStatus    <- Filesystem.createDirIfNotExists(paths.sharedDir)
      gitignoreStatus <- Filesystem.setupGitignore(paths.gitignoreFile)
      devenvStatus    <- Filesystem.setupDevenv(paths.devenvFile)
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
    val userPaths   = resolveUserConfigPaths(userConfigPath)

    // Check if project has been initialized with a devenv configuration file
    if (!java.nio.file.Files.exists(devEnvPaths.devenvFile)) {
      Success(GenerateResult.NotInitialized)
    } else {
      for {
        projectConfig <- Config.loadProjectConfig(devEnvPaths.devenvFile)
        result <- {
          // Check if the config has been customized
          if (projectConfig.name == PLACEHOLDER_PROJECT_NAME) {
            Success(GenerateResult.ConfigNotCustomized)
          } else {
            for {
              maybeUserConfig        <- Config.loadUserConfig(userPaths.devenvConf)
              (userJson, sharedJson) <- generateConfigs(projectConfig, maybeUserConfig)
              userDevcontainerStatus <- Filesystem.updateFile(
                devEnvPaths.userDevcontainerFile,
                userJson
              )
              sharedDevcontainerStatus <- Filesystem.updateFile(
                devEnvPaths.sharedDevcontainerFile,
                sharedJson
              )
            } yield GenerateResult.Success(
              userDevcontainerStatus,
              sharedDevcontainerStatus
            )
          }
        }
      } yield result
    }
  }

  def check(
      devcontainerDir: Path,
      userConfigPath: Path
  ): Try[CheckResult] = {
    val devEnvPaths = resolveDevenvPaths(devcontainerDir)
    val userPaths   = resolveUserConfigPaths(userConfigPath)

    // Check if project has been initialized with a devenv configuration file
    if (!java.nio.file.Files.exists(devEnvPaths.devenvFile)) {
      Success(CheckResult.NotInitialized)
    } else {
      for {
        projectConfig <- Config.loadProjectConfig(devEnvPaths.devenvFile)
        result <- {
          // Check if the config has been customized
          if (projectConfig.name == PLACEHOLDER_PROJECT_NAME) {
            Success(CheckResult.NotInitialized)
          } else {
            checkDevcontainerFiles(
              devEnvPaths,
              userPaths,
              projectConfig,
              devcontainerDir
            )
          }
        }
      } yield result
    }
  }

  private def checkDevcontainerFiles(
      devEnvPaths: DevEnvPaths,
      userPaths: UserConfigPaths,
      projectConfig: ProjectConfig,
      devcontainerDir: Path
  ): Try[CheckResult] =
    for {
      maybeUserConfig                        <- Config.loadUserConfig(userPaths.devenvConf)
      (expectedUserJson, expectedSharedJson) <- generateConfigs(projectConfig, maybeUserConfig)
      actualUserJson <- Filesystem
        .readFile(devEnvPaths.userDevcontainerFile)
        .recover { case _ => "" }
      actualSharedJson <- Filesystem
        .readFile(devEnvPaths.sharedDevcontainerFile)
        .recover { case _ => "" }
    } yield compareDevcontainerFiles(
      expectedUserJson,
      actualUserJson,
      expectedSharedJson,
      actualSharedJson,
      devcontainerDir
    )

  private def compareDevcontainerFiles(
      expectedUserJson: String,
      actualUserJson: String,
      expectedSharedJson: String,
      actualSharedJson: String,
      devcontainerDir: Path
  ): CheckResult = {
    val userDevcontainerPath   = s"${devcontainerDir.getFileName}/user/devcontainer.json"
    val sharedDevcontainerPath = s"${devcontainerDir.getFileName}/shared/devcontainer.json"

    val userMismatch = if (expectedUserJson != actualUserJson) {
      Some(FileDiff(userDevcontainerPath, expected = expectedUserJson, actualUserJson))
    } else None

    val sharedMismatch = if (expectedSharedJson != actualSharedJson) {
      Some(
        FileDiff(sharedDevcontainerPath, expected = expectedSharedJson, actual = actualSharedJson)
      )
    } else None

    if (userMismatch.isEmpty && sharedMismatch.isEmpty) {
      CheckResult.Match(userDevcontainerPath, sharedDevcontainerPath)
    } else {
      CheckResult.Mismatch(
        userMismatch,
        sharedMismatch,
        userDevcontainerPath,
        sharedDevcontainerPath
      )
    }
  }

  private def generateConfigs(
      projectConfig: ProjectConfig,
      maybeUserConfig: Option[UserConfig]
  ): Try[(String, String)] = {
    val mergedUserConfig = Config.mergeConfigs(projectConfig, maybeUserConfig)
    for {
      userJson   <- Config.configAsJson(mergedUserConfig)
      sharedJson <- Config.configAsJson(projectConfig)
    } yield (userJson.spaces2, sharedJson.spaces2)
  }

  private def resolveDevenvPaths(devcontainerDir: Path): DevEnvPaths = {
    val userDir   = devcontainerDir.resolve("user")
    val sharedDir = devcontainerDir.resolve("shared")
    DevEnvPaths(
      devcontainerDir = devcontainerDir,
      userDir = userDir,
      userDevcontainerFile = userDir.resolve("devcontainer.json"),
      sharedDir = sharedDir,
      sharedDevcontainerFile = sharedDir.resolve("devcontainer.json"),
      gitignoreFile = devcontainerDir.resolve(".gitignore"),
      devenvFile = devcontainerDir.resolve("devenv.yaml")
    )
  }

  private def resolveUserConfigPaths(root: Path): UserConfigPaths =
    UserConfigPaths(
      devenvConf = root.resolve("devenv.yaml")
    )

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

  sealed trait GenerateResult
  object GenerateResult {
    case class Success(
        userDevcontainerStatus: FileSystemStatus,
        sharedDevcontainerStatus: FileSystemStatus
    ) extends GenerateResult
    case object NotInitialized      extends GenerateResult
    case object ConfigNotCustomized extends GenerateResult
  }

  sealed trait CheckResult
  object CheckResult {
    case class Match(
        userPath: String,
        sharedPath: String
    ) extends CheckResult
    case class Mismatch(
        userMismatch: Option[FileDiff],
        sharedMismatch: Option[FileDiff],
        userPath: String,
        sharedPath: String
    ) extends CheckResult
    case object NotInitialized extends CheckResult
  }

  case class FileDiff(
      path: String,
      expected: String,
      actual: String
  )
}
