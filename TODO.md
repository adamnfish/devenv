# devenv Implementation TODO

## Overview

Building `devenv` - a CLI tool for managing ephemeral, branch-scoped Dev Container environments with IDE integration. Starting with small, incremental steps to establish a working foundation.

## Phase 1: Foundation (Small Steps)

### 1. Set up basic Python project structure ✅ COMPLETED
- [x] Create `pyproject.toml` with minimal dependencies (click, pyyaml, docker)
- [x] Create basic directory structure:
  ```
  src/devenv/
  ├── __init__.py
  ├── __main__.py       # Entry point
  ├── cli.py            # Click command definitions
  └── utils/
      ├── __init__.py
      ├── docker.py     # Docker operations
      └── config.py     # Config loading
  ```
- [x] Set up entry point script that can be called as `devenv`
- [x] Test basic CLI responds to `devenv --help`
- [x] Added `.mise.toml` with Python 3.11 for toolchain management
- [x] Added comprehensive `.gitignore` file
- [x] Installed package in development mode with `mise exec -- python -m pip install -e .`

### 2. Implement simple CLI framework with basic commands ✅ COMPLETED
- [x] Create Click group in `cli.py` with main command structure
- [x] Add placeholder commands:
  - `devenv init` - prints "init command (not implemented)"
  - `devenv create <branch>` - prints "create command (not implemented)"  
  - `devenv list` - prints "list command (not implemented)"
- [x] Test all commands respond correctly with help text
- [x] Add basic error handling and version flag
- [x] Verified all commands work: `mise exec -- devenv --help`, `devenv init`, `devenv create test`, `devenv list`

### 3. Create basic Docker label utilities for container discovery ✅ COMPLETED
- [x] Implement `docker.py` with functions:
  - `find_devenv_containers()` - query all containers with `com.devenv.managed=true`
  - `find_container_by_branch(branch, repo)` - find specific container
  - `get_container_labels(container)` - extract devenv labels
- [x] Test with mock/actual Docker containers
- [x] Handle Docker not running gracefully

### 4. Implement devenv init command with minimal config generation ✅ COMPLETED
- [x] Simplified approach: Generate default config with project name from directory
- [x] Uses vanilla Ubuntu 24.04 LTS base image (mise handles toolchain management)
- [x] Generate minimal `.devenv/config.yml` with:
  ```yaml
  name: "project-directory-name"
  image: "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"
  # No default ports - only added if --port flag used
  ```
- [x] Create `.devenv/` directory if it doesn't exist
- [x] Handle existing config file with confirmation prompt
- [x] Support `--force` flag to overwrite without prompting
- [x] Support `--port 3000:3000` flag to add single port mapping
- [x] Test with sample projects in test-sandbox/

### 5. Build basic config loading and merging logic ✅ COMPLETED
- [x] Implement `load_project_config()` - read `.devenv/config.yml`
- [x] Implement `load_user_config()` - read `~/.config/devenv/` directory structure  
- [x] Implement `merge_configs()` - proper separation of concerns (project vs user config)
- [x] Add validation for required fields (name, image)
- [x] Test with various config combinations
- [x] Added comprehensive unit and integration tests

### 6. Create minimal devcontainer.json generation ✅ COMPLETED
- [x] Implement `generate_devcontainer_json(merged_config)` in new file `devcontainer.py`
- [x] Generate comprehensive devcontainer.json structure with:
  - Container naming, image, port forwarding
  - Environment variables, features, mounts  
  - IDE customizations (VS Code & JetBrains)
  - mise integration for toolchain management
  - Docker labels for state management
- [x] Write to temporary directory with context manager cleanup
- [x] Test generation with sample configs
- [x] Added comprehensive unit tests (20 test cases)

### 7. Implement basic devenv create command ✅ COMPLETED
- [x] Check if `.devenv/config.yml` exists (error if not)
- [x] Load and merge configs using new config system
- [x] Generate temporary devcontainer.json using new devcontainer module
- [x] Generate container name: `devenv-<repo>-<branch>-<editor>`
- [x] Call devcontainer CLI: `devcontainer up --workspace-folder . --config <temp-config>`
- [x] Clean up temporary files automatically
- [x] Handle error cases (branch already exists, Docker not running, missing devcontainer CLI)
- [x] Support `--modules`, `--editor`, and `--ports` flags
- [x] IDE integration (VS Code launch, JetBrains manual connection)

