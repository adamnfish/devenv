package com.gu.devenv

/** Version information for the devenv CLI tool.
  *
  * These settings are baked into the native binary at build time via environment variables.
  */
object Version {

  /** The release version of this devenv binary.
    *
    * This is set at build time via the DEVENV_RELEASE environment variable and gets baked into the
    * native binary during GraalVM compilation.
    *
    * This environment variable is allowlisted in the build.sbt settings for the cli project.
    *
    * Defaults to "dev" for local development builds (running via sbt or JVM packaging).
    */
  val release: String = sys.env.getOrElse("DEVENV_RELEASE", "dev")

  /** The target architecture for which the devenv binary was built.
    *
    * This is set at build time via the DEVENV_ARCHITECTURE environment variable and gets baked into
    * the native binary during GraalVM compilation.
    *
    * This environment variable is allowlisted in the build.sbt settings for the cli project.
    *
    * Defaults to "jvm" for local development builds (running via sbt or JVM packaging).
    */
  val architecture: String = sys.env.getOrElse("DEVENV_ARCHITECTURE", "jvm")

  /** The branch from which this devenv binary was built.
    *
    * This is set at build time via the DEVENV_BRANCH environment variable and gets baked into the
    * native binary during GraalVM compilation.
    *
    * This environment variable is allowlisted in the build.sbt settings for the cli project.
    *
    * Returns None for local development builds (running via sbt or JVM packaging).
    */
  val branch: Option[String] = sys.env.get("DEVENV_BRANCH")
}
