package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import io.circe.Json

/** Provides AWS CLI support.
  *
  * This module adds the AWS CLI.
  */
private[modules] val awscli = Module(
  name = "awscli",
  summary = "Add AWS CLI",
  enabledByDefault = false,
  contribution = ModuleContribution(
    features = Map(
      "ghcr.io/devcontainers/features/aws-cli:1" -> Json.obj(
        "version"                  -> Json.fromString("latest"),
        "moby"                     -> Json.fromBoolean(true),
        "dockerDashComposeVersion" -> Json.fromString("v2")
      )
    ),
    capAdd = List("SYS_ADMIN"),
    securityOpt = List("seccomp=unconfined")
  )
)
