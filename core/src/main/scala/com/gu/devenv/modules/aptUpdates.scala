package com.gu.devenv.modules

import com.gu.devenv.Command
import com.gu.devenv.modules.Modules.{Module, ModuleContribution}

/** Updates / upgrades apt packages during the post-create phase.
  *
  * This ensures that the development container has the latest security patches and updates applied
  * when it is created.
  *
  * There's little reason not to enable this module for any devcontainer that uses apt for package
  * management.
  */
private[modules] val aptUpdates = Module(
  name = "apt-updates",
  enabledByDefault = true,
  contribution = ModuleContribution(
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
)
