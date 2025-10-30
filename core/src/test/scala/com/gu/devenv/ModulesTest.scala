package com.gu.devenv

import io.circe.Json
import org.scalatest.TryValues
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Failure

class ModulesTest extends AnyFreeSpec with Matchers with TryValues with OptionValues {
  "Modules.applyModules" - {
    "return the original config when no modules are specified" in {
      val config = ProjectConfig(
        name = "Test Project",
        modules = Nil,
        plugins = Plugins(vscode = List("plugin1"), intellij = List("plugin2"))
      )

      val result = Modules.applyModules(config).success.value

      result shouldBe config
    }

    "fail with unknown module name" in {
      val config = ProjectConfig(
        name = "Test Project",
        modules = List("unknown-module")
      )

      val result = Modules.applyModules(config)

      result shouldBe a[Failure[_]]
      result.failure.exception.getMessage should include("Unknown module: 'unknown-module'")
      result.failure.exception.getMessage should include("apt-updates")
      result.failure.exception.getMessage should include("mise")
    }

    "apply mise module correctly" in {
      val config = ProjectConfig(
        name = "Test Project",
        modules = List("mise"),
        plugins = Plugins(vscode = List("existing-vscode"), intellij = List("existing-intellij"))
      )

      val result = Modules.applyModules(config).success.value

      // Should add mise plugins
      result.plugins.vscode should contain allOf ("hverlin.mise-vscode", "existing-vscode")
      result.plugins.intellij should contain allOf ("com.github.l34130.mise", "existing-intellij")


      // Should add mise mount
      result.mounts should have length 1
      val mount = result.mounts.head.asInstanceOf[Mount.ExplicitMount]
      mount.source shouldBe "docker-mise-data-volume"
      mount.target shouldBe "/mnt/mise-data"
      mount.`type` shouldBe "volume"

      // Should add mise container environment variable
      result.containerEnv should have length 1
      result.containerEnv.head shouldBe Env("MISE_DATA_DIR", "/mnt/mise-data")

      // Should add mise remote environment variable for PATH
      result.remoteEnv should have length 1
      result.remoteEnv.head shouldBe Env("PATH", "${containerEnv:PATH}:/mnt/mise-data/shims")

      // Should add mise setup commands
      result.postCreateCommand should have length 1
      result.postCreateCommand.head.cmd should include("mise install")
      result.postCreateCommand.head.cmd should include("mise doctor")
    }

    "apply apt-updates module correctly" in {
      val config = ProjectConfig(
        name = "Test Project",
        modules = List("apt-updates")
      )

      val result = Modules.applyModules(config).success.value

      // Should add apt update commands
      result.postCreateCommand should have length 1
      result.postCreateCommand.head.cmd should include("apt-get update")
      result.postCreateCommand.head.cmd should include("apt-get upgrade")
      result.postCreateCommand.head.cmd should include("DEBIAN_FRONTEND=noninteractive")
    }

    "apply multiple modules in order" in {
      val config = ProjectConfig(
        name = "Test Project",
        modules = List("apt-updates", "mise")
      )

      val result = Modules.applyModules(config).success.value

      // Should have both modules' commands in order
      result.postCreateCommand should have length 2
      result.postCreateCommand(0).cmd should include("apt-get upgrade")
      result.postCreateCommand(1).cmd should include("mise install")

      // Should have mise plugins
      result.plugins.vscode should contain("hverlin.mise-vscode")
      result.plugins.intellij should contain("com.github.l34130.mise")

      // Should have mise mount
      result.mounts should have length 1
    }

    "preserve explicit config over module defaults" in {
      val explicitFeature = "ghcr.io/devcontainers/features/custom:1"
      val config = ProjectConfig(
        name = "Test Project",
        modules = List("mise"),
        features = Map(explicitFeature -> Json.obj("version" -> Json.fromString("1.0"))),
        plugins = Plugins(vscode = List("my-plugin"), intellij = List("my-intellij-plugin")),
        postCreateCommand = List(Command("my custom command", "."))
      )

      val result = Modules.applyModules(config).success.value

      // Explicit features should be preserved
      result.features should contain key explicitFeature
      result.features(explicitFeature).asObject.value("version").value.asString.value shouldBe "1.0"

      // Explicit plugins should be at the end (have precedence)
      result.plugins.vscode.last shouldBe "my-plugin"
      result.plugins.intellij.last shouldBe "my-intellij-plugin"
      // But module plugins should also be present
      result.plugins.vscode should contain("hverlin.mise-vscode")
      result.plugins.intellij should contain("com.github.l34130.mise")

      // Explicit commands should be appended after module commands
      result.postCreateCommand.last.cmd shouldBe "my custom command"
      result.postCreateCommand.head.cmd should include("mise install")
    }

    "handle modules with overlapping contributions" in {
      // Both modules contribute to postCreateCommand
      val config = ProjectConfig(
        name = "Test Project",
        modules = List("apt-updates", "mise"),
        postCreateCommand = List(Command("project setup", "/workspace"))
      )

      val result = Modules.applyModules(config).success.value

      // Should have all commands: apt-updates, mise, then explicit
      result.postCreateCommand should have length 3
      result.postCreateCommand(0).cmd should include("apt-get")
      result.postCreateCommand(1).cmd should include("mise")
      result.postCreateCommand(2).cmd shouldBe "project setup"
      result.postCreateCommand(2).workingDirectory shouldBe "/workspace"
    }

    "preserve other config fields when applying modules" in {
      val config = ProjectConfig(
        name = "Test Project",
        image = "custom:image",
        modules = List("mise"),
        forwardPorts = List(ForwardPort(3000, 3000)),
        remoteUser = "customuser",
        updateRemoteUserUID = false,
        postStartCommand = List(Command("start script", "."))
      )

      val result = Modules.applyModules(config).success.value

      // Non-module-affected fields should be preserved
      result.name shouldBe "Test Project"
      result.image shouldBe "custom:image"
      result.forwardPorts shouldBe List(ForwardPort(3000, 3000))
      result.remoteUser shouldBe "customuser"
      result.updateRemoteUserUID shouldBe false
      result.postStartCommand shouldBe List(Command("start script", "."))
    }
  }

