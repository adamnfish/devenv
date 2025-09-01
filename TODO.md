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

### 5. Build basic config loading and merging logic
- [ ] Implement `load_project_config()` - read `.devenv/config.yml`
- [ ] Implement `load_user_config()` - read `~/.config/devenv/config.yml` (optional)
- [ ] Implement `merge_configs()` - simple dict merge with project taking precedence
- [ ] Add validation for required fields (name, image)
- [ ] Test with various config combinations

### 6. Create minimal devcontainer.json generation
- [ ] Implement `generate_devcontainer_json(merged_config)` in new file `devcontainer.py`
- [ ] Generate basic devcontainer.json structure:
  ```json
  {
    "name": "config.name",
    "image": "config.image",
    "forwardPorts": ["extracted from config.ports"],
    "runArgs": ["--label=com.devenv.managed=true", "--label=com.devenv.branch=<branch>"]
  }
  ```
- [ ] Write to temporary directory and return path
- [ ] Test generation with sample configs

### 7. Implement basic devenv create command
- [ ] Check if `.devenv/config.yml` exists (error if not)
- [ ] Load and merge configs
- [ ] Generate temporary devcontainer.json
- [ ] Generate container name: `devenv-<repo>-<branch>-vscode`
- [ ] Call devcontainer CLI: `devcontainer up --workspace-folder . --config <temp-config>`
- [ ] Clean up temporary files
- [ ] Handle basic error cases (branch already exists, Docker not running)
- [ ] Test with a real project

### 8. Add devenv list command to query Docker containers by labels
- [ ] Query Docker for containers with `com.devenv.managed=true` label
- [ ] Parse container labels to extract:
  - Branch name
  - Repository
  - Container ID
  - Status (running/stopped)
- [ ] Display in formatted table:
  ```
  BRANCH        CONTAINER_ID   STATUS    PORTS
  feature-xyz   abc123def      running   3000,5432
  main          def456ghi      stopped   8080
  ```
- [ ] Handle no containers found case
- [ ] Test with real containers

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

## Next Steps (Future Phases)
- Switch command
- Remove/cleanup commands  
- Module system
- Hook system
- IntelliJ support
- mise integration