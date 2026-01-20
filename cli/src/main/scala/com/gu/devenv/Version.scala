package com.gu.devenv

/** Version information for the devenv CLI tool.
  *
  * The version is set at build time via the DEVENV_VERSION environment variable and gets baked into
  * the native binary during GraalVM compilation.
  *
  * This environment variable is allowlisted in the build.sbt settings for the cli project.
  */
object Version {

  /** The current version of devenv, set at build time and passed to the native binary using the
    * `-E` graalVMNativeImageOptions entry in build.sbt.
    */
  val current: String = sys.env.getOrElse("DEVENV_VERSION", "dev")
}
