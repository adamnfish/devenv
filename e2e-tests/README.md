# devenv End-to-End Tests

This project contains end-to-end tests for the devenv tool's modules. These tests:

1. Generate devcontainer.json files using the `core` library directly (not via CLI)
2. Build and start actual devcontainers using the `devcontainer` CLI (via npx)
3. Run module-specific assertions inside the containers to verify they work correctly

## Prerequisites

- Docker must be running
- Node.js (for npx to run @devcontainers/cli - no global installation needed)
- sbt (for building and running tests)

The tests use `npx --yes @devcontainers/cli` to run the devcontainer CLI, which automatically downloads and caches the package on first use.

## Architecture

These tests call the `Devenv.generate()` function from the `core` library directly, rather than shelling out to the CLI binary. This means:

- Tests always run against the current source code
- No need to stage the CLI binary first
- Faster test execution (no process spawning for generation)
- We're testing the *modules work in real containers*, not CLI argument parsing

## Running the Tests

### Run all e2e tests

```bash
sbt "endToEndTests/test"
```


### Run tests for a specific module

```bash
# Test only the mise module
sbt "endToEndTests/testOnly *MiseModuleSpec"

# Test only docker-in-docker
sbt "endToEndTests/testOnly *DockerInDockerModuleSpec"

# Test only apt-updates
sbt "endToEndTests/testOnly *AptUpdatesModuleSpec"
```

### Skip slow container tests

If you want to run only non-container tests (though currently all tests are container tests):

```bash
sbt "endToEndTests/testOnly -- -l com.gu.devenv.e2e.ContainerTest"
```

### Run only container tests

```bash
sbt "endToEndTests/testOnly -- -n com.gu.devenv.e2e.ContainerTest"
```

## Test Structure

### Fixtures

Test fixtures are located in `src/test/resources/fixtures/`. Each fixture is a minimal workspace with:
- `.devcontainer/devenv.yaml` - The devenv configuration
- Any additional files needed for that module (e.g., `.mise.toml`, `docker-compose.yaml`)

### Module Assertions

Each module has a corresponding `*Assertion` object in `ModuleAssertions.scala` that defines the verification logic. These assertions run commands inside the container to verify the module's functionality.

## Adding New Module Tests

1. Create a fixture in `src/test/resources/fixtures/<module-name>/`
2. Add the `devenv.yaml` with the module enabled
3. Add any supporting files needed
4. Create an assertion in `ModuleAssertions.scala`
5. Create a test spec in `<ModuleName>ModuleSpec.scala`

## Notes

- Tests run sequentially (not in parallel) because each test starts a container
- Tests are tagged with `ContainerTest` for filtering
- The `cli/stage` target is run automatically before tests to ensure the binary is up-to-date
- Containers and temporary workspaces are cleaned up after each test

