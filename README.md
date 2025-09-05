# devenv

A CLI tool for managing ephemeral, branch-scoped Dev Container environments with IDE integration.

## Installation

1. Install dependencies:
   ```bash
   npm install -g @devcontainers/cli
   ```

2. Install devenv in development mode:
   ```bash
   git clone <repository-url>
   cd devenv
   mise exec -- python -m pip install -e .
   ```

## Usage

### devenv init
Initialize a new devenv project with configuration file.

```bash
devenv init
devenv init --port 3000:3000
devenv init --force
```

### devenv create
Create a new dev container for the specified branch.

```bash
devenv create main
devenv create feature-auth --modules claude-code
devenv create api-work --modules claude-code,docker-in-docker --editor jetbrains
```

### devenv list
List all managed dev containers with their status and metadata.

```bash
devenv list
```

### devenv switch
Switch to an existing dev container for the specified branch.

```bash
devenv switch main
devenv switch feature-auth --editor vscode
```

### devenv rm
Remove a dev container for the specified branch.

```bash
devenv rm main
devenv rm feature-auth --volumes
devenv rm old-branch --force
```

### devenv modules
List available built-in modules.

```bash
devenv modules
```

## Modules

Built-in modules extend container functionality:

- **claude-code**: Integrates Claude Code for AI-assisted development
- **docker-in-docker**: Allows running Docker commands inside the container

## Docker Labels

devenv uses Docker labels for state management instead of external files. Each managed container includes labels like:

- `com.devenv.managed=true`: Identifies devenv-managed containers
- `com.devenv.repo=<name>`: Repository name
- `com.devenv.branch=<name>`: Git branch name
- `com.devenv.editor=<type>`: IDE type (vscode or jetbrains)
- `com.devenv.modules=<list>`: Comma-separated active modules

These labels enable stateless operation and reliable container discovery across sessions.