### 8. Add devenv list command to query Docker containers by labels ✅ COMPLETED
- [x] Query Docker for containers with `com.devenv.managed=true` label
- [x] Parse container labels to extract:
  - Branch name, Repository, Container ID, Status, Editor
  - Active modules, Port mappings
- [x] Display in formatted table:
  ```
  BRANCH        CONTAINER_ID   STATUS    EDITOR    PORTS
  feature-xyz   abc123def      running   vscode    3000,5432
  main          def456ghi      stopped   jetbrains 8080
  ```
- [x] Handle no containers found case
- [x] Show modules and total container count

## Implementation Notes

### Dependencies to Add
```toml
[dependencies]
click = "^8.0"
pyyaml = "^6.0"
docker = "^6.0"
```

### Key Design Decisions
- Use Docker Python SDK for reliable container operations
- Store all state in Docker labels (no external state files)
- Generate devcontainer.json dynamically in temp directory
- Start with VS Code support only (IntelliJ later)
- Minimal error handling initially, expand as needed

### Testing Strategy
- Manual testing with real Docker containers
- Start with happy path testing
- Add error case handling incrementally
- Test with sample projects (Node.js, Python)

### Success Criteria for Phase 1
- Can run `devenv init` in a project and generate config
- Can run `devenv create main` and launch VS Code with dev container
- Can run `devenv list` and see the created container
- All basic error cases handled gracefully

## Phase 2: Essential Features ✅ COMPLETED

### 9. Implement devenv switch command ✅ COMPLETED
- [x] Query Docker for existing containers by branch/repo
- [x] Support `--editor` flag to override container's default editor
- [x] Automatically start stopped containers
- [x] Launch VS Code automatically for vscode containers
- [x] Provide manual connection instructions for JetBrains
- [x] Show helpful error messages with available containers when branch not found
- [x] Get workspace folder from container labels for accurate VS Code launch

### 10. Implement devenv rm command ✅ COMPLETED
- [x] Find and validate container exists before removal
- [x] Display container information before removal (branch, name, ID, editor, modules, status)
- [x] Support `--force` flag to skip confirmation prompt
- [x] Support `--volumes` flag to remove associated volumes
- [x] Gracefully stop running containers before removal
- [x] Comprehensive error handling with clear messages
- [x] Confirmation prompts for safety

### 11. Add comprehensive module system ✅ COMPLETED
- [x] Built-in module definitions (claude-code, docker-in-docker)
- [x] Module validation and error handling
- [x] Dynamic devcontainer.json modification based on active modules
- [x] Support for:
  - Additional mounts (Claude config, Docker socket)
  - Environment variables (Claude flags)
  - Features (Docker-in-Docker)
  - Run arguments
  - Post-create commands (package installation)
- [x] `devenv modules` command to list available modules
- [x] Module integration in `devenv create --modules` flag
- [x] Comprehensive test suite (16 test cases)

## Current Status: Phase 2 Complete! 🎉

### **What We've Built:**

**Complete Container Lifecycle Management:**
- ✅ `devenv init` - Initialize projects
- ✅ `devenv create` - Create containers with module support  
- ✅ `devenv list` - List and inspect containers
- ✅ `devenv switch` - Connect to existing containers
- ✅ `devenv rm` - Remove containers safely
- ✅ `devenv modules` - Browse available modules

**Advanced Module System:**
- ✅ **claude-code**: AI development assistance with Claude Code integration
- ✅ **docker-in-docker**: Docker access inside containers

**Professional Quality:**
- ✅ **62 passing tests** (increased from 46)
- ✅ **Comprehensive error handling** with helpful messages
- ✅ **End-to-end verified** with real containers and modules
- ✅ **Production-ready** architecture with proper separation of concerns

## Next Steps (Phase 3)
- Hook system (pre/post lifecycle events)
- Taint/purge system for advanced cleanup workflows
- Additional built-in modules
- Project language/framework detection for smarter defaults
- Custom module support (.devenv/modules/)
- Enhanced IntelliJ integration