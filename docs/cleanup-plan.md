# DevContainer Cleanup for Security

## Problem Statement

A compromised AI agent or supply chain attack may leave the development environment in a poisoned state. After completing work, developers need to confidently dispose of the entire environment and start fresh from a known-good state.

## Security Context

**Threat model:**
- Malicious code execution within devcontainer (via agent, dependency, etc.)
- Attacker may attempt persistence across container rebuilds
- Need to prevent compromise from spreading to host or future containers

**Persistence mechanisms to address:**
- Running containers
- Docker volumes (mise data, custom mounts)
- Cached images (could contain backdoors)
- Bind mounts to host filesystem
- Docker networks

## Proposed Solution: `devenv clean`

### Command Design

```bash
# Interactive mode (default) - shows what will be removed, asks for confirmation
devenv clean

# Non-interactive mode - removes everything without prompting
devenv clean --force

# Dry run - show what would be removed
devenv clean --dry-run
```

### What Gets Removed

**Level 1: Container and volumes (default)**
- Stop and remove devcontainer for current project
- Remove associated Docker volumes (mise data, etc.)
- Leave images intact (faster rebuild)

**Level 2: Deep clean (--deep flag)**
- Everything from Level 1
- Remove project-specific images
- Remove dangling/unused images
- Verify no orphaned resources remain

### Implementation Considerations

**Discovery:**
- Use Docker labels to find project resources (devcontainer sets `devcontainer.local_folder`)
- Parse `.devcontainer/devenv.yaml` to identify module-created volumes
- Enumerate all containers/volumes/networks with project marker

**Verification:**
- After cleanup, verify nothing remains with project labels
- Report any resources that couldn't be removed (permissions, etc.)
- Exit with error if cleanup incomplete

**Safe defaults:**
- Never remove bind mounts (host filesystem safety)
- Warn about bind mounts in use (potential persistence vector)
- Preserve base images by default (ubuntu, etc. - shared across projects)

### Output Design

```bash
$ devenv clean

Cleaning devcontainer environment for: /home/user/my-project

Found:
  ✓ Container: my-project_devcontainer (running)
  ✓ Volume: docker-mise-data-volume (2.3GB)
  ✓ Volume: vscode-server-extensions (450MB)
  ⚠ Bind mount: /home/user/my-project → /workspace (preserved)

Remove container and volumes? [y/N]: y

Removing container... ✓
Removing volumes... ✓

Verification:
  ✓ No containers found with project labels
  ✓ No volumes found with project labels

Clean complete. Environment disposed.

To start fresh:
  1. Close IDE remote connection
  2. devenv generate
  3. Reopen in container
```

### Edge Cases

**Container currently open in IDE:**
- Detect and warn
- Require --force to proceed
- Suggest closing IDE connection first

**Shared volumes:**
- mise volume might be shared across projects
- Provide --preserve-shared flag
- Document tradeoff (convenience vs paranoid cleanup)

**No containers found:**
- Report "nothing to clean"
- Still useful (verifies clean state)

## Security Properties

**Completeness:**
- Removes all container-layer filesystem changes
- Removes volume data (including mise cache)
- No state carries over to next environment

**Verifiability:**
- Exit code 0 = verified clean
- Exit code 1 = cleanup incomplete
- Explicit verification step catches partial cleanup

**Paranoid mode (--deep --no-preserve-shared):**
- Maximum cleanup, slower rebuild
- For high-security workflows

## What This Doesn't Solve

**Compromised base images:**
- If `mcr.microsoft.com/devcontainers/base:ubuntu` is poisoned, cleanup won't help
- Mitigation: Pin image digests in config, verify signatures

**Host filesystem access:**
- Bind mounts can be modified from container
- Mitigation: Minimize bind mounts, document risk

**Docker daemon compromise:**
- If Docker daemon itself is compromised, all bets are off
- Out of scope for devenv

**Trust in devenv binary:**
- If devenv binary is compromised, it could fake cleanup
- Mitigation: Verify devenv binary hash, build from source

## Implementation Plan

1. Add `devenv clean` command to CLI
2. Implement Docker resource discovery via labels
3. Implement removal with verification
4. Add comprehensive output/error handling
5. Write tests (unit + E2E)
6. Document security workflow in README

## Alternative: Documentation-Only Approach

Instead of building cleanup into devenv, document the manual process:

```bash
# In project README or devenv docs
## Secure Environment Disposal

cd /path/to/project
docker ps -a --filter "label=devcontainer.local_folder=$(pwd)" -q | xargs docker rm -f
docker volume ls --filter "label=devcontainer.local_folder=$(pwd)" -q | xargs docker volume rm
docker system prune -f

# Verify clean
docker ps -a --filter "label=devcontainer.local_folder=$(pwd)"
# Should return nothing
```

**Pros:** No code changes, works today
**Cons:** Error-prone, no verification, easy to skip steps

## Recommendation

Implement `devenv clean` with:
- Conservative defaults (container + volumes)
- Verification step
- Clear security-focused messaging

This aligns with devenv's goal of making secure workflows easy. The command provides confidence that environment disposal is complete.
