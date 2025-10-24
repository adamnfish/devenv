package com.gu.devenv

import io.circe.Json
import io.circe.syntax.*
import io.circe.JsonObject

import scala.util.Try
import io.circe.yaml.scalayaml.parser
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto.*

import java.nio.file.Path

object Config {
  implicit val config: Configuration = Configuration.default.withDefaults

  def loadProjectConfig(path: Path): Try[ProjectConfig] =
    for {
      configStr <- Filesystem.readFile(path)
      config    <- parseProjectConfig(configStr)
    } yield config

  def loadUserConfig(path: Path): Try[Option[UserConfig]] =
    Filesystem
      .readFile(path)
      .flatMap(parseUserConfig)
      .map(Some(_))
      .recover { case _: java.nio.file.NoSuchFileException =>
        // It's ok if the user config file doesn't exist
        None
      }

  def parseProjectConfig(contents: String): Try[ProjectConfig] =
    for {
      json          <- parser.parse(contents).toTry
      projectConfig <- json.as[ProjectConfig].toTry
    } yield projectConfig

  def parseUserConfig(contents: String): Try[UserConfig] =
    for {
      json       <- parser.parse(contents).toTry
      userConfig <- json.as[UserConfig].toTry
    } yield userConfig

  def mergeConfigs(
      projectConfig: ProjectConfig,
      maybeUserConfig: Option[UserConfig]
  ): ProjectConfig =
    maybeUserConfig.fold(projectConfig) { userConfig =>
      // fetch the user's configured items so they can be added to the project config
      val mergedPlugins =
        applyPlugins(projectConfig.plugins, userConfig.plugins)
      val dotfilesCommands = userConfig.dotfiles
        .map(applyDotfiles)
        .getOrElse(Nil)

      projectConfig.copy(
        plugins = mergedPlugins,
        postCreateCommand = dotfilesCommands ++ projectConfig.postCreateCommand,
        postStartCommand = projectConfig.postStartCommand
      )
    }

  def configAsJson(projectConfig: ProjectConfig): Try[Json] =
    // Apply modules to get the final configuration
    Modules.applyModules(projectConfig).map { config =>
      val customizations = JsonObject(
        "vscode" -> Json.obj(
          "extensions" -> config.plugins.vscode.asJson
        ),
        "jetbrains" -> Json.obj(
          "plugins" -> config.plugins.intellij.asJson
        )
      )

      val commands = JsonObject.fromIterable(
        List(
          "postCreateCommand" -> combineCommands(config.postCreateCommand),
          "postStartCommand"  -> combineCommands(config.postStartCommand)
        ).collect { case (key, Some(value)) =>
          key -> Json.fromString(value)
        }
      )

      val baseConfig = JsonObject(
        "name"           -> config.name.asJson,
        "image"          -> config.image.asJson,
        "customizations" -> customizations.asJson,
        "forwardPorts"   -> config.forwardPorts.asJson
      )

      // Add optional fields if they exist
      val withFeatures = if (config.features.nonEmpty) {
        baseConfig.add("features", config.features.asJson)
      } else baseConfig

      val withMounts = if (config.mounts.nonEmpty) {
        withFeatures.add("mounts", config.mounts.asJson)
      } else withFeatures

      commands.deepMerge(withMounts).asJson
    }

  private def combineCommands(commands: List[Command]): Option[String] =
    if (commands.isEmpty) None
    else
      Some(
        commands
          .map(command => s"(cd ${command.workingDirectory} && ${command.cmd})")
          .mkString(" && ")
      )

  private def applyPlugins(
      projectPlugins: Plugins,
      userPlugins: Plugins
  ): Plugins =
    Plugins(
      intellij = (projectPlugins.intellij ++ userPlugins.intellij).distinct,
      vscode = (projectPlugins.vscode ++ userPlugins.vscode).distinct
    )

  private def applyDotfiles(dotfiles: Dotfiles): List[Command] = {
    val cloneCommand = Command(
      s"git clone ${dotfiles.repository} ${dotfiles.targetPath}",
      "."
    )
    val installCommand = Command(
      dotfiles.installCommand,
      dotfiles.targetPath
    )
    List(cloneCommand, installCommand)
  }
}
