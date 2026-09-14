# SC-Clip Setup Guide

This guide walks you through installing and configuring SC-Clip for your hardware setup.

## Prerequisites

- **SuperCollider 3.13 or later** — [Download here](https://supercollider.github.io/downloads)
- **Audio interface** with multiple inputs for hardware synths
- **Hardware synthesizers** routed to your audio interface
- **(Optional) MIDI grid controller** for hands-on control

## Installation

### 1. Install SuperCollider

Download and install SuperCollider 3.13+ from [supercollider.github.io](https://supercollider.github.io/downloads).

**Verify installation:**
```supercollider
Server.default.boot;
// Should boot without errors
Server.default.quit;
```

### 2. Install Required Quarks

SC-Clip depends on the `ddwMixerChannel` quark for mixing and routing.

```supercollider
// Install ddwMixerChannel
Quarks.install("ddwMixerChannel");

// Recompile class library
thisProcess.recompile;
```

**Verify installation:**
```supercollider
MixerChannel;  // Should return: MixerChannel
```

### 3. Install SC-Clip

**Option A: Install from GitHub (recommended)**
```supercollider
Quarks.install("https://github.com/mipdr/sc-clip");
thisProcess.recompile;
```

**Option B: Manual installation**
```bash
# Clone to your SuperCollider extensions directory
cd ~/Library/Application\ Support/SuperCollider/Extensions  # macOS
# or
cd ~/.local/share/SuperCollider/Extensions                   # Linux
# or
cd %USERPROFILE%\AppData\Local\SuperCollider\Extensions      # Windows

git clone https://github.com/mipdr/sc-clip.git
```

Then recompile in SuperCollider:
```supercollider
thisProcess.recompile;
```

**Verify installation:**
```supercollider
SCClip;  // Should return: SCClip
```

## Audio Interface Setup

### Hardware Routing

Connect your hardware synthesizers to your audio interface inputs.

**Example setup (4 synths):**
```
Synth 1 → Interface Input 0 (mono)
Synth 2 → Interface Input 1 (mono)
Synth 3 → Interface Inputs 2-3 (stereo)
Synth 4 → Interface Input 4 (mono)
```

### Configure Input Channels

Set the number of input channels in your server options to match your interface:

```supercollider
(
s.options.numInputBusChannels = 8;    // 8 hardware inputs
s.options.numOutputBusChannels = 2;   // Stereo out
s.boot;
)
```

**Note:** If using stereo synths, you'll need two SC-Clip channels per stereo synth, or modify the code to support stereo clips (current version is mono per channel).

## Server Configuration

### Recommended Settings

```supercollider
(
// Memory for loop buffers (128 MB)
s.options.memSize = 8192 * 16;

// Number of buffers (one per clip + overhead)
s.options.numBuffers = 2048;

// Audio block size (affects latency)
s.options.blockSize = 128;   // ~3ms @ 48kHz

// Sample rate
s.options.sampleRate = 48000;

// Input/output channels
s.options.numInputBusChannels = 8;
s.options.numOutputBusChannels = 2;

s.boot;
)
```

### Platform-Specific Optimization

#### macOS (CoreAudio)
```supercollider
s.options.device = "Your Interface Name";  // e.g. "Focusrite USB"
s.options.blockSize = 128;  // 128-256 typical
```

#### Windows (ASIO)
```supercollider
// Use ASIO driver for low latency
s.options.device = "ASIO :: Your Interface";
s.options.blockSize = 256;  // 256-512 typical (Windows has higher latency)
```

#### Linux (JACK)
```supercollider
// SC uses JACK on Linux
// Start JACK first with low latency settings:
// jackd -d alsa -r 48000 -p 128

s.options.blockSize = 128;  // Match JACK buffer size
```

**For lowest latency on Linux:**
- Use a real-time kernel (e.g., `linux-rt`)
- Run scsynth with RT priority: `-R` flag
- Configure JACK with real-time priority

## Latency Configuration

SC-Clip uses a split latency strategy:

### Quantized Events (Clip Launch/Stop)
- Uses `server.latency` (default 0.2s / 200ms)
- Sample-accurate timing at beat boundaries
- 200ms perceived delay acceptable since clips are quantized to bars

### Immediate Events (Knob Tweaks)
- Uses `latency: 0`
- Instant response
- May not align perfectly to beat grid

**Adjust server latency:**
```supercollider
s.latency = 0.15;  // Lower for faster perceived response (less accurate)
s.latency = 0.3;   // Higher for more sample-accurate timing (feels delayed)
```

**Audio buffer latency floor:**
- Block size 64 @ 48kHz = ~1.3ms
- Block size 128 @ 48kHz = ~2.7ms
- Block size 256 @ 48kHz = ~5.3ms

Total latency = audio buffer + `server.latency` + OS/driver overhead

## Memory Requirements

### Buffer Memory Calculation

Each clip uses:
```
Loop length in seconds × sample rate × 4 bytes per sample = memory per clip
```

**Example:** 4-bar loop @ 120 BPM, 48kHz
- Loop length: 4 beats × 60s / 120 BPM = 8 seconds
- Memory: 8s × 48000 × 4 bytes = 1.536 MB per clip

**For 4 channels × 8 slots = 32 clips:**
- 32 clips × 1.5 MB = ~48 MB

**Recommended:** Set `memSize = 8192 * 16` (128 MB) for headroom.

### Increase Memory If Needed

```supercollider
s.options.memSize = 8192 * 32;  // 256 MB (for long loops or many channels)
s.reboot;
```

## MIDI Controller Setup (Optional)

SC-Clip architecture supports MIDI controllers but no controller classes are implemented yet in Phase 1.

### Future: Launchpad Setup

When `ClipMIDIController` and `ClipLaunchpad` are implemented:

```supercollider
// Initialize MIDI
MIDIClient.init;
MIDIIn.connectAll;

// List available devices
MIDIClient.sources;

// Connect Launchpad
~clip.setupMIDI(\launchpad, "Launchpad Mini");
```

### Manual MIDI Control (Current Workaround)

You can manually map MIDI to clip functions:

```supercollider
(
MIDIClient.init;
MIDIIn.connectAll;

// Map MIDI note to clip launch
MIDIFunc.noteOn({ |vel, note|
	var channel = note div 8;
	var slot = note mod 8;
	~clip.launchSlot(channel, slot);
}, noteRange: (0..63));  // 8×8 grid

// Map MIDI CC to master level
MIDIFunc.cc({ |val|
	var db = val.linlin(0, 127, -60, 0);
	~clip.setMasterLevel(db);
}, ccNum: 7);  // CC 7 = volume
)
```

## First Run

### Quick Test

```supercollider
(
// Configure and boot server
s.options.memSize = 8192 * 16;
s.options.numInputBusChannels = 4;
s.options.numOutputBusChannels = 2;
s.waitForBoot {
	// Create SC-Clip instance
	~clip = SCClip.new(numChannels: 4, numSlots: 8);
	~clip.boot({
		"SC-Clip ready!".postln;
		~clip.printStatus;
	});
};
)
```

### Test Recording

```supercollider
// Set tempo
~clip.setTempo(120);

// Arm a slot for 4-beat recording
~clip.armSlot(channelIndex: 0, slotIndex: 0, loopLengthBeats: 4);

// Launch (will record on next bar)
~clip.launchSlot(0, 0);

// Play your hardware synth for 4 beats!
// Recording will auto-transition to playback

// Check status
~clip.printStatus;

// Stop
~clip.stopSlot(0, 0);
```

## Troubleshooting

### "ERROR: Class not defined: MixerChannel"
- Install ddwMixerChannel quark: `Quarks.install("ddwMixerChannel")`
- Recompile: `thisProcess.recompile`

### "ERROR: audio rate inputs only"
- Make sure server is booted before creating SC-Clip instance
- Use `~clip.boot({ ... })` with a callback

### No audio from hardware inputs
- Check audio interface routing in your OS
- Verify `s.options.numInputBusChannels` is >= number of synths
- Test with `{ SoundIn.ar(0) }.play;` to verify input 0 works

### Clips not quantizing correctly
- Check tempo: `~clip.transport.tempo`
- Check quantization: `~clip.transport.quantization`
- Verify time signature: `~clip.transport.timeSignature`

### Buffer allocation errors
- Increase memory: `s.options.memSize = 8192 * 32; s.reboot;`
- Check available memory: `s.options.memSize.postln;`

### Timing drift over long sessions
- This shouldn't happen! Loop lengths are calculated in samples once.
- If it does happen, please file a bug report.

### High CPU usage
- Lower block size increases CPU (try 256 or 512)
- Reduce number of playing clips
- Remove/disable master effects if not needed

## Performance Tips

1. **Use shorter loops for rhythmic parts, longer for textures**
   - 1-4 bars: drums, bass, melodic phrases
   - 8-16 bars: pads, ambient layers

2. **Arm multiple clips before recording**
   - Reduces buffer allocation latency during performance

3. **Monitor server CPU**
   ```supercollider
   s.avgCPU;  // Current CPU usage
   s.peakCPU; // Peak CPU usage
   ```

4. **Use scenes for song structure**
   - Arm slots at the same index across channels
   - Launch with `~clip.launchScene(slotIndex)`

5. **Overdub for building layers**
   - Record basic part, then overdub variations
   - Keep overdubbing to add complexity

## Next Steps

- Read [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for architecture details
- See [Examples/01_basic_setup.scd](Examples/01_basic_setup.scd) for usage examples
- Explore per-channel effects (via ddwMixerChannel)
- Experiment with sync options (Link, MIDI clock)

## Getting Help

- SuperCollider documentation: [doc.sccode.org](https://doc.sccode.org)
- SC-Clip GitHub issues: [github.com/mipdr/sc-clip/issues](https://github.com/mipdr/sc-clip/issues)
- SuperCollider forum: [scsynth.org](https://scsynth.org)
