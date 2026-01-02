# devenv

A CLI tool for managing devcontainer configurations for your projects.

Managing devcontainer.json files can be tedious and error-prone. The devcontainer specification is very detailed, exposing a lot of options to engineers. It also doesn't provide a way to separate team-wide project settings from personal user preferences, forcing developers to either commit their personal configs or manually maintain separate files. `devenv` solves both of these problems by providing a simple configuration format that lets you define both project-wide and user-specific settings in YAML format. It then generates the appropriate devcontainer.json files for you.

For example, this `.devcontainer/devenv.yaml` file in your project:

```yaml
name: "devenv"
modules:  # Pre-configured bundles (e.g., apt-updates, mise for version management)
  - apt-updates
  - mise
plugins:
  vscode:
    - "scala-lang.scala"
  intellij:
    - "org.intellij.scala"
```

and this user config file in your home directory at `~/.config/devenv/devenv.yaml`:

```yaml
dotfiles:
  repository: https://github.com/adamnfish/dotfiles.git
  targetPath: ~/.dotfiles
  installCommand: ./install.sh
plugins:
  vscode:
    - "com.github.copilot"
  intellij:
    - "GitHub.copilot"
    - "com.mallowigi"
```

will generate two devcontainer.json files:
- `.devcontainer/shared/devcontainer.json` - with project settings only (checked into the repository)
- `.devcontainer/user/devcontainer.json` - project settings merged with your personal preferences (excluded via .gitignore entry)

You can then use your IDE (VSCode or IntelliJ) to launch into the `user` configuration for a fully personalized development environment, or the `shared` configuration for a standard project setup. The latter ensures that cloud-based IDEs like GitHub Codespaces can use the (checked-in) shared configuration to provide a simple and consistent development environment.

## Quickstart

From the root of your project, run:

```bash
devenv init
```

This will create a `.devcontainer/devenv.yaml` file with some default settings. Set the project name and any other project options in this file.

You can also create a user config file at `~/.config/devenv/devenv.yaml` to set your personal preferences (dotfiles, additional plugins, etc). These settings will be merged with the project config to create a user-specific devcontainer.json customised to your personal preferences.

Then run:

```bash
devenv generate
```

This will generate two devcontainer.json files:
- `.devcontainer/shared/devcontainer.json` - Project-wide settings
- `.devcontainer/user/devcontainer.json` - Merged project and user settings

## Build

### JVM Build (Development)

```bash
# Build and run locally
sbt cli/stage
cli/target/universal/stage/bin/devenv
```

### Native Image Build (Production)

Build a standalone native executable with GraalVM Native Image. The GraalVM dependency is included in `.tool-versions` so that it can be managed by `mise`.

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

# Check if devcontainer.json files match current config
devenv check
```

## Configuration

**Project config**: `.devcontainer/devenv.yaml` - Project-specific settings (image, ports, plugins, commands). Checked into version control.

**User config**: `~/.config/devenv/devenv.yaml` - Personal preferences (dotfiles, additional plugins). Merged with project config for the user-specific devcontainer.

Two devcontainer files are generated:
- `.devcontainer/user/devcontainer.json` - Merged config with your personal settings
- `.devcontainer/shared/devcontainer.json` - Project-only config for team use

Your user-specific file is excluded from the Git repository with a .gitignore entry. The general project file can be checked in to provide a project environment for cloud-based editors.

For detailed configuration specifications, see the [Configuration Reference](docs/configuration.md).

## Development

The [.tool-versions](./.tool-versions) file includes toolchain dependencies that can be managed by [mise](https://mise.jdx.dev/) or your preferred version manager.

### Testing

#### Unit and Integration Tests

Use sbt to run the unit and integration tests:

```bash
sbt test
```

This includes end-to-end tests in the `e2e-tests` module that create real docker containers and verify devenv's modules and features properly configure the real container environment.

#### Generation Tests

The project also includes generation tests that validate the real program output. These package the CLI in dev/universal mode with `sbt stage`, run the program against isolated temp directories, and validate the JSON output and file structure to ensure the CLI behaves correctly in real-world scenarios.

```bash
./generation-tests/run-tests.sh
```

### Release

The project uses GitHub Actions to build and publish a native binary for macOS ARM64 as a date-based development release.

> [!WARNING]
> The GitHub actions workflow does not properly sign the macOS binaries, so the workflow artifact is currently not usable.
> Building the macOS binary locally on a Macbook works ok for now, this binary can be uploaded to the draft release in Step 4, below.

**Creating a release:**

1. Go to the [Actions tab](https://github.com/adamnfish/devenv/actions/workflows/release.yml) on GitHub

2. Click "Run workflow" and select the branch to build from

3. GitHub Actions will automatically:
    - Build native binaries for macOS ARM64
    - Create a **draft** GitHub Release with date-based versioning (e.g., `20251103-143022`)
    - Name the binaries as `devenv-{date-version}-{platform}-arm64`
    - Mark the release as a prerelease

4. **Manually verify and publish the release:**
    - Go to the [Releases page](https://github.com/adamnfish/devenv/releases) on GitHub
    - Review the draft release
    - (upload a locally-built version of the binary, until we sign macOS CI builds properly)
    - Test the binaries if needed
    - Click "Publish release" when ready

**Version management:**

- Releases use date-based versioning: `YYYYMMDD-HHMMSS` (e.g., `20251103-143022`)
- All releases are marked as prereleases to indicate development status

**Note:** The release workflow is triggered manually - it will not run automatically on pushes or merges to give full control over when to "cut" a release.

Users can install the release binary with the following command (replace `<latest-release-version>` with your actual version string):

```bash
VERSION=<latest-release-version>
curl -L -o ~/.local/bin/devenv https://github.com/adamnfish/devenv/releases/download/$VERSION/devenv-$VERSION-macos-arm64
chmod +x ~/.local/bin/devenv
```
