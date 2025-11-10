package com.gu.devenv

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import cats._
import cats.syntax.all._

import scala.util.Try

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
    updateRemoteUserUID: Boolean = true,
    capAdd: List[String] = Nil,
    securityOpt: List[String] = Nil
)

case class UserConfig(
    plugins: Plugins,
    dotfiles: Option[Dotfiles]
)

enum ForwardPort {
  case SamePort(port: Int)
  case DifferentPorts(hostPort: Int, containerPort: Int)
}

object ForwardPort {

  /** we express container/host port forwards as either:
    *   - an Int (same port) (e.g. 8080)
    *   - a String with format "hostPort:containerPort" (e.g. "8000:9000")
    */
  given Decoder[ForwardPort.SamePort] = Decoder[Int].flatMap { portNumber =>
    Decoder.instance { c =>
      validatePortNumber(portNumber, c).map { validPort =>
        SamePort(validPort)
      }
    }
  }

  given Decoder[ForwardPort.DifferentPorts] = Decoder[String].flatMap { portString =>
    Decoder.instance { c =>
      portString.split(":") match {
        case Array(hostPortStr, containerPortStr) =>
          for {
            hostPort <- Try(hostPortStr.toInt).toEither.leftMap(_ =>
              DecodingFailure(
                s"Invalid host port: $hostPortStr",
                c.history
              )
            )
            containerPort <- Try(containerPortStr.toInt).toEither.leftMap(_ =>
              DecodingFailure(
                s"Invalid container port: $containerPortStr",
                c.history
              )
            )
            validHostPort      <- validatePortNumber(hostPort, c)
            validContainerPort <- validatePortNumber(containerPort, c)
          } yield DifferentPorts(validHostPort, validContainerPort)
        case _ =>
          Left(
            DecodingFailure(
              s"Invalid port mapping format: $portString. Expected format 'hostPort:containerPort'.",
              c.history
            )
          )
      }
    }
  }
  given Decoder[ForwardPort] = summon[Decoder[ForwardPort.SamePort]]
    .or(summon[Decoder[ForwardPort.DifferentPorts]].widen)

  private def validatePortNumber(
      port: Int,
      c: io.circe.HCursor
  ): Either[DecodingFailure, Int] =
    if (port >= 1 && port <= 65535)
      Right(port)
    else
      Left(
        DecodingFailure(
          s"Invalid port number: $port. Port numbers must be between 1 and 65535.",
          c.history
        )
      )

  given Encoder[ForwardPort] = Encoder.instance {
    case SamePort(port) => Json.fromInt(port)
    case DifferentPorts(hostPort, containerPort) =>
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
