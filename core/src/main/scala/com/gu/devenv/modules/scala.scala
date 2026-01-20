package com.gu.devenv.modules

import com.gu.devenv.Plugins
import com.gu.devenv.modules.Modules.{Module, ModuleContribution}

/** Provides IDE plugin support for Scala development.
  *
  * This module adds the Scala language plugin for both VS Code and IntelliJ IDEA.
  */
private[modules] val scalaLang = Module(
  name = "scala",
  summary = "Add IDE plugins for Scala development",
  enabledByDefault = false,
  contribution = ModuleContribution(
    plugins = Plugins(
      intellij = List("org.intellij.scala"),
      vscode = List("scala-lang.scala")
    )
  )
)
