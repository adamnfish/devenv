package com.gu.devenv.modules

import com.gu.devenv.Plugins
import com.gu.devenv.modules.Modules.ModuleContribution

/** Provides IDE plugin support for Scala development.
  *
  * This module adds the Scala language plugin for both VS Code and IntelliJ IDEA.
  */
private[modules] val scalaLanguage = ModuleContribution(
  plugins = Plugins(
    intellij = List("org.intellij.scala"),
    vscode = List("scala-lang.scala")
  )
)
