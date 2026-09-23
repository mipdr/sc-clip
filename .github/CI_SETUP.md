# CI Compilation Test Setup

## Overview

This repository now includes automated compilation testing for the sc-clip SuperCollider quark. The tests run on every branch push and pull request to ensure code changes don't break compilation.

## Files Added

### 1. `scripts/test-compile.scd`
A SuperCollider script that verifies all sc-clip classes compile successfully:
- Tests all core classes (SCClip, ClipGrid, ClipChannel, etc.)
- Tests audio classes (ClipMasterBus, ClipSynthDefs)
- Tests controller classes (ClipMIDIController, ClipLaunchpadMini, etc.)
- Tests dependencies (MixerChannel)
- Exits with status 0 on success, non-zero on failure

### 2. `.github/workflows/test-compile.yml`
GitHub Actions workflow that:
- Triggers on all branch pushes and pull requests
- Runs in a Docker container with SuperCollider pre-installed
- Installs dependencies using existing `scripts/install-deps.scd`
- Runs the compilation test
- Reports success/failure

## Docker Image Used

**`ghcr.io/capital-g/sc-docker:latest`**

This is an unofficial but actively maintained Docker image for SuperCollider that:
- Includes both sclang (language) and scsynth (audio server)
- Supports headless operation (no GUI required)
- Is designed for CI/CD pipelines
- Repository: https://github.com/capital-G/sc-docker

**No custom Docker image needed** - the existing image works perfectly for compilation testing!

## Alternative Docker Images Considered

- `rukano/supercollider` - Older image (8+ years), less maintained
- `orbsmiv/docker-SuperCollider-rpi` - Raspberry Pi specific
- Custom image - Not necessary given capital-G/sc-docker meets our needs

## Running Tests Locally

To run the compilation test locally (requires SuperCollider installed):

```bash
# Install the quark
make install

# Run compilation test
QT_QPA_PLATFORM=offscreen sclang -D scripts/test-compile.scd
```

## Environment Variables

- `QT_QPA_PLATFORM=offscreen` - Runs sclang headless without X11/display
- `-D` flag - Runs sclang in daemon mode (non-interactive)

## References

- [capital-G/sc-docker GitHub](https://github.com/capital-G/sc-docker/)
- [SuperCollider Headless Documentation](https://gist.github.com/samdoshi/4835d64e4e8254faeb7fe6df3e94ab49)
