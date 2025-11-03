# devenv

A CLI tool for managing devcontainer configurations. Generates both user-specific and project-specific devcontainer.json files from simple YAML configuration.

## Build

### JVM Build (Development)

```bash
# Build and run locally
sbt cli/stage
cli/target/universal/stage/bin/devenv
```

### Native Image Build (Production)

Build a standalone native executable with GraalVM Native Image. The GraalVM dependency is included using `mise`.

```bash
sbt "cli/GraalVMNativeImage/packageBin"
```

The resulting binary will be at `cli/target/graalvm-native-image/devenv`.

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

Your user-specific file is excluded from the Git repository with a .gitignore entry. The general project file can be checked in to provide a project environment for cloud-based editors.

For detailed configuration specifications, see the [Configuration Reference](docs/configuration.md).

## Testing

### Unit and Integration Tests

Use sbt to run the unit and integration tests:

```bash
sbt test
```

### E2E Tests

The project also includes end-to-end tests verify the CLI program across multiple scenarios:

```bash
./e2e/run-tests.sh
```

The E2E suite packages the CLI in dev/universal mode with `sbt stage`, runs the program against isolated temp directories, and validates the JSON output and file structure to ensure the CLI behaves correctly in real-world scenarios.

## Release

The project uses GitHub Actions to build and publish native binaries for both Linux ARM64 and macOS ARM64 as date-based development releases.

**Creating a release:**

1. Go to the [Actions tab](https://github.com/YOUR_ORG/devenv2/actions/workflows/release.yml) on GitHub

2. Click "Run workflow" and select the branch to build from

3. GitHub Actions will automatically:
    - Build native binaries for Linux ARM64 and macOS ARM64
    - Create a **draft** GitHub Release with date-based versioning (e.g., `20251103-143022`)
    - Name the binaries as `devenv-{date}-{platform}-arm64`
    - Mark the release as a prerelease

4. **Manually verify and publish the release:**
    - Go to the [Releases page](https://github.com/YOUR_ORG/devenv2/releases) on GitHub
    - Review the draft release
    - Test the binaries if needed
    - Click "Publish release" when ready

**Version management:**

- Releases use date-based versioning: `YYYYMMDD-HHMMSS` (e.g., `20251103-143022`)
- The `version` in `build.sbt` is for development/snapshots only
- All releases are marked as prereleases to indicate development status

**Note:** The release workflow is **manual only** - it will not run automatically on pushes or merges. This gives you full control over when to cut a release.

Users can install the release binaries:

```bash
# macOS Apple Silicon
curl -L -o devenv https://github.com/YOUR_ORG/devenv2/releases/download/20251103-143022/devenv-20251103-143022-macos-arm64
chmod +x devenv
sudo mv devenv /usr/local/bin/
```

