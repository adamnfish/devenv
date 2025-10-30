# devenv

A CLI tool for managing devcontainer configurations. Generates both user-specific and project-specific devcontainer.json files from simple YAML configuration.

## Build

```bash
sbt cli/stage
cli/target/universal/stage/bin/devenv
```

Requires Java 8+. Build with Java 21 for optimal performance.

## Package

```bash
# Create distributable archive
sbt cli/universal:packageBin  # Creates cli/target/universal/devenv-*.zip
```

## Usage

```bash
# Initialize .devcontainer directory structure
devenv init

# Generate devcontainer.json files from config
devenv generate
```

## Configuration

**Project config**: `.devcontainer/devenv.yaml` - Project-specific settings (image, ports, plugins, commands). Checked into version control.

**User config**: `~/.config/devenv/devenv.yaml` - Personal preferences (dotfiles, additional plugins). Merged with project config for the user-specific devcontainer.

Two devcontainer files are generated:
- `.devcontainer/user/devcontainer.json` - Merged config with your personal settings
- `.devcontainer/shared/devcontainer.json` - Project-only config for team use

### Dotfiles Support

You can configure your personal dotfiles repository in your user config (`~/.config/devenv/devenv.yaml`). When you run `devenv generate`, the dotfiles will be automatically cloned and installed in your user-specific devcontainer during the `postCreateCommand` phase.

**Example user config with dotfiles:**

```yaml
plugins:
  vscode:
    - "GitHub.copilot"
  intellij:
    - "com.github.copilot"

dotfiles:
  repository: "https://github.com/yourusername/dotfiles.git"
  targetPath: "~/dotfiles"
  installCommand: "./install.sh"
```

The dotfiles configuration has three fields:
- **`repository`** - Git repository URL for your dotfiles
- **`targetPath`** - Where to clone the dotfiles in the container
- **`installCommand`** - Script to run to install/setup the dotfiles (relative to targetPath)

This will generate shell commands in the `postCreateCommand` that clone your dotfiles repository and run the installation script. Dotfiles are only included in the user-specific devcontainer, not in the shared one.

**Note:** Dotfiles setup runs after project and module setup commands, ensuring your personal configuration doesn't interfere with the shared development environment setup.

## Modules

Modules are pre-configured bundles of features, plugins, and commands that can be enabled in your project config. They're included in the default `.devenv` template and can be disabled by commenting them out or removing them from the list.

**Available modules:**

- **`apt-updates`** - Applies apt security updates during container creation (Ubuntu/Debian only)
- **`mise`** - Installs [mise](https://mise.jdx.dev/) for version management of languages and tools

**Example configuration:**

```yaml
# In .devcontainer/devenv.yaml
modules:
  - apt-updates
  - mise
```

To disable a module, comment it out or remove it:

```yaml
modules:
  - apt-updates
  # - mise  (disabled)
```

## Testing

### Unit and Integration Tests

The core logic is tested with ScalaTest:

```bash
sbt test
```

Tests cover:
- Configuration parsing and merging
- Module system functionality
- File system operations
- Full init/generate workflows

### E2E Tests

End-to-end tests verify the packaged binary across multiple scenarios:

```bash
./e2e/run-tests.sh
```

The E2E suite:
- Builds the packaged binary with `sbt stage`
- Tests `devenv init` from scratch
- Tests `devenv generate` with modules (apt-updates, mise)
- Tests generate with project configuration
- Validates JSON output and file structure
- Runs in isolated temp directories

The E2E tests ensure the CLI behaves correctly in real-world usage scenarios.
