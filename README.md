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
