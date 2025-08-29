# Ephemeral DevEnv CLI – Technical Specification & Project Plan

## Overview

**Goal:** Build a CLI tool (`devenv`) that enables ephemeral, branch-scoped Dev Container environments with IDE integration (VS Code & IntelliJ). It supports personal customizations, optional modules, and safe teardown workflows.

**Core Principles:**
- Dev containers are ephemeral, per-feature-branch, isolate-side-effects, and easily cleaned up.
- Personal customizations come from the host (not required to publish dotfiles).
- IDE plugin lists are merged from org defaults, project, and user preferences.
- Support dynamic modules (e.g. `claude-code`) even post-launch.
- Provide tidy CLI-based environment lifecycle: create, switch, list, tear down, purge.
- State is tracked via Docker labels/metadata, not external state files.

---

## Project Layout

### 1) User-Level Configuration (on Host)

```
~/.config/devenv/
├── plugins.vscode.txt           # Optional, one extension ID per line
├── plugins.jetbrains.txt        # Optional, one plugin ID per line
├── hooks/
│  ├── pre_build                 # Executed before container build
│  ├── post_create               # Executed right after `devcontainer up`
│  ├── post_start                # Executed after container is up and running
│  └── pre_stop                  # Executed before tearing down container
└── dotfiles/                    # Files/folders to copy into container home
                                 # ⚠️ IMPORTANT: These are NOT your actual dotfiles
                                 # Only place files here that are specifically for
                                 # dev container initialization. NO SECRETS!
```

### 2) Project-Level Configuration (in Repo)

```
my-repo/
├── .devenv/
│  ├── config.yml               # Complete project specification
│  ├── modules/                 # Custom module definitions (optional)
│  │  └── custom-module.yml     # Project-specific module
│  └── hooks/                   # Optional override scripts
│     ├── pre_build
│     ├── post_create
│     └── ...
├── .devcontainer/              # OPTIONAL - only if complex legacy setup exists
│  └── devcontainer.json        # Will be used if present, otherwise generated
└── ...                         # Project code, etc.
```

### Purpose Summary
- **Host config** = personal preferences + hooks + dotfiles (explicitly for containers)
- **Project config** = complete container specification + optional module definitions + optional hook overrides
- **State tracking** = Docker container labels and metadata (no external state.json)
- **devcontainer.json** = dynamically generated at runtime (unless explicitly provided)

---

## Project Configuration Schema (.devenv/config.yml)

The project config file serves as the single source of truth for container specification:

```yaml
# .devenv/config.yml - Complete example
name: "my-app-dev"
image: "mcr.microsoft.com/devcontainers/javascript-node:20"  # or dockerfile: ./Dockerfile

# Application settings
ports:
  - 3000:3000  # Web app
  - 5432:5432  # PostgreSQL (if using docker-compose)

environment:
  NODE_ENV: "development"
  APP_NAME: "my-app"

# Dev container features (optional)
features:
  "ghcr.io/devcontainers/features/docker-in-docker:2": {}
  "ghcr.io/devcontainers/features/git:1": {}

# IDE configurations
plugins:
  vscode:
    - dbaeumer.vscode-eslint
    - esbenp.prettier-vscode
  jetbrains:
    - com.jetbrains.plugins.node

# Available modules (can be activated via CLI)
modules:
  claude-code:
    enabled: false  # Can be overridden with --modules flag
  gpu-support:
    enabled: false

# Additional mounts (beyond the workspace)
mounts:
  - source: ./.devenv/cache
    target: /workspace/.cache
    type: bind

# Lifecycle commands
post_create_command: "npm install"
post_start_command: "npm run db:migrate"
```

### Minimal Config Example

For simple projects, the config can be minimal:

```yaml
# .devenv/config.yml - Minimal
name: "simple-api"
image: "mcr.microsoft.com/devcontainers/python:3.11"
ports:
  - 8000:8000
```

The system will provide sensible defaults for everything else.

---

## State Management via Docker Labels

All container metadata is stored as Docker labels:

