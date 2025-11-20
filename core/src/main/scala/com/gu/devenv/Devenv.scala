package com.gu.devenv

import cats._
import com.gu.devenv.Filesystem.PLACEHOLDER_PROJECT_NAME

import java.nio.file.Path
import scala.util.Try
import scala.language.implicitConversions
import Utils.*

object Devenv {

  /** Sets up the .devcontainer directory structure with necessary subdirectories and files.
    *
    * The provided path is treated as the root .devcontainer directory where the following will be
    * created:
    *   - user/ (for user-specific devcontainer with merged preferences)
    *   - shared/ (for project-specific devcontainer that can be checked in)
    *   - .gitignore (to exclude user directory)
    *   - devenv.yaml (a 'blank' project-specific configuration file)
    */
  def init(devcontainerDir: Path): Try[InitResult] = {
    val devEnvPaths = Filesystem.resolveDevenvPaths(devcontainerDir)

    for {
      devcontainerStatus <- Filesystem.createDirIfNotExists(
        devEnvPaths.devcontainerDir
      )
      userStatus      <- Filesystem.createDirIfNotExists(devEnvPaths.userDir)
      sharedStatus    <- Filesystem.createDirIfNotExists(devEnvPaths.sharedDir)
      gitignoreStatus <- Filesystem.setupGitignore(devEnvPaths.gitignoreFile)
      devenvStatus    <- Filesystem.setupDevenv(devEnvPaths.devenvFile)
    } yield InitResult(
      devcontainerStatus,
      userStatus,
      sharedStatus,
      gitignoreStatus,
      devenvStatus
    )
  }

  /** Generates the devcontainer.json files for user-specific and shared configurations by merging
    * the project and user configurations.
    *
    * Uses the provided paths to locate the .devcontainer directory and the user's configuration
    * file.
    */
  def generate(
      devcontainerDir: Path,
      userConfigPath: Path
  ): Try[GenerateResult] = withConditions {
    val devEnvPaths = Filesystem.resolveDevenvPaths(devcontainerDir)
    val userPaths   = Filesystem.resolveUserConfigPaths(userConfigPath)

    for {
      // exit early if the devenv directory does not exist
      _ <- condition(
        !java.nio.file.Files.exists(devEnvPaths.devenvFile),
        GenerateResult.NotInitialized
      )
      projectConfig <- Config.loadProjectConfig(devEnvPaths.devenvFile).liftF
      // exit early if the project config has not been configured
      _ <- condition(
        projectConfig.name == PLACEHOLDER_PROJECT_NAME,
        GenerateResult.ConfigNotCustomized
      )
      maybeUserConfig        <- Config.loadUserConfig(userPaths.devenvConf).liftF
      (userJson, sharedJson) <- Config.generateConfigs(projectConfig, maybeUserConfig).liftF
      userDevcontainerStatus <- Filesystem
        .updateFile(devEnvPaths.userDevcontainerFile, userJson)
        .liftF
      sharedDevcontainerStatus <- Filesystem
        .updateFile(devEnvPaths.sharedDevcontainerFile, sharedJson)
        .liftF
    } yield GenerateResult.Success(
      userDevcontainerStatus,
      sharedDevcontainerStatus
    )
  }

  /** Checks if the existing devcontainer.json files match the expected content based on the current
    * configuration.
    *
    * This may be useful in CI/CD pipelines to ensure that the committed devcontainer files are
    * up-to-date with the project's configuration.
    *
    * Uses the provided paths to locate the .devcontainer directory and the user's configuration
    * file.
    */
  def check(
      devcontainerDir: Path,
      userConfigPath: Path
  ): Try[CheckResult] = withConditions {
    val devEnvPaths = Filesystem.resolveDevenvPaths(devcontainerDir)
    val userPaths   = Filesystem.resolveUserConfigPaths(userConfigPath)

    for {
      // exit early if the devenv directory does not exist
      _ <- condition(
        !java.nio.file.Files.exists(devEnvPaths.devenvFile),
        CheckResult.NotInitialized
      )
      projectConfig <- Config.loadProjectConfig(devEnvPaths.devenvFile).liftF
      // exit early if the project config has not been configured
      _ <- condition(
        projectConfig.name == PLACEHOLDER_PROJECT_NAME,
        CheckResult.NotInitialized
      )
      maybeUserConfig <- Config.loadUserConfig(userPaths.devenvConf).liftF
      (expectedUserJson, expectedSharedJson) <- Config
        .generateConfigs(
          projectConfig,
          maybeUserConfig
        )
        .liftF
      actualUserJson <- Filesystem
        .readFile(devEnvPaths.userDevcontainerFile)
        .recover { case _ => "" }
        .liftF
      actualSharedJson <- Filesystem
        .readFile(devEnvPaths.sharedDevcontainerFile)
        .recover { case _ => "" }
        .liftF
    } yield Config.compareDevcontainerFiles(
      expectedUserJson,
      actualUserJson,
      expectedSharedJson,
      actualSharedJson,
      devcontainerDir
    )
  }
}
