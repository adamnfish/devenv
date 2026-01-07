# Generate with Modules Test

Tests the `devenv generate` command with a complete configuration including modules.

## Setup:
- Pre-configured `.devcontainer/devenv.yaml` with:
  - apt-updates module
  - mise module
  - plugins
  - forwardPorts
  - postCreateCommand

## Expected behavior:
- Generates `.devcontainer/user/devcontainer.json`
- Generates `.devcontainer/shared/devcontainer.json`
- Both files contain valid JSON
- Module contributions are merged correctly
- Plugins and other settings are present
