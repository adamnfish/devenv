# Configuration Reference

This document provides detailed specifications for the project and user configuration files used by devenv.

## Overview

**Project config**: `.devcontainer/devenv.yaml` - Project-specific settings (image, ports, plugins, commands). Checked into version control.

**User config**: `~/.config/devenv/devenv.yaml` - Personal preferences (dotfiles, additional plugins). Merged with project config for the user-specific devcontainer.

Two devcontainer files are generated:
- `.devcontainer/user/devcontainer.json` - Merged config with your personal settings
- `.devcontainer/shared/devcontainer.json` - Project-only config for team use

Your user-specific file is excluded from the Git repository with a .gitignore entry. The general project file can be checked in to provide a project environment for cloud-based editors.

## Project Configuration Spec

The project config (`.devcontainer/devenv.yaml`) supports the following fields:

| Field                 | Required | Description                                                                                     | Default                                       |
|-----------------------|----------|-------------------------------------------------------------------------------------------------|-----------------------------------------------|
| `name`                | Yes      | Project name, used as the devcontainer name                                                     | -                                             |
| `image`               | No       | Base Docker image                                                                               | `mcr.microsoft.com/devcontainers/base:ubuntu` |
| `modules`             | No       | List of module names to enable (see Modules section)                                            | `[]`                                          |
| `forwardPorts`        | No       | Port forwarding config, either as integer (maps same port) or `"hostPort:containerPort"` string | `[]`                                          |
| `remoteEnv`           | No       | Environment variables set in the remote/container environment                                   | `[]`                                          |
| `containerEnv`        | No       | Environment variables set at container creation time                                            | `[]`                                          |
| `plugins`             | No       | IDE plugins to install (`intellij`: list of plugin IDs, `vscode`: list of extension IDs)        | `{}`                                          |
| `mounts`              | No       | Volume mounts, either as Docker mount strings or objects with `source`, `target`, and `type`    | `[]`                                          |
| `postCreateCommand`   | No       | Commands to run once after container creation                                                   | `[]`                                          |
| `postStartCommand`    | No       | Commands to run each time the container starts                                                  | `[]`                                          |
| `features`            | No       | Dev Container features to enable (as key-value pairs)                                           | `{}`                                          |
| `remoteUser`          | No       | User account to use in the container                                                            | `vscode`                                      |
| `updateRemoteUserUID` | No       | Whether to update remote user's UID to match host                                               | `true`                                        |
| `capAdd`              | No       | Linux capabilities to add to the container (use with caution)                                   | `[]`                                          |
| `securityOpt`         | No       | Security options for the container (use with caution)                                           | `[]`                                          |

### Example

```yaml
name: my-project
image: mcr.microsoft.com/devcontainers/base:ubuntu
modules:
  - apt-updates
  - mise
forwardPorts:
  - 3000
  - "8080:80"
remoteEnv:
  - name: DEBUG
    value: "true"
plugins:
  intellij:
    - org.intellij.scala
  vscode:
    - scala-lang.scala
mounts:
  - "source=${localWorkspaceFolder}/.cache,target=/home/vscode/.cache,type=bind"
postCreateCommand:
  - cmd: "npm install"
    workingDirectory: "/workspaces/my-project"
```

## User Configuration Spec

The user config (`~/.config/devenv/devenv.yaml`) supports:

| Field      | Required | Description                                                                            |
|------------|----------|----------------------------------------------------------------------------------------|
| `plugins`  | No       | Personal IDE plugins (same structure as project config: `intellij` and `vscode` lists) |
| `dotfiles` | No       | Dotfiles repository configuration (see below)                                          |

### Dotfiles Configuration

| Field            | Required | Description                                                 |
|------------------|----------|-------------------------------------------------------------|
| `repository`     | Yes      | GitHub repository (format: `username/repo`)                 |
| `targetPath`     | Yes      | Path where dotfiles will be cloned in the container         |
| `installCommand` | Yes      | Script to run for installation (executed from `targetPath`) |

### Example

```yaml
plugins:
  vscode:
    - usernamehw.errorlens
    - eamodio.gitlens
dotfiles:
  repository: "your-github-id/your-dotfiles-repo"
  targetPath: "~/dotfiles"
  installCommand: "install.sh"
```

## Modules

Modules are pre-configured bundles of features, plugins, and commands that can be enabled in your project config. They're included in the default `.devenv` template and can be disabled by commenting them out or removing them from the list.

### Available Modules

- **`apt-updates`** - Applies apt security updates during container creation (Ubuntu/Debian only)
- **`mise`** - Installs and configures [mise](https://mise.jdx.dev/) for version management of languages and tools
- **`docker-in-docker`** - Enables running Docker containers within the devcontainer. Uses an isolated Docker daemon (not host socket) with minimal capabilities for better security. Disabled by default.
  - Image storage is ephemeral (lost on container rebuild)
  - Containers run inside the devcontainer, not directly on host network
  - Use `docker run -p 8080:8080` then access via devcontainer's forwarded ports

### Example

```yaml
# In .devcontainer/devenv.yaml
modules:
  - apt-updates
  - mise
  # - docker-in-docker
```

To enable docker-in-docker, uncomment it:

```yaml
modules:
  - apt-updates
  - mise
  - docker-in-docker  # Now enabled
```

## Dotfiles

You can configure personal dotfiles in your user config (`~/.config/devenv/devenv.yaml`) to automatically clone and install them during container creation:

```yaml
dotfiles:
  repository: "your-github-id/your-dotfiles-repo"
  targetPath: "~/dotfiles"
  installCommand: "install.sh"
```

The dotfiles setup runs after project/container setup to avoid interfering with shared configuration. The repository is cloned into the container at the specified path, and the `installCommand` is executed from there.

