package com.gu.devenv.integration

import com.gu.devenv.Devenv
import com.gu.devenv.Devenv.GenerateResult
import com.gu.devenv.Filesystem.FileSystemStatus
import com.gu.devenv.integration.IntegrationTestHelpers._
import io.circe.parser._
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class GenerateIntegrationTest extends AnyFreeSpec with Matchers with TryValues {

  "generate" - {
    "generating for an uninitialized directory" - {
      "should return NotInitialized result" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        val result = Devenv.generate(devcontainerDir, userConfigDir).success.value

        result shouldBe GenerateResult.NotInitialized
      }

      "should not create any files" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.generate(devcontainerDir, userConfigDir).success.value

        Files.exists(devcontainerDir) shouldBe false
      }
    }

    "generating with placeholder project name" - {
      "should return ConfigNotCustomized result" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        // Initialize first
        Devenv.init(devcontainerDir).success.value

        val result = Devenv.generate(devcontainerDir, userConfigDir).success.value

        result shouldBe GenerateResult.ConfigNotCustomized
      }

      "should not create devcontainer.json files" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Devenv.generate(devcontainerDir, userConfigDir).success.value

        Files.exists(devcontainerDir.resolve("user/devcontainer.json")) shouldBe false
        Files.exists(devcontainerDir.resolve("shared/devcontainer.json")) shouldBe false
      }
    }

    "generating a basic project config" - {
      "should create both devcontainer.json files" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        // Initialize and customize
        Devenv.init(devcontainerDir).success.value
        val devenvFile = devcontainerDir.resolve("devenv.yaml")
        Files.writeString(devenvFile, basicProjectConfig)

        val result = Devenv.generate(devcontainerDir, userConfigDir).success.value

        result match {
          case GenerateResult.Success(userStatus, sharedStatus) =>
            userStatus shouldBe FileSystemStatus.Created
            sharedStatus shouldBe FileSystemStatus.Created
          case _ => fail("Expected Success result")
        }

        Files.exists(devcontainerDir.resolve("user/devcontainer.json")) shouldBe true
        Files.exists(devcontainerDir.resolve("shared/devcontainer.json")) shouldBe true
      }

      "should generate valid JSON devcontainer files" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Files.writeString(devcontainerDir.resolve("devenv.yaml"), basicProjectConfig)

        Devenv.generate(devcontainerDir, userConfigDir).success.value

        val userJson   = Files.readString(devcontainerDir.resolve("user/devcontainer.json"))
        val sharedJson = Files.readString(devcontainerDir.resolve("shared/devcontainer.json"))

        parse(userJson).isRight shouldBe true
        parse(sharedJson).isRight shouldBe true
      }

      "should include project name in generated files" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Files.writeString(devcontainerDir.resolve("devenv.yaml"), basicProjectConfig)

        Devenv.generate(devcontainerDir, userConfigDir).success.value

        val userJson   = Files.readString(devcontainerDir.resolve("user/devcontainer.json"))
        val sharedJson = Files.readString(devcontainerDir.resolve("shared/devcontainer.json"))

        userJson should include("\"My Test Project\"")
        sharedJson should include("\"My Test Project\"")
      }

      "should update existing devcontainer files on subsequent generation" in withTempDirs {
        (tempDir, userConfigDir) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir).success.value
          Files.writeString(devcontainerDir.resolve("devenv.yaml"), basicProjectConfig)

          // First generation
          Devenv.generate(devcontainerDir, userConfigDir).success.value

          val firstUserJson = Files.readString(devcontainerDir.resolve("user/devcontainer.json"))

          // Update the project config
          val updatedConfig = basicProjectConfig.replace("My Test Project", "Updated Project Name")
          Files.writeString(devcontainerDir.resolve("devenv.yaml"), updatedConfig)

          // Second generation
          val result = Devenv.generate(devcontainerDir, userConfigDir).success.value

          result match {
            case GenerateResult.Success(userStatus, sharedStatus) =>
              userStatus shouldBe FileSystemStatus.AlreadyExists
              sharedStatus shouldBe FileSystemStatus.AlreadyExists
            case _ => fail("Expected Success result")
          }

          // Verify the files were actually updated with new content
          val secondUserJson = Files.readString(devcontainerDir.resolve("user/devcontainer.json"))
          secondUserJson should include("Updated Project Name")
          secondUserJson should not include "My Test Project"
          firstUserJson should not equal secondUserJson
      }
    }

    "generating a project config merged with a user config" - {
      "should merge user plugins into project plugins" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Files.writeString(devcontainerDir.resolve("devenv.yaml"), projectConfigWithPlugins)
        Files.writeString(userConfigDir.resolve("devenv.yaml"), userConfigWithPlugins)

        Devenv.generate(devcontainerDir, userConfigDir).success.value

        val userJson = Files.readString(devcontainerDir.resolve("user/devcontainer.json"))

        // User devcontainer should have both project and user plugins
        userJson should include("project-plugin-1")
        userJson should include("project-plugin-2")
        userJson should include("user-plugin-1")
        userJson should include("user-plugin-2")
      }

      "should not include user plugins in shared devcontainer" in withTempDirs {
        (tempDir, userConfigDir) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir).success.value
          Files.writeString(devcontainerDir.resolve("devenv.yaml"), projectConfigWithPlugins)
          Files.writeString(userConfigDir.resolve("devenv.yaml"), userConfigWithPlugins)

          Devenv.generate(devcontainerDir, userConfigDir).success.value

          val sharedJson = Files.readString(devcontainerDir.resolve("shared/devcontainer.json"))

          // Shared devcontainer should only have project plugins
          sharedJson should include("project-plugin-1")
          sharedJson should include("project-plugin-2")
          sharedJson should not include "user-plugin-1"
          sharedJson should not include "user-plugin-2"
      }

      "should merge user dotfiles into user devcontainer" in withTempDirs {
        (tempDir, userConfigDir) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir).success.value
          Files.writeString(devcontainerDir.resolve("devenv.yaml"), basicProjectConfig)
          Files.writeString(userConfigDir.resolve("devenv.yaml"), userConfigWithDotfiles)

          Devenv.generate(devcontainerDir, userConfigDir).success.value

          val userJson = Files.readString(devcontainerDir.resolve("user/devcontainer.json"))

          userJson should include("dotfiles")
          userJson should include("github.com/myuser/dotfiles")
      }

      "should not include dotfiles in shared devcontainer" in withTempDirs {
        (tempDir, userConfigDir) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir).success.value
          Files.writeString(devcontainerDir.resolve("devenv.yaml"), basicProjectConfig)
          Files.writeString(userConfigDir.resolve("devenv.yaml"), userConfigWithDotfiles)

          Devenv.generate(devcontainerDir, userConfigDir).success.value

          val sharedJson = Files.readString(devcontainerDir.resolve("shared/devcontainer.json"))

          sharedJson should not include "dotfiles"
      }
    }

    "generating a project that includes modules" - {
      "should apply apt-updates module features" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Files.writeString(devcontainerDir.resolve("devenv.yaml"), projectConfigWithAptUpdates)

        Devenv.generate(devcontainerDir, userConfigDir).success.value

        val sharedJson = Files.readString(devcontainerDir.resolve("shared/devcontainer.json"))

        sharedJson should include("apt-get update")
        sharedJson should include("apt-get upgrade")
      }

      "should apply mise module features" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Files.writeString(devcontainerDir.resolve("devenv.yaml"), projectConfigWithMise)

        Devenv.generate(devcontainerDir, userConfigDir).success.value

        val sharedJson = Files.readString(devcontainerDir.resolve("shared/devcontainer.json"))

        sharedJson should include("ghcr.io/devcontainers-extra/features/mise:1")
        sharedJson should include("hverlin.mise-vscode")
        sharedJson should include("com.github.l34130.mise")
      }

      "should apply multiple modules" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Files.writeString(devcontainerDir.resolve("devenv.yaml"), projectConfigWithMultipleModules)

        Devenv.generate(devcontainerDir, userConfigDir).success.value

        val sharedJson = Files.readString(devcontainerDir.resolve("shared/devcontainer.json"))

        sharedJson should include("apt-get update")
        sharedJson should include("ghcr.io/devcontainers-extra/features/mise:1")
      }

      "should fail with unknown module" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Files.writeString(devcontainerDir.resolve("devenv.yaml"), projectConfigWithUnknownModule)

        val result = Devenv.generate(devcontainerDir, userConfigDir)

        result.isFailure shouldBe true
      }
    }

    "generating a complex project with modules and user config" - {
      "should merge all configurations correctly" in withTempDirs { (tempDir, userConfigDir) =>
        val devcontainerDir = tempDir.resolve(".devcontainer")

        Devenv.init(devcontainerDir).success.value
        Files.writeString(devcontainerDir.resolve("devenv.yaml"), complexProjectConfig)
        Files.writeString(userConfigDir.resolve("devenv.yaml"), userConfigWithPlugins)

        Devenv.generate(devcontainerDir, userConfigDir).success.value

        val userJson   = Files.readString(devcontainerDir.resolve("user/devcontainer.json"))
        val sharedJson = Files.readString(devcontainerDir.resolve("shared/devcontainer.json"))

        // Both should have mise module features
        userJson should include("ghcr.io/devcontainers-extra/features/mise:1")
        sharedJson should include("ghcr.io/devcontainers-extra/features/mise:1")

        // Both should have project plugins
        userJson should include("project-plugin-1")
        sharedJson should include("project-plugin-1")

        // Only user should have user plugins
        userJson should include("user-plugin-1")
        sharedJson should not include "user-plugin-1"

        // Both should have project config
        userJson should include("Complex Project")
        sharedJson should include("Complex Project")

        // User should have merged mise plugins from module and user plugins
        userJson should include("hverlin.mise-vscode")
        userJson should include("com.github.l34130.mise")
      }
    }
  }
}
