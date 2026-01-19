# Generate with Modules Test

Tests the `devenv generate` command with a complete configuration including modules.

## Setup:
- Pre-configured `.devcontainer/devenv.yaml` with:
  - mise module
  - scala language module
  - plugins (both from modules and explicit)
  - forwardPorts
  - postCreateCommand

## Expected behavior:
- Generates `.devcontainer/user/devcontainer.json`
- Generates `.devcontainer/shared/devcontainer.json`
- Both files contain valid JSON
- Module contributions are merged correctly (mise features + scala plugins)
- Explicit plugins are merged with module plugins
- Other settings (ports, commands) are present
