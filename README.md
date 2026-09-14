# SC-Clip

**SuperCollider Hardware Clip-Launcher / Looper Framework**

A live performance system for sampling, looping, and launching audio from external hardware synthesizers, controlled via MIDI grid controllers (Launchpad-style).

## What It Does

- **Record loops** from hardware synths routed into your audio interface
- **Launch clips** in perfect sync with a beat-quantized clock
- **Control everything** via MIDI grid controller (Launchpad or similar)
- **Mix and process** with per-channel effects and master bus mastering
- **Stay in time** with MIDI clock or Ableton Link sync

This is **not a synthesizer** — it's a performance looper for hardware. Think Ableton Live's Session View, but for SuperCollider, optimized for external gear.

## Status

🚧 **In Development** — See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for detailed architecture.

## Features

### Core Functionality
- ✅ Multi-channel clip grid (8 channels × 8 slots default)
- ✅ Record, overdub, play, stop loops from hardware
- ✅ Beat-quantized launch/stop (no timing slop)
- ✅ MIDI grid controller support (Launchpad + generic grids)
- ✅ LED feedback showing clip states

### Signal Processing
- ✅ Per-channel effects chains (via ddwMixerChannel quark)
- ✅ Master bus with mastering chain (EQ, compression, limiting)
- ✅ Clean signal routing: hardware in → loops → FX → master → out

### Synchronization
- ✅ Internal TempoClock (SC as master)
- ✅ Ableton Link support (sync with Live, other apps)
- ✅ MIDI clock output (sync hardware sequencers)

## Requirements

- **SuperCollider** 3.13 or later
- **ddwMixerChannel** quark (for mixing/routing)
- **Audio interface** with multiple inputs for hardware synths
- **MIDI controller** (Launchpad or 8×8 grid recommended)

## Installation

```supercollider
// 1. Install dependencies
Quarks.install("ddwMixerChannel");

// 2. Install sc-clip
Quarks.install("https://github.com/[username]/sc-clip");

// 3. Recompile class library
thisProcess.recompile;
```

For detailed setup (audio routing, MIDI configuration, latency tuning), see [SETUP.md](SETUP.md).

## Quick Start

```supercollider
// Boot server with recommended settings
(
s.options.memSize = 8192 * 16;        // 128 MB for loops
s.options.numBuffers = 2048;
s.options.blockSize = 128;            // ~3ms latency @ 48kHz
s.options.numInputBusChannels = 8;    // 8 hardware inputs
s.options.numOutputBusChannels = 2;   // Stereo out
s.boot;
)

// Initialize sc-clip with 8 channels, 8×8 grid
~clip = SCClip.new(numChannels: 8, gridRows: 8, gridCols: 8);

// Set tempo
~clip.transport.setTempo(120); // BPM

// Connect MIDI controller (Launchpad example)
~clip.setupMIDI(\launchpad, "Launchpad Mini");

// Now use your controller:
// - Press grid button: launch clip (quantized to bar)
// - Hold Shift + Press: arm for recording
// - Press again while playing: stop clip

// Or control programmatically:
~clip.grid.armSlot(channel: 0, slot: 0);      // Arm channel 0, slot 0
~clip.grid.launchSlot(0, 0);                   // Start recording (quantized)
// Play your hardware synth...
~clip.grid.stopSlot(0, 0);                     // Stop recording, loop plays back

// Add per-channel effects
~clip.grid.channels[0].addEffect(\reverb, [\room: 0.5, \mix: 0.3]);

// Adjust master level
~clip.masterBus.setMasterLevel(-6); // dB
```

## Architecture Overview

```
Hardware Synth → Audio Interface Input
                        ↓
                 [Input Group]
                        ↓
                [Recorder/Looper]
                 RecordBuf/PlayBuf
                        ↓
            [Per-Channel FX Group]
            ddwMixerChannel + effects
                        ↓
                [Master Group]
            EQ → Compressor → Limiter
                        ↓
           Audio Interface Output
```

**Modules:**
- `SCClip` — Main class, coordinates everything
- `ClipGrid` — Manages clip matrix, routes MIDI to slots
- `ClipChannel` — Per-input-track wrapper (uses MixerChannel)
- `ClipSlot` — Individual loop/clip (state machine, buffers, synths)
- `ClipTransport` — TempoClock, beat quantization, sync
- `ClipMIDIController` — MIDI input handling, LED feedback
- `ClipMasterBus` — Master bus with mastering chain

See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for full details.

## Design Philosophy

### Timing Over Latency
- **Quantized events** (clip launch/stop) use 200ms server latency for sample-accurate timing
- **Immediate events** (knob tweaks) use 0ms latency for instant response
- Loop lengths computed **once in samples** at record time (no drift over long sets)

### Simplicity Over Features
- No in-SC synthesis (use your hardware!)
- No GUI (use your MIDI controller!)
- No session save/load (phase 2 maybe)
- Focus: rock-solid looping and timing

### Leverage Existing Tools
- `ddwMixerChannel` for routing/mixing (don't reinvent buses)
- `launchpadmini-sc` for LED protocols (don't write SysEx by hand)
- Built-in `TempoClock`, `LinkClock` for sync

## Performance Targets

- **Latency:** ~3-5ms for immediate events, 200ms for quantized (sample-accurate)
- **CPU:** <50% with 8 channels, 16 playing clips, full effects
- **Stability:** No dropouts in 30+ minute sets

## Examples

See the `Examples/` directory:
- `01_basic_setup.scd` — Manual control (no MIDI)
- `02_launchpad_grid.scd` — Full Launchpad setup
- `03_effects_chains.scd` — Per-channel FX demos
- `04_link_sync.scd` — Ableton Link integration
- `05_midi_clock.scd` — MIDI clock sync

## Documentation

- [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — Complete architecture and design decisions
- [SETUP.md](SETUP.md) — Installation, audio routing, MIDI setup
- `HelpSource/` — SC help files for each class

## Contributing

This is a performance instrument under active development. Contributions welcome, especially:
- Additional controller mappings (APC40, Push, etc.)
- Effects chain presets
- Performance optimizations
- Bug reports from live use

## License

TBD

## Credits

Built with:
- [SuperCollider](https://supercollider.github.io/)
- [ddwMixerChannel](https://github.com/supercollider-quarks/ddwMixerChannel) by dewdrop_world
- Inspired by Ableton Live, Octatrack, and hardware loopers
