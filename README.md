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
devenv create feature-branch --dry-run  # Preview configuration without creating container
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

## Hooks

Hook scripts allow you to customize container lifecycle events. Hooks execute with the mise environment activated, giving access to project-specific tool versions (Node.js, Python, etc.).

### Supported Hook Types

- **post_create**: Executes after container creation (ideal for installing dependencies)
- **post_start**: Executes when container starts (ideal for starting development servers)

### Hook Locations

Hooks are discovered and executed in this order:

1. **User hooks**: `~/.config/devenv/hooks/<hook_name>` (applies to all projects, runs first)
2. **Project hooks**: `.devenv/hooks/<hook_name>` (project-specific, runs after user hooks)

Both hooks will execute if present - the project hook extends rather than replaces the user hook.

### Hook Requirements

- Must be executable files (`chmod +x`)
- Can be any executable format (shell scripts, Python, etc.)
- Execute with mise environment activated (access to project tools)

### Example Hook Scripts

**Project post_create hook** (`.devenv/hooks/post_create`):
```bash
#!/bin/bash
echo "Installing project dependencies..."
npm install
pip install -r requirements.txt
echo "Running database migrations..."
npm run db:migrate
```

**User post_start hook** (`~/.config/devenv/hooks/post_start`):
```bash
#!/bin/bash
echo "Starting development services..."
npm run dev &
echo "Development server started in background"
```

**Python hook example** (`.devenv/hooks/post_create`):
```python
#!/usr/bin/env python3
import subprocess
import os

print("Setting up Python environment...")
subprocess.run(["pip", "install", "-r", "requirements.txt"])
subprocess.run(["python", "manage.py", "migrate"])
print("Python setup complete!")
```

### Hook Execution Flow

When creating a container, hooks execute in this order:

1. **mise setup**: Install and configure mise toolchain manager
2. **mise install**: Install project tools from `.mise.toml` or `.tool-versions`  
3. **post_create hooks**: Execute user hook first, then project hook (both run if present)
4. **config post_create_command**: Execute command from `.devenv/config.yml` (if specified)

When starting a container:

1. **post_start hooks**: Execute user hook first, then project hook (both run if present)
2. **config post_start_command**: Execute command from `.devenv/config.yml` (if specified)

### Testing Hooks

Use the `--dry-run` flag to preview generated configuration:

```bash
devenv create feature-branch --dry-run
```

This shows the complete `devcontainer.json` including hook execution commands.

## Docker Labels

devenv uses Docker labels for state management instead of external files. Each managed container includes labels like:

- `com.devenv.managed=true`: Identifies devenv-managed containers
- `com.devenv.repo=<name>`: Repository name
- `com.devenv.branch=<name>`: Git branch name
- `com.devenv.editor=<type>`: IDE type (vscode or jetbrains)
- `com.devenv.modules=<list>`: Comma-separated active modules

These labels enable stateless operation and reliable container discovery across sessions.
