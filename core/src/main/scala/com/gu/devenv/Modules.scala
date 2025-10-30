package com.gu.devenv

import io.circe.Json
import scala.util.{Failure, Success, Try}
import cats.implicits._

/** Defines built-in modules that can be enabled/disabled in project configuration. Each module can
  * contribute features, mounts, plugins, and commands to the final devcontainer.
  */
object Modules {
  case class ModuleContribution(
      features: Map[String, Json] = Map.empty,
      mounts: List[Mount] = Nil,
      plugins: Plugins = Plugins.empty,
      containerEnv: List[Env] = Nil,
      remoteEnv: List[Env] = Nil,
      postCreateCommands: List[Command] = Nil
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
      postCreateCommand = contribution.postCreateCommands ++ config.postCreateCommand
    )

  private def getModuleContribution(
      moduleName: String
  ): Try[ModuleContribution] =
    moduleName match {
      case "apt-updates" => Success(aptUpdates)
      case "mise"        => Success(mise)
      case unknown =>
        Failure(
          new IllegalArgumentException(
            s"Unknown module: '$unknown'. Available modules: apt-updates, mise"
          )
        )
    }

  private val aptUpdates = ModuleContribution(
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
    plugins = Plugins(
      intellij = List("com.github.l34130.mise"),
      vscode = List("hverlin.mise-vscode")
    ),
    containerEnv = List(
      Env("MISE_DATA_DIR", "/mnt/mise-data")
    ),
    remoteEnv = List(
      Env("PATH", "${containerEnv:PATH}:/mnt/mise-data/shims")
    ),
    mounts = List(
      Mount.ExplicitMount(
        source = "docker-mise-data-volume",
        target = "/mnt/mise-data",
        `type` = "volume"
      )
    ),
    postCreateCommands = List(
      Command(
        cmd = """bash -c 'echo -e "\033[1;34m[setup] Setting up mise...\033[0m" && """ +
          // ensure correct ownership of the shared mise data volume
          "sudo chown -R vscode:vscode /mnt/mise-data && " +
          // install the mise binary
          """curl https://mise.run | sh && """ +
          """mise --version && """ +
          // This enables the repository's config files
          // See: https://mise.jdx.dev/cli/trust.html
          """mise trust || true && """ +
          // do a mise install and print a warning if it fails
          """mise install || echo -e "\033[1;33m[setup] mise install failed. You may need to run `mise install` manually inside the container.\033[0m" && """ +
          // Make sure mise is active after installation
          "mise doctor && " +
          """echo -e "\033[1;32m[setup] mise setup complete.\033[0m"'""",
        workingDirectory = "."
      )
    )
  )
}
