EXT_DIR := $(HOME)/.local/share/SuperCollider/Extensions
PROJECT_DIR := $(CURDIR)

.PHONY: install start

# Symlinks this repo into SuperCollider's Extensions dir (so local edits are
# picked up live, instead of a Quarks.install copy), installs the
# ddwMixerChannel dependency, and verifies SCClip resolves after a fresh
# class-library compile. This step needs no GUI, so DISPLAY is unset and
# QT_QPA_PLATFORM=offscreen forced -- sclang is a Qt-GUI build even in -D
# (daemon/no-input) mode, and will otherwise hang trying to reach whatever
# X session DISPLAY happens to point at (e.g. a locked desktop session).
SC_HEADLESS_ENV := env -u DISPLAY QT_QPA_PLATFORM=offscreen

install:
	mkdir -p $(EXT_DIR)
	ln -sfn $(PROJECT_DIR) $(EXT_DIR)/sc-clip
	@echo "Symlinked $(PROJECT_DIR) -> $(EXT_DIR)/sc-clip"
	$(SC_HEADLESS_ENV) timeout 120 sclang -D scripts/install-deps.scd
	$(SC_HEADLESS_ENV) timeout 60 sclang -D scripts/verify-install.scd

# Generic, hardware-agnostic sclang session for interactively testing SCClip
# itself -- default server options, no JACK/UMC404 specifics. Real rig
# integration lives in ~/music (see `make start-clip` there).
start:
	sclang scripts/dev-boot.scd
