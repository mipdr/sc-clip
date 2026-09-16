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

### Command-Line Testing (No MIDI Controller)

Perfect for initial testing and development:

```supercollider
// 1. Boot server and initialize
(
s.options.memSize = 8192 * 16;
s.options.numInputBusChannels = 4;
s.options.numOutputBusChannels = 2;
s.waitForBoot {
	~clip = SCClip.new(numChannels: 4, numSlots: 8);
	~clip.boot({
		"SC-Clip ready!".postln;
		~clip.enableMetronome(0.3);  // Enable click track
	});
};
)

// 2. Set tempo and time signature
~clip.setTempo(120);              // 120 BPM
~clip.setTimeSignature(4, 4);     // 4/4 time

// 3. Create test tone (simulates hardware synth)
(
~testTone = {
	var freq = LFNoise1.kr(0.5).range(200, 800);
	SinOsc.ar(freq) * 0.3;
}.play;
)

// 4. Record a loop (listens to metronome for timing!)
~clip.armSlot(0, 0, 4);           // Arm channel 0, slot 0, 4 beats
~clip.launchSlot(0, 0);           // Start recording on next bar
// Wait 4 beats... loop will auto-play

// 5. Control playback
~clip.stopSlot(0, 0);             // Stop
~clip.launchSlot(0, 0);           // Play again
~clip.clearSlot(0, 0);            // Delete

// 6. Metronome controls
~clip.enableMetronome(0.3);       // Enable (downbeat = high pitch)
~clip.disableMetronome;           // Disable
~clip.setMetronomeVolume(0.5);    // Adjust volume

// 7. Mixing
~clip.setChannelLevel(0, -6);     // Channel level (dB)
~clip.setMasterLevel(-3);         // Master level (dB)

// See Examples/00_command_line_test.scd for comprehensive guide!
```

### With MIDI Controller (Future)

```supercollider
// MIDI controller support coming in Phase 3
// For now, use command-line methods above
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
- `00_command_line_test.scd` — **Start here!** Complete command-line testing guide with metronome
- `01_basic_setup.scd` — Comprehensive usage examples (recording, overdub, mixing, effects)
- `02_launchpad_grid.scd` — Full Launchpad setup (coming in Phase 3)
- `03_effects_chains.scd` — Per-channel FX demos (coming soon)
- `04_link_sync.scd` — Ableton Link integration (coming soon)
- `05_midi_clock.scd` — MIDI clock sync (coming soon)

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
