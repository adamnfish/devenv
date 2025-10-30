# Generate with User Config Test

Tests the `devenv generate` command with user-specific configuration.

## Setup:
- `project/.devcontainer/devenv.yaml` - Project config with basic plugins
- `mock-home/.config/devenv/devenv.yaml` - User config with personal plugins and dotfiles (for reference/future use)

## Current Limitations:
The full user config merging cannot be tested in E2E without modifications because:
- Java's `System.getProperty("user.home")` doesn't respect the HOME environment variable
- The binary hardcodes use of `user.home` system property
- Would need code changes or to write to actual user's ~/.config/devenv/

## Current test coverage:
- Verifies `devenv generate` runs successfully
- Verifies both user/ and shared/ devcontainer.json files are created
- Verifies project config is present in generated files
- Verifies generated JSON is valid

## Future improvements:
To fully test user config merging in E2E, could:
1. Modify Main.scala to check HOME env var first, then fall back to user.home property
2. Pass `-Duser.home=/path/to/mock-home` as JVM option
3. Write actual test config to ~/.config/devenv/ and clean up after
