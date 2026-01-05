package com.gu.devenv.modules

import io.circe.Json

import scala.util.{Failure, Success, Try}
import cats.implicits.*
import com.gu.devenv.{Command, Env, Mount, Plugins, ProjectConfig}

object Modules {
  case class ModuleContribution(
      features: Map[String, Json] = Map.empty,
      mounts: List[Mount] = Nil,
      plugins: Plugins = Plugins.empty,
      containerEnv: List[Env] = Nil,
      remoteEnv: List[Env] = Nil,
      postCreateCommands: List[Command] = Nil,
      capAdd: List[String] = Nil,
      securityOpt: List[String] = Nil
  )

  /** Apply modules to a project config, merging their contributions. Explicit config takes
    * precedence over module defaults. Returns a Failure if any unknown modules are specified.
    */
  def applyModules(config: ProjectConfig): Try[ProjectConfig] =
    config.modules
      .traverse(getModuleContribution) // lookup requested modules to check we support them
      .map { moduleContributions =>
        moduleContributions.foldRight(config)((contribution, cfg) =>
          applyModuleContribution(cfg, contribution)
        )
      }

  /** Apply a single module contribution to a project config. Module contributions are prepended to
    * explicit config, so explicit config takes precedence.
    */
  private[devenv] def applyModuleContribution(
      config: ProjectConfig,
      contribution: ModuleContribution
  ): ProjectConfig =
    config.copy(
      features = contribution.features ++ config.features,
      mounts = contribution.mounts ++ config.mounts,
      plugins = Plugins(
        intellij = contribution.plugins.intellij ++ config.plugins.intellij,
        vscode = contribution.plugins.vscode ++ config.plugins.vscode
      ),
      containerEnv = contribution.containerEnv ++ config.containerEnv,
      remoteEnv = contribution.remoteEnv ++ config.remoteEnv,
      postCreateCommand = contribution.postCreateCommands ++ config.postCreateCommand,
      capAdd = contribution.capAdd ++ config.capAdd,
      securityOpt = contribution.securityOpt ++ config.securityOpt
    )

  private def getModuleContribution(
      moduleName: String
  ): Try[ModuleContribution] =
    moduleName match {
      case "apt-updates"      => Success(aptUpdates)
      case "mise"             => Success(mise)
      case "docker-in-docker" => Success(dockerInDocker)
      case unknown =>
        Failure(
          new IllegalArgumentException(
            s"Unknown module: '$unknown'. Available modules: apt-updates, mise, docker-in-docker"
          )
        )
    }
}
