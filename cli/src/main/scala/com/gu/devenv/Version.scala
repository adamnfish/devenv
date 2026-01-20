package com.gu.devenv

/** Version information for the devenv CLI tool.
  *
  * These settings are baked into the native binary at build time via environment variables.
  */
object Version {

  /** The release version of this devenv binary.
    *
    * This is set at build time via the DEVENV_ARCHITECTURE environment variable and gets baked into
    * the native binary during GraalVM compilation.
    *
    * This environment variable is allowlisted in the build.sbt settings for the cli project.
    */
  val release: String = sys.env.getOrElse("DEVENV_RELEASE", "dev")

  /** The target architecture for which the devenv binary was built.
    *
    * This is set at build time via the DEVENV_ARCHITECTURE environment variable and gets baked into
    * the native binary during GraalVM compilation.
    *
    * This environment variable is allowlisted in the build.sbt settings for the cli project.
    */
  val architecture: String = sys.env.getOrElse("DEVENV_ARCHITECTURE", "jvm")
}
