package com.gu.devenv

import io.circe.Json
import org.scalatest.{OptionValues, TryValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Success

class ConfigTest
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with OptionValues
    with HavingMatchers {
  "Loads our example project config file" in {
    val exampleConfig =
      scala.io.Source.fromResource("projectConfig.yaml").mkString
    val Success(projectConfig) =
      Config.parseProjectConfig(exampleConfig).success

    projectConfig should have(
      "name" as "Scala SBT Development Container",
      "image" as "mcr.microsoft.com/devcontainers/base:ubuntu",
      "forwardPorts" as List(
        ForwardPort(8080, 8080),
        ForwardPort(9000, 9000)
      ),
      "remoteEnv" as List(
        Env("SBT_OPTS", "-Xmx2G -XX:+UseG1GC"),
        Env("JAVA_HOME", "/usr/lib/jvm/java-17-openjdk-amd64")
      ),
      "containerEnv" as List(
        Env("EXAMPLE", "foo"),
        Env("EXAMPLE+2", "bar")
      ),
      "mounts" as List(
        Mount.ExplicitMount(
          "${localWorkspaceFolder}/.ivy2",
          "/home/vscode/.ivy2",
          "volume"
        ),
        Mount.ExplicitMount(
          "${localWorkspaceFolder}/.sbt",
          "/home/vscode/.sbt",
          "volume"
        )
      ),
      "postCreateCommand" as List(
        Command("sbt update", "/workspaces/project/subdir"),
        Command("sbt compile", "subdir")
      ),
      "postStartCommand" as List(
        Command("echo 'Container started successfully'", ".")
      ),
      "features" as Map(
        "ghcr.io/devcontainers/features/docker-in-docker:1" -> Json.obj()
      ),
      "remoteUser" as "vscode",
      "updateRemoteUserUID" as true
    )
  }

  "loads an example user config file" in {
    val exampleConfig =
      scala.io.Source.fromResource("userConfig.yaml").mkString
    val Success(userConfig) =
      Config.parseUserConfig(exampleConfig).success

    userConfig should have(
      "plugins" as Plugins(
        List("com.github.copilot", "com.github.gtache.lsp"),
        List("GitHub.copilot")
      ),
      "dotfiles" as Some(
        Dotfiles(
          "https://github.com/example/dotfiles.git",
          "~",
          "install.sh"
        )
      )
    )
  }

  "merges the example config files" in {
    val projectConfigYaml =
      scala.io.Source.fromResource("projectConfig.yaml").mkString
    val userConfigYaml =
      scala.io.Source.fromResource("userConfig.yaml").mkString

    val Success(projectConfig) =
      Config.parseProjectConfig(projectConfigYaml).success
    val Success(userConfig) =
      Config.parseUserConfig(userConfigYaml).success

    val merged = Config.mergeConfigs(projectConfig, Some(userConfig))

    // Plugins should be merged and deduplicated (project plugins + user plugins)
    merged.plugins should have(
      "intellij" as List(
        "org.intellij.scala",
        "com.github.gtache.lsp",
        "com.github.copilot"
      ),
      "vscode" as List("scalameta.metals", "scala-lang.scala", "GitHub.copilot")
    )

    // Dotfiles commands should be prepended to postCreateCommand
    merged.postCreateCommand should have length 4
    merged.postCreateCommand.take(2) shouldBe List(
      Command("git clone https://github.com/example/dotfiles.git ~", "."),
      Command("install.sh", "~")
    )
    merged.postCreateCommand.drop(2) shouldBe projectConfig.postCreateCommand

    // Other fields should remain unchanged from project config
    merged should have(
      "name" as projectConfig.name,
      "image" as projectConfig.image,
      "forwardPorts" as projectConfig.forwardPorts,
      "remoteEnv" as projectConfig.remoteEnv,
      "containerEnv" as projectConfig.containerEnv,
      "mounts" as projectConfig.mounts,
      "postStartCommand" as projectConfig.postStartCommand,
      "features" as projectConfig.features,
      "remoteUser" as projectConfig.remoteUser,
      "updateRemoteUserUID" as projectConfig.updateRemoteUserUID
    )
  }

  "generates devcontainer.json for project config only" in {
    val projectConfigYaml =
      scala.io.Source.fromResource("projectConfig.yaml").mkString
    val Success(projectConfig) =
      Config.parseProjectConfig(projectConfigYaml).success

    val Success(json) = Config.configAsJson(projectConfig).success

    // Assert against JSON structure
    (json \\ "name").head.asString should contain(
      "Scala SBT Development Container"
    )
    (json \\ "image").head.asString should contain(
      "mcr.microsoft.com/devcontainers/base:ubuntu"
    )

    val forwardPorts = (json \\ "forwardPorts").head.asArray.value
    forwardPorts should have length 2
    forwardPorts.head.asNumber.flatMap(_.toInt) shouldBe Some(8080)
    forwardPorts(1).asNumber.flatMap(_.toInt) shouldBe Some(9000)

    val extensions =
      (json \\ "extensions").head.asArray.value.flatMap(_.asString)
    extensions should contain allOf ("scalameta.metals", "scala-lang.scala", "hverlin.mise-vscode")

    val plugins = (json \\ "plugins").head.asArray.value.flatMap(_.asString)
    plugins should contain allOf (
      "org.intellij.scala",
      "com.github.gtache.lsp",
      "com.github.l34130.mise"
    )

    // Should have mise feature from the module
    val features = (json \\ "features").head.asObject.value
    features.keys should contain("ghcr.io/devcontainers-extra/features/mise:1")
    features.keys should contain("ghcr.io/devcontainers/features/docker-in-docker:1")

    // Should have mise mount from the module
    val mounts = (json \\ "mounts").head.asArray.value
    mounts.length should be >= 3
    val mountSources = mounts.flatMap(_.asObject).flatMap(_.apply("source")).flatMap(_.asString)
    mountSources should contain("docker-mise-data-volume")

    val postCreateCommand = (json \\ "postCreateCommand").head.asString.value
    postCreateCommand should include("sbt update")
    postCreateCommand should include("sbt compile")
    postCreateCommand should include("mise install")
    postCreateCommand should include("mise activate")

    (json \\ "postStartCommand").head.asString.value shouldBe
      "(cd . && echo 'Container started successfully')"
  }

  "generates devcontainer.json with merged user config" in {
    val projectConfigYaml =
      scala.io.Source.fromResource("projectConfig.yaml").mkString
    val userConfigYaml =
      scala.io.Source.fromResource("userConfig.yaml").mkString

    val Success(projectConfig) =
      Config.parseProjectConfig(projectConfigYaml).success
    val Success(userConfig) =
      Config.parseUserConfig(userConfigYaml).success

    val merged        = Config.mergeConfigs(projectConfig, Some(userConfig))
    val Success(json) = Config.configAsJson(merged).success

    // Assert against JSON structure
    (json \\ "name").head.asString should contain(
      "Scala SBT Development Container"
    )
    (json \\ "image").head.asString should contain(
      "mcr.microsoft.com/devcontainers/base:ubuntu"
    )

    val forwardPorts = (json \\ "forwardPorts").head.asArray.value
    forwardPorts should have length 2
    forwardPorts.head.asNumber.flatMap(_.toInt) shouldBe Some(8080)

    val extensions =
      (json \\ "extensions").head.asArray.value.flatMap(_.asString)
    extensions should contain allOf ("scalameta.metals", "scala-lang.scala", "GitHub.copilot")

    val plugins = (json \\ "plugins").head.asArray.value.flatMap(_.asString)
    plugins should contain allOf (
      "org.intellij.scala",
      "com.github.gtache.lsp",
      "com.github.copilot"
    )

    (json \\ "postCreateCommand").head.asString.value should include(
      "git clone https://github.com/example/dotfiles.git"
    )
  }

  /** This is left here as a debugging tool.
    *
    * To see the output, change `ignore` to `in` and run this test with:
    *
    * core/testOnly *ConfigTest -- -z "print the merged devcontainer content for manual inspection"
    */
  "print the merged devcontainer content for manual inspection" in {
    val projectConfigYaml =
      scala.io.Source.fromResource("projectConfig.yaml").mkString
    val userConfigYaml =
      scala.io.Source.fromResource("userConfig.yaml").mkString

    val Success(projectConfig) =
      Config.parseProjectConfig(projectConfigYaml).success
    val Success(userConfig) =
      Config.parseUserConfig(userConfigYaml).success

    val merged        = Config.mergeConfigs(projectConfig, Some(userConfig))
    val Success(json) = Config.configAsJson(merged).success

    println(json.spaces2)
  }
}