```yaml
labels:
  com.devenv.managed: "true"           # Identifies devenv-managed containers
  com.devenv.repo: "my-repo"           # Repository name
  com.devenv.repo-path: "/path/to/repo" # Full path for uniqueness
  com.devenv.branch: "feature-xyz"     # Git branch name
  com.devenv.editor: "vscode"          # IDE type
  com.devenv.created: "2025-01-15T10:30:00Z"
  com.devenv.tainted: "false"          # Taint flag
  com.devenv.modules: "claude-code,gpu" # Comma-separated active modules
```

This eliminates state sync issues and provides a single source of truth.

---

## CLI Command Overview

### `devenv init`
- **Flags:** `--detect`, `--image <image>`, `--language <lang>`
- **Description:** Creates `.devenv/config.yml` with sensible defaults
- **Implementation:**
  1. Detect project language/framework (package.json, requirements.txt, etc.)
  2. Suggest appropriate base image
  3. Detect common ports from framework files
  4. Generate minimal config.yml
  5. Optionally migrate existing devcontainer.json

### `devenv create <branch>`
- **Flags:** `--modules <mod1,mod2>`, `--editor <code|jetbrains>`, `--ports <port1,port2>`
- **Description:** Build and run a new Dev Container for `<branch>`, generating devcontainer.json from project config.
- **Implementation:**
  1. Load `.devenv/config.yml` (error if not found)
  2. Merge configurations (base defaults → project → user → modules → CLI flags)
  3. Generate devcontainer.json in temp directory (including mise setup)
  4. Generate unique container name: `devenv-<repo>-<branch>-<editor>`
  5. Add all metadata as Docker labels
  6. Add mise cache mount to container mounts
  7. Run appropriate CLI pointing to temp devcontainer.json
  8. Execute hooks at appropriate stages
  9. Clean up temp files

### `devenv list`
- **Description:** Query Docker for containers with `com.devenv.managed=true` label, display formatted output
- **No state file needed** - pure Docker query
- **Output format:**
```
BRANCH        CONTAINER_ID   EDITOR    PORTS              MODULES       TAINTED
feature-xyz   abc123def      vscode    3000,5432,6379     claude-code   false
main          def456ghi      jetbrains 8080               -             false
```

### `devenv switch <branch>`
- **Flags:** `--editor <...>`
- **Description:** Attach to existing container (found via Docker labels)
- **Implementation:** Query Docker for matching container, then attach IDE

### `devenv module add <branch> <module>`
- **Description:** Stops container, rebuilds with additional module config merged, restarts
- **Updates label:** `com.devenv.modules` 

### `devenv taint <branch>`
- **Description:** Updates Docker container label `com.devenv.tainted=true`

### `devenv rm <branch>`
- **Flags:** `--volumes`
- **Description:** Stops and removes container found via Docker query

### `devenv purge`
- **Flags:** `--tainted`, `--older-than=Nd`, `--volumes`
- **Description:** Query Docker with label filters, bulk delete matching containers

### `devenv shell <branch>` *(Lower Priority)*
- **Description:** Quick terminal access via `docker exec -it <container> /bin/bash`

---

## Toolchain Management with mise-en-place

