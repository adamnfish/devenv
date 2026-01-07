package com.gu.devenv.integration

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

object IntegrationTestHelpers {
  // Helper functions for temporary directory management

  def withTempDir[A](test: Path => A): A = {
    val tempDir = Files.createTempDirectory("devenv-init-test")
    try
      test(tempDir)
    finally
      deleteRecursively(tempDir)
  }

  def withTempDirs[A](test: (Path, Path) => A): A = {
    val projectTempDir    = Files.createTempDirectory("devenv-generate-test")
    val userConfigTempDir = Files.createTempDirectory("devenv-user-config-test")
    try test(projectTempDir, userConfigTempDir)
    finally {
      deleteRecursively(projectTempDir)
      deleteRecursively(userConfigTempDir)
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).iterator().asScala.foreach(deleteRecursively)
    }
    Files.delete(path)
  }

  // Test fixture data - Project configurations

  val basicProjectConfig: String =
    """|name: "My Test Project"
       |image: "mcr.microsoft.com/devcontainers/base:ubuntu"
       |modules: []
       |""".stripMargin

  val projectConfigWithPlugins: String =
    """|name: "Project With Plugins"
       |modules: []
       |plugins:
       |  vscode:
       |    - project-plugin-1
       |    - project-plugin-2
       |  intellij:
       |    - project-intellij-plugin
       |""".stripMargin

  val projectConfigWithAptUpdates: String =
    """|name: "Project With Apt Updates"
       |modules:
       |  - apt-updates
       |""".stripMargin

  val projectConfigWithMise: String =
    """|name: "Project With Mise"
       |modules:
       |  - mise
       |""".stripMargin

  val projectConfigWithMultipleModules: String =
    """|name: "Project With Multiple Modules"
       |modules:
       |  - apt-updates
       |  - mise
       |""".stripMargin

  val projectConfigWithModules: String =
    """|name: "Project With Modules"
       |modules:
       |  - apt-updates
       |""".stripMargin

  val projectConfigWithUnknownModule: String =
    """|name: "Project With Unknown Module"
       |modules:
       |  - unknown-module
       |""".stripMargin

  val complexProjectConfig: String =
    """|name: "Complex Project"
       |image: "mcr.microsoft.com/devcontainers/base:ubuntu"
       |modules:
       |  - mise
       |plugins:
       |  vscode:
       |    - project-plugin-1
       |  intellij:
       |    - project-intellij-plugin
       |forwardPorts:
       |  - 3000
       |postCreateCommand:
       |  - cmd: "npm install"
       |    workingDirectory: "/workspaces/project"
       |""".stripMargin

  // Test fixture data - User configurations

  val userConfigWithPlugins: String =
    """|plugins:
       |  vscode:
       |    - user-plugin-1
       |    - user-plugin-2
       |  intellij:
       |    - user-intellij-plugin
       |""".stripMargin

  val userConfigWithDotfiles: String =
    """|plugins:
       |  vscode: []
       |  intellij: []
       |dotfiles:
       |  repository: "https://github.com/myuser/dotfiles"
       |  targetPath: "~/dotfiles"
       |  installCommand: "./install.sh"
       |""".stripMargin
}
