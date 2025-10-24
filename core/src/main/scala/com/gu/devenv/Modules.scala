package com.gu.devenv

import io.circe.Json
import scala.util.{Try, Success, Failure}
import cats.implicits._

/** Defines built-in modules that can be enabled/disabled in project configuration.
  * Each module can contribute features, mounts, plugins, and commands to the final devcontainer.
  */
object Modules {

  case class ModuleContribution(
      features: Map[String, Json] = Map.empty,
      mounts: List[Mount] = Nil,
      plugins: Plugins = Plugins.empty,
      postCreateCommands: List[Command] = Nil
  )

  /** Apply modules to a project config, merging their contributions.
    * Explicit config takes precedence over module defaults.
    * Returns a Failure if any unknown modules are specified.
    */
  def applyModules(config: ProjectConfig): Try[ProjectConfig] = {
    config.modules.traverse(getModuleContribution).map { contributions =>
      val mergedContribution = contributions.foldLeft(ModuleContribution()) {
        case (acc, contrib) =>
          ModuleContribution(
            features = acc.features ++ contrib.features,
            mounts = acc.mounts ++ contrib.mounts,
            plugins = Plugins(
              intellij = acc.plugins.intellij ++ contrib.plugins.intellij,
              vscode = acc.plugins.vscode ++ contrib.plugins.vscode
            ),
            postCreateCommands = acc.postCreateCommands ++ contrib.postCreateCommands
          )
      }

      // Merge with explicit config - explicit config takes precedence
      config.copy(
        features = mergedContribution.features ++ config.features,
        mounts = mergedContribution.mounts ++ config.mounts,
        plugins = Plugins(
          intellij = mergedContribution.plugins.intellij ++ config.plugins.intellij,
          vscode = mergedContribution.plugins.vscode ++ config.plugins.vscode
        ),
        postCreateCommand = mergedContribution.postCreateCommands ++ config.postCreateCommand,
        postStartCommand = config.postStartCommand
      )
    }
  }

  // ...existing code...

  private def getModuleContribution(moduleName: String): Try[ModuleContribution] = {
    moduleName match {
      case "security-updates" => Success(securityUpdates)
      case "mise"             => Success(mise)
      case unknown => Failure(new IllegalArgumentException(
        s"Unknown module: '$unknown'. Available modules: security-updates, mise"
      ))
    }
  }

  // ...existing code...

  private val securityUpdates = ModuleContribution(
    postCreateCommands = List(
      Command(
        cmd = "export DEBIAN_FRONTEND=noninteractive && " +
          "sudo bash -lc 'apt-get update || (sleep 2 && apt-get update)' && " +
          "sudo bash -lc 'apt-get upgrade -y' && " +
          "sudo apt-get clean && " +
          "sudo rm -rf /var/lib/apt/lists/*",
        workingDirectory = "."
      )
    )
  )

  private val mise = ModuleContribution(
    features = Map(
      "ghcr.io/devcontainers-extra/features/mise:1" -> Json.obj()
    ),
    mounts = List(
      Mount.ExplicitMount(
        source = "docker-mise-data-volume",
        target = "/mnt/mise-data",
        `type` = "volume"
      )
    ),
    plugins = Plugins(
      intellij = List("com.github.l34130.mise"),
      vscode = List("hverlin.mise-vscode")
    ),
    postCreateCommands = List(
      Command(
        cmd = """echo -e "\033[1;34m[setup] Setting up mise...\033[0m" && """ +
          "eval \"$(mise activate bash)\" && " +
          "mise --version && " +
          "mise trust || true && " +
          """for i in 1 2; do mise install && break; echo "mise install failed, retrying in 2 seconds... (attempt $i)"; sleep 2; done""",
        workingDirectory = "."
      )
    )
  )
}

