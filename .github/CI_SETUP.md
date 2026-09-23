# CI Quark Validation Setup

## Overview

This repository includes automated validation testing for the sc-clip SuperCollider quark. The tests run on every branch push and pull request to ensure the quark structure is valid.

## Files Added

### 1. `.github/workflows/test-compile.yml`
GitHub Actions workflow that validates:
- Quark metadata file (`sc-clip.quark`) exists
- `Classes/` directory exists
- All `.sc` files in `Classes/` contain class definitions
- Runs on every branch push and pull request

### 2. `scripts/test-compile.scd`
A comprehensive SuperCollider script for manual testing that verifies all sc-clip classes by name:
- Core classes (SCClip, ClipGrid, ClipChannel, ClipSlot, ClipTransport)
- Audio classes (ClipMasterBus, ClipSynthDefs)
- Controller classes (ClipGridController, ClipMIDIController, ClipMIDIClock, etc.)
- Dependencies (MixerChannel)

**Note**: This script requires a working SuperCollider installation with dependencies and is intended for local testing, not CI.

### 3. `scripts/test-syntax.scd`
Simple syntax validation script that attempts to start sclang and reports if the class library loaded.

## Current CI Approach

The GitHub Action performs **structural validation** only:
- ✅ Validates quark file structure
- ✅ Checks for class files
- ✅ Verifies class definitions exist
- ❌ Does NOT compile SuperCollider code

### Why Not Full Compilation Testing?

SuperCollider Docker images and package repositories presented several challenges:
- `ghcr.io/capital-G/sc-docker:latest` - Access denied (private/removed)
- `rukano/supercollider` - Too old (incompatible with GitHub Actions Node.js)
- Ubuntu PPAs - Don't support newer Ubuntu versions (22.04+, 24.04)
- Ubuntu default package - Older SC version with compatibility issues

Future work could add full compilation testing once a reliable SuperCollider Docker image is available.

## Running Tests Locally

### Automated Structure Validation (matches CI)
```bash
# Validate quark structure
./.github/workflows/test-compile.yml  # (extract the validation steps)
```

### Manual Compilation Test (requires SuperCollider)
```bash
# Install the quark and dependencies
make install

# Run full compilation test
QT_QPA_PLATFORM=offscreen sclang -D scripts/test-compile.scd
```

## Docker Images Investigated

All investigated for potential CI use:

- **capital-G/sc-docker** ([GitHub](https://github.com/capital-G/sc-docker/))
  - Status: GHCR package not publicly accessible
  - Would be ideal for CI/CD if available

- **rukano/supercollider** ([Docker Hub](https://hub.docker.com/r/rukano/supercollider))
  - Status: Incompatible with GitHub Actions (GLIBC version too old)
  - Last updated: ~8 years ago

- **orbsmiv/supercollider-rpi** ([Docker Hub](https://hub.docker.com/r/orbsmiv/supercollider-rpi/))
  - Status: Raspberry Pi specific, not suitable for x86_64 CI

- **SuperCollider PPA** (ppa:supercollider/ppa)
  - Status: No packages for Ubuntu 22.04+ (Jammy, Noble)
  - Last supported: Ubuntu 20.04 (Focal)

## Future Improvements

To add full SuperCollider compilation testing to CI:

1. **Create custom Docker image** in separate repository with:
   - Recent Ubuntu base
   - SuperCollider built from source (latest stable)
   - Minimal dependencies for headless operation

2. **Use GitHub Container Registry** to host the custom image

3. **Update workflow** to use custom image for full compilation tests

## References

- [capital-G/sc-docker GitHub](https://github.com/capital-G/sc-docker/)
- [rukano/dockerfiles GitHub](https://github.com/rukano/dockerfiles)
- [SuperCollider Headless with Docker](https://gist.github.com/samdoshi/4835d64e4e8254faeb7fe6df3e94ab49)
