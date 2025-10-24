package com.gu.devenv

import io.circe.{Encoder, Json}

case class ProjectConfig(
    name: String,
    image: String = "mcr.microsoft.com/devcontainers/base:ubuntu",
    modules: List[String] = Nil,
    forwardPorts: List[ForwardPort] = Nil,
    remoteEnv: List[Env] = Nil,
    containerEnv: List[Env] = Nil,
    plugins: Plugins = Plugins.empty,
    mounts: List[Mount] = Nil,
    postCreateCommand: List[Command] = Nil,
    postStartCommand: List[Command] = Nil,
    features: Map[String, Json] = Map.empty,
    remoteUser: String = "vscode",
    updateRemoteUserUID: Boolean = true
)

case class UserConfig(
    plugins: Plugins,
    dotfiles: Option[Dotfiles]
)

case class ForwardPort(containerPort: Int, hostPort: Int)
object ForwardPort {
  given Encoder[ForwardPort] = Encoder.instance {
    case ForwardPort(containerPort, hostPort) if containerPort == hostPort =>
      Json.fromInt(containerPort)
    case ForwardPort(containerPort, hostPort) =>
      Json.fromString(s"$hostPort:$containerPort")
  }
}
case class Env(name: String, value: String)
case class Plugins(
    intellij: List[String],
    vscode: List[String]
)
object Plugins {
  def empty = Plugins(Nil, Nil)
}
case class Command(
    cmd: String,
    workingDirectory: String
)
enum Mount {
  case ExplicitMount(
      source: String,
      target: String,
      `type`: String
  )
  case ShortMount(
      mount: String
  )
}

object Mount {
  import io.circe.{Decoder, Encoder}

  given Decoder[Mount] = Decoder.instance { c =>
    c.as[String].map(ShortMount(_)) match {
      case Right(shortMount) => Right(shortMount)
      case Left(_) =>
        for {
          source    <- c.downField("source").as[String]
          target    <- c.downField("target").as[String]
          mountType <- c.downField("type").as[String]
        } yield ExplicitMount(source, target, mountType)
    }
  }

  implicit val encodeMountEncoder: Encoder[Mount] = Encoder.instance {
    case ShortMount(mount) => Json.fromString(mount)
    case ExplicitMount(source, target, mountType) =>
      Json.obj(
        "source" -> Json.fromString(source),
        "target" -> Json.fromString(target),
        "type"   -> Json.fromString(mountType)
      )
  }
}

/** Allows automatic provisioning of a user's dotfiles via a git repository.
  *
  * See also:
  * https://code.visualstudio.com/docs/devcontainers/containers#_personalizing-with-dotfile-repositories
  */
case class Dotfiles(
    repository: String,
    targetPath: String,
    installCommand: String
)