  "Modules.applyModuleContribution" - {
    "add features from module contribution" in {
      val config = ProjectConfig(name = "Test")
      val contribution = Modules.ModuleContribution(
        features = Map("feature1" -> Json.obj(), "feature2" -> Json.fromString("value"))
      )

      val result = Modules.applyModuleContribution(config, contribution)

      result.features should contain key "feature1"
      result.features should contain key "feature2"
    }

    "add mounts from module contribution" in {
      val config       = ProjectConfig(name = "Test")
      val mount        = Mount.ExplicitMount("source", "target", "volume")
      val contribution = Modules.ModuleContribution(mounts = List(mount))

      val result = Modules.applyModuleContribution(config, contribution)

      result.mounts should contain(mount)
    }

    "add vscode plugins from module contribution" in {
      val config = ProjectConfig(name = "Test")
      val contribution = Modules.ModuleContribution(
        plugins = Plugins(intellij = Nil, vscode = List("plugin1", "plugin2"))
      )

      val result = Modules.applyModuleContribution(config, contribution)

      result.plugins.vscode should contain allOf ("plugin1", "plugin2")
    }

    "add intellij plugins from module contribution" in {
      val config = ProjectConfig(name = "Test")
      val contribution = Modules.ModuleContribution(
        plugins = Plugins(intellij = List("intellij-plugin1", "intellij-plugin2"), vscode = Nil)
      )

      val result = Modules.applyModuleContribution(config, contribution)

      result.plugins.intellij should contain allOf ("intellij-plugin1", "intellij-plugin2")
    }

    "add containerEnv from module contribution" in {
      val config = ProjectConfig(name = "Test")
      val contribution = Modules.ModuleContribution(
        containerEnv = List(Env("VAR1", "value1"), Env("VAR2", "value2"))
      )

      val result = Modules.applyModuleContribution(config, contribution)

      result.containerEnv should contain allOf (Env("VAR1", "value1"), Env("VAR2", "value2"))
    }

    "add remoteEnv from module contribution" in {
      val config = ProjectConfig(name = "Test")
      val contribution = Modules.ModuleContribution(
        remoteEnv = List(Env("PATH", "/custom/path"), Env("HOME", "/custom/home"))
      )

      val result = Modules.applyModuleContribution(config, contribution)

      result.remoteEnv should contain allOf (Env("PATH", "/custom/path"), Env("HOME", "/custom/home"))
    }

    "prepend postCreateCommands from module contribution" in {
      val config = ProjectConfig(
        name = "Test",
        postCreateCommand = List(Command("existing", "."))
      )
      val contribution = Modules.ModuleContribution(
        postCreateCommands = List(Command("module command", "."))
      )

      val result = Modules.applyModuleContribution(config, contribution)

      result.postCreateCommand should have length 2
      result.postCreateCommand.head.cmd shouldBe "module command"
      result.postCreateCommand.last.cmd shouldBe "existing"
    }

    "preserve explicit config when applying contribution" in {
      val config = ProjectConfig(
        name = "Test",
        features = Map("explicit-feature" -> Json.obj()),
        plugins = Plugins(intellij = Nil, vscode = List("explicit-plugin"))
      )
      val contribution = Modules.ModuleContribution(
        features = Map("module-feature" -> Json.obj()),
        plugins = Plugins(intellij = Nil, vscode = List("module-plugin"))
      )

      val result = Modules.applyModuleContribution(config, contribution)

      // Both should be present, explicit takes precedence in maps
      result.features should contain key "explicit-feature"
      result.features should contain key "module-feature"
      result.plugins.vscode should contain allOf ("module-plugin", "explicit-plugin")
    }

    "handle empty module contribution" in {
      val config = ProjectConfig(
        name = "Test",
        features = Map("feature" -> Json.obj()),
        plugins = Plugins(intellij = Nil, vscode = List("plugin"))
      )
      val contribution = Modules.ModuleContribution()

      val result = Modules.applyModuleContribution(config, contribution)

      result should equal(config)
    }
  }
}