All dev containers will use [mise](https://mise.jdx.dev/) (mise-en-place) for consistent toolchain management across environments. This ensures developers have the exact versions of languages and tools required for each project, whether working in containers or directly on the host.

### mise Integration
- **Automatic installation**: mise is installed in all containers during creation
- **Shared cache**: The host's `~/.local/share/mise` directory is mounted to avoid re-downloading tools
- **Shell activation**: Automatic setup for bash and zsh (other shells can be configured via dotfiles)
- **Project tools**: Defined using mise's standard configuration files in the project root

### Tool Configuration
Projects should define their tool versions using mise's standard configuration files, which work both in containers and on host machines:

**Option 1: `.mise.toml`** (recommended)
```toml
[tools]
node = "20.11.0"
python = "3.11"
go = "1.21"
```

**Option 2: `.tool-versions`** (asdf-compatible format)
```
node 20.11.0
python 3.11
go 1.21
```

These files are checked into the project repository and will be automatically detected and used by mise in both container and host environments. The mise cache mount ensures fast container startup times by reusing previously downloaded tools.

The devenv tool ships with several built-in modules that can be activated via the `--modules` flag:

### claude-code
Integrates Claude Code for AI-assisted development:
- Mounts `~/.claude` from host
- Installs `@anthropic/claude-code` package
- Sets appropriate environment variables

### gpu-support
Enables GPU passthrough for ML workloads:
- Adds `--gpus=all` to container runtime
- Configures CUDA environment
- Installs necessary GPU features

### docker-in-docker
Allows running Docker commands inside the container:
- Installs Docker feature
- Mounts Docker socket (with security considerations)

Usage: `devenv create feature-ml --modules claude-code,gpu-support`

---

## Plugin Merging Logic

Plugins are merged with deduplication, using plugin IDs only (no versions):

```python
# Pseudocode for merging
def merge_plugins(org_plugins, project_plugins, user_plugins):
    # Later sources override earlier ones
    # Using dict to naturally dedupe by ID
    plugins = {}
    for plugin_id in org_plugins:
        plugins[plugin_id] = True
    for plugin_id in project_plugins:
        plugins[plugin_id] = True
    for plugin_id in user_plugins:
        plugins[plugin_id] = True
    return list(plugins.keys())
```

---

## Technical Decisions

### Why Generate devcontainer.json?
- **Single source of truth** - `.devenv/config.yml` contains all configuration
- **Reduces duplication** - No need to maintain two config files
- **Backwards compatible** - Can still use existing devcontainer.json if present
- **Dynamic generation** - Allows runtime modification based on modules, user prefs, etc.
- **Cleaner project repos** - One config file instead of multiple

### Configuration Precedence
1. Built-in defaults (language-specific)
2. Project `.devenv/config.yml`
3. User `~/.config/devenv/`
4. Module configurations
5. CLI flags (highest priority)
6. Existing `.devcontainer/devcontainer.json` (if present, skip generation)

### Container Naming Scheme
Format: `devenv-<repo>-<branch>-<editor>`
- Repo: Sanitized repository name
- Branch: Sanitized branch name (must be unique per repo)
- Editor: vscode or jetbrains

Example: `devenv-myapp-feature-auth-vscode`

### Language Detection for Smart Defaults

The `devenv init` command will detect and provide appropriate defaults:

```python
# Example detection logic
def detect_project_type(project_path):
    if exists("package.json"):
        return {"language": "javascript", "image": "node:20", "ports": [3000]}
    elif exists("requirements.txt") or exists("pyproject.toml"):
        return {"language": "python", "image": "python:3.11", "ports": [8000]}
    elif exists("go.mod"):
        return {"language": "go", "image": "golang:1.21", "ports": [8080]}
    elif exists("Cargo.toml"):
        return {"language": "rust", "image": "rust:latest", "ports": [3000]}
    # ... etc
```

---

## Documentation Requirements

### Critical User Documentation

1. **Dotfiles Security Warning**
   ```markdown
   ⚠️ IMPORTANT: Dotfiles Directory Security
   
   The ~/.config/devenv/dotfiles/ directory is for dev container initialization ONLY.
   
   DO NOT:
   - Copy your actual home directory dotfiles here
   - Include any files containing secrets, tokens, or passwords
   - Symlink to sensitive configuration files
   
   DO:
   - Create minimal, container-specific configurations
   - Use environment variables or secrets management for sensitive data
   - Include only files you're comfortable having in ephemeral environments
   ```

2. **Branch Naming Requirements**
   - Branch names must be unique within a repository
   - Use descriptive names like `feature-auth` or `bugfix-memory-leak`
   - Avoid special characters that aren't filesystem-safe

3. **IDE-Specific Guides**
   - VS Code: Automatic connection and extension sync
   - IntelliJ: Manual connection steps via Services panel

---

## Task Breakdown

### Phase 1: Core Foundation
1. **Docker label-based state management**
   - Design label schema
   - Implement Docker query wrappers
   - Container name generation logic

2. **Project initialization (`devenv init`)**
   - Language/framework detection
   - Config.yml generation
   - Migration from existing devcontainer.json

3. **Basic create/list commands**
   - Config loading and merging logic
   - devcontainer.json generation
   - VS Code integration via devcontainer CLI
   - List containers via Docker label queries

4. **Hook system**
   - Hook discovery and execution order
   - Project vs user hook precedence
   - Error handling for failed hooks

5. **Switch command**
   - Container discovery via labels
   - IDE attachment logic

### Phase 2: Essential Features
6. **Taint & rm commands**
   - Label update operations
   - Safe cleanup with volume handling

7. **Plugin merging**
   - Deduplication logic
   - Config file parsing

8. **Dotfile management**
   - Safe copying mechanism
   - Documentation and warnings

9. **Port forwarding**
   - Port configuration parsing
   - Display in list command

10. **Built-in modules**
    - Implement claude-code, gpu-support, docker-in-docker
    - Module activation via CLI flags
    - Module configuration merging

11. **mise integration**
    - Install mise in all containers via post-create hook
    - Mount host mise cache directory (~/.local/share/mise)
    - Configure shell activation for bash and zsh
    - Run `mise install` to detect and install tools from .mise.toml, .tool-versions, etc.

### Phase 3: Advanced Features
11. **Purge workflow**
    - Advanced filtering by age, taint status
    - Batch operations with confirmation

12. **IntelliJ optimization**
    - Research ijdevc automation options
    - Document manual steps clearly

13. **Shell command**
    - Quick terminal access implementation

### Phase 4: Future Enhancements
14. **Custom module system**
    - Module definition schema for project-specific modules
    - Dynamic module loading from `.devenv/modules/`
15. **Resource limits**
16. **CI/CD integration**
17. **Multi-container support**
18. **Volume recovery for deleted containers**

---

## Example Implementation Snippets

### Generate devcontainer.json from config.yml
```python
def generate_devcontainer(config: dict, modules: list, user_config: dict) -> dict:
    """Generate devcontainer.json from merged configurations"""
    
    # Base mounts always include mise cache
    base_mounts = [
        {
            "source": "${localEnv:HOME}/.local/share/mise",
            "target": "/home/vscode/.local/share/mise",
            "type": "bind"
        }
    ]
    
    devcontainer = {
        "name": config.get("name", "dev-environment"),
        "image": config.get("image"),  # or "dockerFile" if specified
        "forwardPorts": [p.split(":")[0] for p in config.get("ports", [])],
        "remoteEnv": config.get("environment", {}),
        "mounts": base_mounts + config.get("mounts", []),
        "features": config.get("features", {}),
        "customizations": {
            "vscode": {
                "extensions": merge_plugins(
                    config.get("plugins", {}).get("vscode", []),
                    user_config.get("plugins", {}).get("vscode", [])
                )
            },
            "jetbrains": {
                "plugins": merge_plugins(
                    config.get("plugins", {}).get("jetbrains", []),
                    user_config.get("plugins", {}).get("jetbrains", [])
                )
            }
        },
        "postCreateCommand": generate_post_create_command(config),
        "postStartCommand": config.get("post_start_command"),
        "runArgs": ["--label=com.devenv.managed=true", ...]
    }
    
    # Apply active modules
    for module_name in modules:
        module = load_builtin_module(module_name)
        devcontainer = apply_module_to_config(devcontainer, module)
    
    return devcontainer

def generate_post_create_command(config: dict) -> str:
    """Generate post-create command including mise setup"""
    commands = []
    
    # Install mise
    commands.append("curl -fsSL https://mise.run | sh")
    
    # Setup shell activation
    commands.append('echo \'eval "$(~/.local/bin/mise activate bash)"\' >> ~/.bashrc')
    commands.append('echo \'eval "$(~/.local/bin/mise activate zsh)"\' >> ~/.zshrc')
    
    # Install tools from project's mise config files (.mise.toml, .tool-versions, etc)
    # mise will automatically detect and use these files
    commands.append("~/.local/bin/mise install")
    
    # Add user's post_create_command if specified
    if "post_create_command" in config:
        commands.append(config["post_create_command"])
    
    return " && ".join(commands)

def create_container(branch: str, modules: list, editor: str):
    """Main creation flow"""
    # Check for existing devcontainer.json
    if exists(".devcontainer/devcontainer.json"):
        print("Using existing devcontainer.json")
        devcontainer_path = ".devcontainer/devcontainer.json"
    else:
        # Generate from config.yml
        if not exists(".devenv/config.yml"):
            error("No .devenv/config.yml found. Run 'devenv init' first.")
        
        config = load_yaml(".devenv/config.yml")
        user_config = load_yaml("~/.config/devenv/config.yml")
        
        devcontainer = generate_devcontainer(config, modules, user_config)
        
        # Write to temp location
        temp_dir = create_temp_dir()
        devcontainer_path = f"{temp_dir}/devcontainer.json"
        write_json(devcontainer_path, devcontainer)
    
    # Run devcontainer CLI
    run_devcontainer_cli(devcontainer_path, branch, editor)
```

### Smart Project Initialization
```python
def init_project(detect: bool = True):
    """Initialize a new project with devenv"""
    
    if exists(".devenv/config.yml"):
        if not confirm("Config exists. Overwrite?"):
            return
    
    if detect:
        detected = detect_project_type(".")
        config = generate_config_from_detection(detected)
    else:
        config = interactive_config_builder()
    
    # Create .devenv directory
    makedirs(".devenv/modules", exist_ok=True)
    makedirs(".devenv/hooks", exist_ok=True)
    
    # Write config
    write_yaml(".devenv/config.yml", config)
    
    # Migrate existing devcontainer.json if present
    if exists(".devcontainer/devcontainer.json"):
        if confirm("Migrate existing devcontainer.json?"):
            migrate_devcontainer_to_config()
    
    print(f"✓ Initialized devenv for {config['name']}")
    print(f"  Image: {config.get('image', 'auto-detected')}")
    print(f"  Ports: {config.get('ports', [])}")
    print("\nNext: Run 'devenv create <branch>' to start developing")
```

### Query Containers by Branch
```bash
# Find all containers for a branch
docker ps -a --filter "label=com.devenv.managed=true" \
             --filter "label=com.devenv.branch=feature-xyz" \
             --format "table {{.Names}}\t{{.Labels}}"
```

### Update Taint Status
```bash
# Mark container as tainted
docker update --label-add "com.devenv.tainted=true" <container_id>
```

---

## Implementation Guidance for AI Assistants

### Key Dependencies and Requirements

**External CLI Tools Required:**
- `docker` - Container runtime
- `devcontainer` CLI - VS Code's devcontainer CLI (npm install -g @devcontainers/cli)
- `ijdevc` - JetBrains IDE devcontainer CLI (if supporting IntelliJ)
- `git` - For branch detection and repo information

**Suggested Technology Stack:**
- **Language**: Python 3.10+ (has good Docker SDK support)
- **Config parsing**: YAML (PyYAML or similar)
- **CLI framework**: Click (Python)
- **Docker interaction**: Docker SDK or shell commands

### Core Implementation Patterns

**1. Docker Label Queries**
```python
# Example: Find all devenv containers
def list_devenv_containers():
    filters = {"label": ["com.devenv.managed=true"]}
    containers = docker_client.containers.list(all=True, filters=filters)
    return containers

# Example: Find container by branch
def find_container_by_branch(branch: str, repo: str):
    filters = {
        "label": [
            "com.devenv.managed=true",
            f"com.devenv.branch={branch}",
            f"com.devenv.repo={repo}"
        ]
    }
    containers = docker_client.containers.list(all=True, filters=filters)
    return containers[0] if containers else None
```

**2. Config File Merging**
```python
# Priority order (lowest to highest)
DEFAULT_CONFIG = {...}  # Built-in defaults

def load_merged_config(project_path: str) -> dict:
    config = DEFAULT_CONFIG.copy()
    
    # Load project config
    if os.path.exists(f"{project_path}/.devenv/config.yml"):
        project_config = load_yaml(f"{project_path}/.devenv/config.yml")
        config = deep_merge(config, project_config)
    
    # Load user config
    user_config_path = os.path.expanduser("~/.config/devenv/config.yml")
    if os.path.exists(user_config_path):
        user_config = load_yaml(user_config_path)
        config = deep_merge(config, user_config)
    
    return config
```

**3. Temporary File Management**
```python
import tempfile
import contextlib

@contextlib.contextmanager
def temp_devcontainer_json(config: dict):
    """Create temporary devcontainer.json, yield path, then cleanup"""
    with tempfile.TemporaryDirectory() as tmpdir:
        devcontainer_path = os.path.join(tmpdir, "devcontainer.json")
        with open(devcontainer_path, 'w') as f:
            json.dump(config, f, indent=2)
        yield devcontainer_path
```

### Error Handling Scenarios

The implementation should handle these common error cases gracefully:

1. **No .devenv/config.yml found** → Suggest running `devenv init`
2. **Branch already has container** → Suggest `devenv switch` instead
3. **Container name conflicts** → Add incremental suffix or error clearly
4. **Docker not running** → Clear error message with fix instructions
5. **Missing devcontainer CLI** → Provide installation instructions
6. **Stale containers in Docker** → Detect and offer to clean up
7. **Port conflicts** → Detect and suggest alternative ports
8. **Missing mise config files** → Continue without error (mise will use defaults)

### Testing Considerations

**Unit Test Scenarios:**
- Config merging with various precedence cases
- Docker label generation and parsing
- Container name sanitization (special characters, length limits)
- Command argument parsing

**Integration Test Scenarios:**
- Create container → verify labels → list → switch → remove lifecycle
- Module addition to existing container
- Hook execution order
- Port forwarding verification
- mise installation and tool detection

**Test Fixtures Needed:**
- Sample project with `.devenv/config.yml`
- Sample project with legacy `.devcontainer/devcontainer.json`
- Sample `.mise.toml` and `.tool-versions` files
- Mock user config directory

### File Structure Suggestion

```
devenv/
├── src/
│   ├── __main__.py           # Entry point
│   ├── cli.py                # Click/argparse command definitions
│   ├── config.py             # Config loading and merging
│   ├── docker_utils.py       # Docker operations and label management
│   ├── devcontainer.py       # devcontainer.json generation
│   ├── modules.py            # Built-in module definitions
│   ├── hooks.py              # Hook execution logic
│   └── mise.py               # mise integration utilities
├── tests/
│   ├── test_config.py
│   ├── test_docker_utils.py
│   └── fixtures/
│       └── sample_projects/
└── setup.py / pyproject.toml
```

### Development Workflow Suggestions

1. **Start with `devenv init` and `devenv create`** - These form the core experience
2. **Use mock/dry-run mode** - Add `--dry-run` flag to test without creating containers
3. **Log all shell commands** - Add `--verbose` to show exact docker/devcontainer commands
4. **Validate early** - Check for required tools (docker, devcontainer CLI) on startup
5. **Fail fast with helpful errors** - Include suggested fixes in error messages

### Common Pitfalls to Avoid

1. **Don't assume container names are unique** - Always use full label queries
2. **Don't parse docker output with regex** - Use JSON format or SDK
3. **Don't store state that can be queried** - Always query Docker for truth
4. **Don't hardcode paths** - Use os.path.expanduser() and Path objects
5. **Don't forget Windows compatibility** - Use pathlib for cross-platform paths
6. **Don't assume shells** - Check if bash/zsh exist before configuring
7. **Don't block on IDE operations** - Some IDE integrations may need async handling