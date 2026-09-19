# SC-Clip Implementation Plan

## Project Overview
SuperCollider-based hardware clip-launcher/looper framework for live performance with external hardware synths, controlled via MIDI grid controllers.

## Architecture Overview

### Core Modules

```
┌─────────────────────────────────────────────────────────────┐
│                      SCClip (Main Class)                     │
│  - Initialize system, coordinate all components              │
│  - Manage audio server lifecycle                             │
└────────────┬────────────────────────────────────────────────┘
             │
    ┌────────┴────────┬──────────────┬────────────────┐
    │                 │              │                │
┌───▼────┐    ┌──────▼──────┐  ┌───▼─────┐  ┌──────▼──────┐
│ClipGrid│    │ClipTransport│  │ClipMIDI │  │ClipMaster   │
│        │    │             │  │Controller│  │Bus          │
└───┬────┘    └─────────────┘  └──────────┘  └─────────────┘
    │
┌───▼──────────┐
│ClipChannel   │  (per input track)
│- ClipSlot[]  │
│- MixerChannel│
│- FX Chain    │
└──────┬───────┘
       │
┌──────▼──────┐
│ClipSlot     │  (individual loop/clip)
│- RecordBuf  │
│- PlayBuf    │
│- Buffer     │
│- State      │
└─────────────┘
```

### Signal Flow

```
Hardware Synth → Audio Interface Input
                        ↓
                 [Input Group]
                 SoundIn nodes
                        ↓
                [Recorder Group]
                RecordBuf synths (one per armed slot)
                        ↓
                [Looper Group]
                PlayBuf synths (one per playing slot)
                        ↓
            [Per-Channel FX Group]
            MixerChannel instances with effects
                        ↓
                [Master Group]
            Master MixerChannel + mastering chain
            (limiter, EQ, glue compression)
                        ↓
           Audio Interface Output
```

## Module Specifications

### 1. SCClip (Main Class)
**File:** `Classes/SCClip.sc`

**Responsibilities:**
- Boot and configure audio server
- Initialize all subsystems (grid, transport, MIDI, master bus)
- Provide high-level API for setup and control
- Manage global state and cleanup

**Key Methods:**
```supercollider
*new(numChannels: 8, gridRows: 8, gridCols: 8)
boot(serverOptions)
shutdown()
setupMIDI(controllerType: \launchpad)
setupSync(type: \internal) // or \link, \midiclock
```

**Configuration Defaults:**
- 8 input channels (configurable)
- 8x8 clip grid (64 slots per channel)
- Server latency: 0.2s for quantized events, 0.0s for immediate
- Audio buffer size: recommend 128 samples (documented)

---

### 2. ClipGrid
**File:** `Classes/ClipGrid.sc`

**Responsibilities:**
- Manage 2D matrix of clip slots across channels
- Route MIDI grid presses to appropriate ClipSlot
- Coordinate LED feedback to controller
- Handle clip launch modes (one-shot, loop, legato)

**Data Structure:**
```supercollider
channels: Array[ClipChannel]  // one per input track
rows: Int (8 default)
cols: Int (8 default)
```

**Key Methods:**
```supercollider
*new(numChannels, rows, cols, server)
getSlot(channel, row, col) -> ClipSlot
armSlot(channel, slot)
launchSlot(channel, slot, quant)
stopSlot(channel, slot, quant)
stopChannel(channel, quant)
stopAll(quant)
```

---

### 3. ClipChannel
**File:** `Classes/ClipChannel.sc`

**Responsibilities:**
- Wrap one MixerChannel instance (from ddwMixerChannel quark)
- Manage array of ClipSlot instances for this channel
- Route audio: hardware input → slots → mixer channel → master
- Handle per-channel effects chain

**Data Structure:**
```supercollider
channelIndex: Int
inputBus: Bus (from hardware)
mixerChannel: MixerChannel (ddwMixerChannel)
slots: Array[ClipSlot]
server: Server
```

**Key Methods:**
```supercollider
*new(index, numSlots, inputBus, masterBus, server)
addEffect(synthDef, args)
removeEffect(index)
setLevel(db)
setPan(position)
solo()
mute()
```

**Signal Routing:**
- Input: `SoundIn.ar(channelIndex)` → private bus
- Slots record/play from/to this bus
- MixerChannel takes bus as input, outputs to master

---

### 4. ClipSlot
**File:** `Classes/ClipSlot.sc`

**Responsibilities:**
- State machine for one clip/loop slot
- Buffer management (allocation, reuse, clearing)
- Recording engine (RecordBuf with overdub/replace modes)
- Playback engine (PlayBuf with looping)
- Beat-quantized launch/stop scheduling

**State Machine:**
```
EMPTY → armed → RECORDING → PLAYING
         ↓                      ↓
         └──────────────────────┘
                  ↓
         QUEUED_TO_STOP → STOPPED → EMPTY
```

States:
- `\empty` - no audio, buffer may not exist
- `\armed` - waiting for quantized record start
- `\recording` - actively recording (RecordBuf running)
- `\playing` - looping playback (PlayBuf running)
- `\overdubbing` - recording + playing simultaneously
- `\queuedToStop` - will stop on next quantization boundary
- `\stopped` - has audio but not playing

**Data Structure:**
```supercollider
state: Symbol
buffer: Buffer (nil if empty)
loopLengthBeats: Float
loopLengthSamples: Int (computed once!)
recorderSynth: Synth
playerSynth: Synth
channel: ClipChannel (parent)
```

**Key Methods:**
```supercollider
*new(channel, slotIndex, server)
arm(loopLengthBeats)
record(atBeat) // internal, called by transport
play(atBeat)
stop(atBeat)
overdub()
clear()
setState(newState)
```

**Buffer Management Strategy:**
- First arm: allocate buffer asynchronously, wait for /done
- Subsequent arms (same slot): reuse existing buffer (no latency)
- Buffer size: `loopLengthBeats * tempo / 60 * sampleRate * numChannels`
- Store `loopLengthSamples` once at record time to avoid drift

**SynthDefs:**
```supercollider
\clipRecorder - RecordBuf with modes: record, overdub, replace
\clipPlayer - PlayBuf with loop flag, rate=1, trigger, gate
```

---

### 5. ClipTransport
**File:** `Classes/ClipTransport.sc`

**Responsibilities:**
- Manage tempo clock (TempoClock, LinkClock, or MIDISyncClock)
- Beat quantization (Quant class)
- Schedule clip launch/stop events on beat boundaries
- MIDI clock output (if enabled)
- Ableton Link integration (if enabled)

**Data Structure:**
```supercollider
clock: TempoClock or LinkClock
tempo: Float (BPM)
beatsPerBar: Int (4 default)
quantization: Quant (1 bar default, configurable)
syncMode: Symbol (\internal, \link, \midiclock)
```

**Key Methods:**
```supercollider
*new(tempo: 120, beatsPerBar: 4)
setTempo(bpm)
setQuantization(beats) // 1, 4, 8, etc.
scheduleQuantized(func, quant)
scheduleImmediate(func)
enableMIDIClock(port)
enableLink()
beat() -> current beat
nextBar() -> next bar boundary beat
```

**Scheduling Strategy:**
- Quantized events: use `clock.schedAbs(beat, func)` + server latency
- Immediate events: send with `latency: 0`
- Bundle timestamping: `server.makeBundle(server.latency, func)`

---

### 6. ClipMIDIController
**File:** `Classes/ClipMIDIController.sc`

**Responsibilities:**
- Initialize MIDI system
- Map MIDI messages to clip grid actions
- Handle different controller types (Launchpad, generic grid)
- Send LED feedback to controller
- Handle modifier keys (shift, alt for secondary functions)

**Supported Controllers (Phase 1):**
- Novation Launchpad (use launchpadmini-sc quark as reference)
- Generic 8x8 grid (configurable note mapping)

**Key Methods:**
```supercollider
*new(grid, transport, controllerType: \launchpad)
connect(deviceName)
disconnect()
setLED(row, col, color)
updateGridLEDs() // sync all LED states
```

**MIDI Mapping:**
```
Grid buttons (note on/off):
  - Single press: launch clip (quantized)
  - Hold + press: arm for recording
  - Double press: stop clip

Top row (scene launch):
  - Stop all clips in channel

Side buttons:
  - Solo, Mute, Arm track

Bottom row:
  - Global transport (play, stop, record)
```

**LED Colors (map to clip states):**
- Off (black): empty slot
- Dim red: armed for recording
- Bright red: recording
- Dim green: stopped (has audio)
- Bright green: playing
- Yellow: overdubbing
- Flashing: queued to stop

**MIDI Callback Strategy:**
```supercollider
// Keep callbacks minimal - read message, dispatch, return
MIDIFunc.noteOn({ |vel, note, chan, src|
    var row = note div 8;
    var col = note mod 8;
    grid.launchSlot(col, row, transport.quantization);
}, noteRange: (0..63));
```

---

### 7. ClipLaunchpad (optional subclass)
**File:** `Classes/ClipLaunchpad.sc`

**Responsibilities:**
- Launchpad-specific LED protocols (SysEx)
- Button mapping for different Launchpad models
- Reference madskjeldgaard's launchpadmini-sc for implementation

**Note:** May delegate to existing Launchpad quark if available, or implement minimal subset for LED control.

---

### 8. ClipMasterBus
**File:** `Classes/ClipMasterBus.sc`

**Responsibilities:**
- Master MixerChannel receiving all channel outputs
- Mastering effects chain (order matters!)
- Final limiter for safety
- Output metering

**Default Mastering Chain:**
```supercollider
1. Master EQ (BPeakEQ for surgical fixes)
2. Glue Compressor (soft knee, low ratio, slow attack/release)
3. Limiter (brick wall at -0.3dB, safety ceiling)
```

**Key Methods:**
```supercollider
*new(numInputChannels, server)
setMasterLevel(db)
addEffect(synthDef, args, position)
bypass(bool)
```

**SynthDefs:**
```supercollider
\masterEQ - parametric EQ chain
\glueComp - bus compressor with makeup gain
\limiter - lookahead limiter
```

---

## SynthDef Specifications

### ClipRecorder
```supercollider
SynthDef(\clipRecorder, { |inBus, bufnum, loop=1, mode=0|
    var sig = In.ar(inBus, 1);
    var recLevel = Select.kr(mode, [1, 1, 0.7]); // record, replace, overdub
    var preLevel = Select.kr(mode, [0, 0, 0.3]);  // overdub preserves previous
    RecordBuf.ar(sig, bufnum,
        recLevel: recLevel,
        preLevel: preLevel,
        loop: loop);
}).add;
```

### ClipPlayer
```supercollider
SynthDef(\clipPlayer, { |outBus, bufnum, rate=1, loop=1, gate=1|
    var sig = PlayBuf.ar(1, bufnum,
        rate: BufRateScale.kr(bufnum) * rate,
        loop: loop,
        doneAction: Done.freeSelf);
    sig = sig * EnvGen.kr(Env.asr(0.01, 1, 0.05), gate, doneAction: Done.freeSelf);
    Out.ar(outBus, sig);
}).add;
```

### Master Effects
```supercollider
SynthDef(\masterEQ, { |inBus, outBus,
    loFreq=80, loGain=0,
    midFreq=1000, midGain=0, midQ=1,
    hiFreq=8000, hiGain=0|
    var sig = In.ar(inBus, 2);
    sig = BPeakEQ.ar(sig, loFreq, 1, loGain);
    sig = BPeakEQ.ar(sig, midFreq, midQ, midGain);
    sig = BPeakEQ.ar(sig, hiFreq, 1, hiGain);
    ReplaceOut.ar(outBus, sig);
}).add;

SynthDef(\glueComp, { |inBus, outBus, thresh= -12, ratio=3, attack=0.01, release=0.3|
    var sig = In.ar(inBus, 2);
    sig = Compander.ar(sig, sig, thresh.dbamp, 1, 1/ratio, attack, release);
    ReplaceOut.ar(outBus, sig);
}).add;

SynthDef(\limiter, { |inBus, outBus, ceiling= -0.3|
    var sig = In.ar(inBus, 2);
    sig = Limiter.ar(sig, ceiling.dbamp, 0.01);
    ReplaceOut.ar(outBus, sig);
}).add;
```

---

## File Structure

```
sc-clip/
├── README.md                           # User-facing documentation
├── IMPLEMENTATION_PLAN.md              # This file
├── SETUP.md                            # Installation and configuration guide
├── sc-clip.quark                       # Quark manifest
├── .gitignore                          # SC-specific ignores
│
├── Classes/                            # Core implementation
│   ├── SCClip.sc                       # Main class
│   ├── ClipGrid.sc                     # Grid management
│   ├── ClipChannel.sc                  # Per-channel wrapper
│   ├── ClipSlot.sc                     # Individual clip/loop
│   ├── ClipTransport.sc                # Timing and quantization
│   ├── ClipMIDIController.sc           # MIDI input handling
│   ├── ClipLaunchpad.sc                # Launchpad-specific controller
│   ├── ClipMasterBus.sc                # Master bus and mastering
│   └── ClipSynthDefs.sc                # All SynthDef definitions
│
├── HelpSource/                         # Documentation
│   ├── Classes/
│   │   ├── SCClip.schelp
│   │   ├── ClipGrid.schelp
│   │   └── ... (one per class)
│   └── Guides/
│       ├── GettingStarted.schelp
│       ├── MIDISetup.schelp
│       ├── TimingAndLatency.schelp
│       └── EffectsChains.schelp
│
├── Examples/                           # Working examples
│   ├── 01_basic_setup.scd              # Minimal working example
│   ├── 02_launchpad_grid.scd           # Full Launchpad setup
│   ├── 03_effects_chains.scd           # Per-channel FX examples
│   ├── 04_link_sync.scd                # Ableton Link integration
│   └── 05_midi_clock.scd               # MIDI clock sync
│
└── Tests/                              # Unit tests
    ├── TestClipSlot.sc                 # State machine tests
    └── TestClipGrid.sc                 # Grid coordination tests
```

---

## Implementation Phases

### Phase 1: Core Infrastructure (Files 1-3)
**Goal:** Working clip grid with basic record/play, no MIDI

**Deliverables:**
1. `SCClip.sc` - main class, server boot, initialization
2. `ClipGrid.sc` - grid data structure
3. `ClipChannel.sc` - channel wrapper with ddwMixerChannel integration
4. `ClipSlot.sc` - state machine, buffer management, record/play
5. `ClipSynthDefs.sc` - recorder and player SynthDefs
6. `01_basic_setup.scd` - example demonstrating manual control

**Test:** Can record and play loops programmatically (no controller yet)

---

### Phase 2: Transport and Quantization (File 4)
**Goal:** Beat-synchronized launch/stop

**Deliverables:**
1. `ClipTransport.sc` - TempoClock wrapper, quantization
2. Update `ClipSlot` to schedule on beat boundaries
3. Update examples to show quantized launches

**Test:** Loops start/stop on bar boundaries, stay in sync

---

### Phase 3: MIDI Controller (Files 5-6)
**Goal:** Hardware control via grid controller

**Deliverables:**
1. `ClipMIDIController.sc` - generic MIDI mapping
2. `ClipLaunchpad.sc` - Launchpad-specific implementation
3. `02_launchpad_grid.scd` - full hardware control example
4. LED feedback working (colors match clip states)

**Test:** Can arm, record, play, stop clips from Launchpad

---

### Phase 4: Master Bus and Effects (File 7)
**Goal:** Complete signal chain with mastering

**Deliverables:**
1. `ClipMasterBus.sc` - master channel with mastering chain
2. Master effects SynthDefs (EQ, compressor, limiter)
3. Per-channel effects API (use ddwMixerChannel's existing support)
4. `03_effects_chains.scd` - FX examples

**Test:** Audio flows through complete chain, mastering prevents clipping

---

### Phase 5: Advanced Sync (Optional Extensions)
**Goal:** Link and MIDI clock support

**Deliverables:**
1. Update `ClipTransport` with LinkClock support
2. MIDI clock output implementation
3. `04_link_sync.scd` and `05_midi_clock.scd` examples

**Test:** Can sync with Ableton Live or hardware sequencer

---

## Key Design Decisions & Trade-offs

### 1. Latency vs. Timing Accuracy
**Decision:** Use different latency settings by event type
- Quantized events (clip launch/stop): `server.latency` (default 0.2s)
  - **Why:** Sample-accurate timing at beat boundaries
  - **Trade-off:** 200ms perceived delay on button press
- Immediate events (knob tweaks, one-shots): latency 0
  - **Why:** Instant response for feel
  - **Trade-off:** May not align perfectly to beat grid

**Rationale:** For a clip launcher, timing accuracy on bar boundaries matters more than instant response. The 200ms latency is imperceptible when the clip is quantized to start 2-4 beats later anyway.

---

### 2. Loop Length Storage (Samples vs. Beats)
**Decision:** Compute loop length in samples once at record time, store that value

```supercollider
// Do this ONCE when recording starts
loopLengthSamples = (loopLengthBeats * 60 / tempo * sampleRate).asInteger;

// DON'T recalculate every playback cycle
// loopLengthSamples = (loopLengthBeats * 60 / tempo * sampleRate).asInteger; // NO!
```

**Why:** Prevents cumulative drift over long performances. If tempo changes, loops stay locked to original timing rather than warping.

**Trade-off:** Loops won't adapt to tempo changes. This is the correct behavior for a hardware looper (like Ableton's Session View with Warp Off).

---

### 3. Buffer Allocation Strategy
**Decision:** Async allocation on first arm, reuse thereafter

```supercollider
// First arm: allocate buffer (async, wait for /done)
buffer = Buffer.alloc(server, loopLengthSamples, 1, { |buf|
    "Buffer % ready".format(buf.bufnum).postln;
    this.setState(\armed);
});

// Subsequent arms: reuse existing buffer (no latency)
this.setState(\armed); // instant
```

**Why:** Avoid allocation latency during performance. Only allocate once per slot.

**Trade-off:** Buffer memory is pre-allocated and not freed until slot is cleared.

---

### 4. ddwMixerChannel vs. Custom Routing
**Decision:** Use ddwMixerChannel quark for all mixing/routing

**Why:**
- Solves per-channel fader, pan, effects sends
- Handles bus allocation automatically
- Well-tested, maintained quark
- Lets us focus on clip-launching logic

**Trade-off:** Dependency on external quark. Must be installed.

---

### 5. State Machine vs. Event-Driven
**Decision:** Explicit state machine for ClipSlot

**States as symbols:** `\empty`, `\armed`, `\recording`, `\playing`, `\overdubbing`, `\queuedToStop`, `\stopped`

**Why:**
- Makes legal transitions clear
- Easy to debug (just check `slot.state`)
- Maps cleanly to LED colors

**Trade-off:** More verbose than ad-hoc flags, but worth it for clarity.

---

### 6. MIDI Callback Minimal Processing
**Decision:** MIDIFunc callbacks only read message and dispatch to method

```supercollider
// GOOD: minimal callback
MIDIFunc.noteOn({ |vel, note|
    var row = note div 8;
    var col = note mod 8;
    grid.launchSlot(col, row, transport.quantization);
});

// BAD: heavy processing in callback
MIDIFunc.noteOn({ |vel, note|
    // Don't allocate buffers here!
    // Don't run complex logic here!
    // Don't post lots of debug messages here!
});
```

**Why:** Avoid blocking MIDI thread, prevent dropouts

**Trade-off:** None, this is just correct practice.

---

## Configuration Defaults

### Audio Server Settings
```supercollider
s.options.memSize = 8192 * 16;       // 128 MB for buffers
s.options.numBuffers = 2048;          // Lots of clips
s.options.blockSize = 128;            // Balance latency/CPU
s.options.sampleRate = 48000;         // Standard pro audio
s.options.numInputBusChannels = 8;    // 8 hardware inputs
s.options.numOutputBusChannels = 2;   // Stereo out
```

### Grid Defaults
```supercollider
numChannels = 4;     // 4 input tracks (configurable)
gridRows = 8;        // 8 clips per channel
gridCols = 8;        // = 64 slots total per channel
```

### Transport Defaults
```supercollider
tempo = 120;         // BPM
beatsPerBar = 4;     // 4/4 time
quantization = 4;    // Launch on bar boundaries
```

### Latency Settings
```supercollider
server.latency = 0.2;              // Quantized events
immediateLatency = 0.0;            // Interactive events
blockSize = 128 samples;           // = ~2.67ms at 48kHz
```

---

## Dependencies

### Required Quarks
```supercollider
Quarks.install("ddwMixerChannel");
```

### Optional Quarks (for reference, may not install directly)
- `launchpadmini-sc` (madskjeldgaard) - for Launchpad LED protocol reference
- `LaunchPad` (olafklingt) - alternative Launchpad library

### Built-in Classes (no installation needed)
- `TempoClock`, `LinkClock`, `Quant`
- `MIDIFunc`, `MIDIdef`, `MIDIIn`
- `Server`, `Bus`, `Synth`, `SynthDef`
- `Buffer`, `RecordBuf`, `PlayBuf`

---

## Setup Documentation (SETUP.md outline)

### 1. Install SuperCollider
- Download SC 3.13+ from supercollider.github.io
- Recommend using SC 3.13 or later for LinkClock stability

### 2. Install Quarks
```supercollider
Quarks.install("ddwMixerChannel");
// Restart SuperCollider
```

### 3. Install sc-clip
```supercollider
Quarks.install("https://github.com/[username]/sc-clip");
// Or clone manually to Platform.userExtensionDir
```

### 4. Audio Interface Setup
- Set input channels in Server options
- Route hardware synths to interface inputs
- Example: 4 synths → inputs 0-3 (mono) or 0-1, 2-3 (stereo pairs)

### 5. MIDI Controller Setup
- Connect Launchpad or grid controller
- Find device name: `MIDIClient.init; MIDIIn.sources;`
- Configure in sc-clip: `~clip.setupMIDI(\launchpad, "Launchpad Mini");`

### 6. Linux-specific: Low Latency Setup
- Use JACK audio server
- Real-time kernel recommended
- Run scsynth with RT priority: `-R` flag

### 7. Recommended Buffer Sizes
- **macOS:** 128 samples (~3ms at 48kHz)
- **Windows:** 256 samples (~5ms at 48kHz)
- **Linux + JACK:** 64-128 samples (~1-3ms)

---

## Testing Strategy

### Manual Testing Checklist
- [ ] Boot server, initialize system
- [ ] Arm slot, wait for buffer allocation
- [ ] Record loop (4 bars)
- [ ] Loop plays back in sync
- [ ] Launch second clip, verify quantization
- [ ] Overdub on existing loop
- [ ] Stop clip on bar boundary
- [ ] Clear slot, verify buffer freed
- [ ] MIDI controller: press button, LED lights up
- [ ] MIDI controller: record and play from hardware
- [ ] Master limiter prevents clipping
- [ ] Per-channel effects apply correctly
- [ ] Change tempo, verify loops stay in sync

### Unit Tests (TestClipSlot.sc)
```supercollider
TestClipSlot : UnitTest {
    test_initialState {
        var slot = ClipSlot.new;
        this.assertEquals(slot.state, \empty, "New slot should be empty");
    }

    test_armTransition {
        var slot = ClipSlot.new;
        slot.arm(4); // 4 beat loop
        this.assertEquals(slot.state, \armed, "Armed slot should be in armed state");
    }

    // ... more tests
}
```

---

## Performance Notes

### Expected Latency Budget
```
Audio hardware buffer: 2-5ms (128 samples @ 48kHz)
+ Server scheduling latency: 200ms (for quantized events)
+ Network jitter (if using OSC): <1ms
----------------------------------------
Total perceived latency: ~200-205ms for quantized launches
                        ~2-5ms for immediate events
```

### CPU Considerations
- Each playing clip: 1 PlayBuf synth
- Each recording clip: 1 RecordBuf synth
- Per-channel effects: ~2-5% CPU per chain
- Master effects: ~5-10% CPU
- Target: <50% CPU with 8 channels, 16 playing clips, full FX

### Memory Budget
- Each 4-bar loop @ 120 BPM, 48kHz: ~1.5 MB per clip
- 64 clips × 8 channels = 512 clips max
- @ 1.5 MB each = ~770 MB for buffers
- Server memSize = 8192 * 16 = 128 MB (increase if needed)

---

## Implemented Extensions

### Session Save/Load ✅
- ✅ Save clip grid state to disk
- ✅ Store buffer contents as audio files (.wav format)
- ✅ Reload session on startup
- ✅ Human-readable metadata (.scd format)
- See [docs/session_saving.md](docs/session_saving.md) for details

## Future Extensions

### Clip Warping
- Time-stretch loops to match tempo changes
- Pitch-shift independent of tempo

### Scene Launch
- Launch entire rows/columns of clips simultaneously
- Crossfade between scenes

### Advanced Effects
- Per-clip effects (independent of channel)
- Effects automation via envelopes
- Sidechain compression between clips

### Multi-User Sync
- Multiple sc-clip instances linked via Ableton Link
- Distributed performance setup

---

## Configuration Decisions (User-Specified)

1. **Input channel count:** Configurable, **default 4 channels**
2. **Grid size:** 8x8 per channel (standard)
3. **Primary controller:** No controller initially, architecture supports future integration with configurable mappings
4. **Sync method:** **SC as master (TempoClock)** by default, configurable for Link/MIDI clock
5. **Quantization:** **1 bar**, time signature aware (default 4/4)
6. **Clip length:** User-specified per slot (flexible)

All settings are configurable at runtime - these are sensible defaults for initial testing.

---

## Success Criteria

### Minimum Viable Product (Phase 1-4)
- ✅ Can record 4-bar loops from hardware synth
- ✅ Loops play back in perfect sync with tempo clock
- ✅ Clip launches quantized to bar boundaries
- ✅ MIDI controller arms, records, plays, stops clips
- ✅ LED feedback shows clip states
- ✅ Per-channel effects work (using ddwMixerChannel)
- ✅ Master limiter prevents clipping
- ✅ System stable for 30+ minute continuous performance

### Code Quality
- ✅ Classes well-organized, one responsibility each
- ✅ Comments explain why, not just what
- ✅ Examples demonstrate all key features
- ✅ Setup doc gets new user running in <30 min
- ✅ Timing/latency design decisions documented

### Performance Requirements
- ✅ <5ms latency for immediate events
- ✅ Sample-accurate timing for quantized events
- ✅ No audio dropouts with 8 channels, 16 playing clips
- ✅ <50% CPU usage on typical system

---

## Implementation Notes

### Starting Order (Recommended)
1. `ClipSynthDefs.sc` - SynthDefs first (needed by everything else)
2. `ClipSlot.sc` - Core clip logic
3. `ClipChannel.sc` - Channel wrapper
4. `ClipGrid.sc` - Grid coordination
5. `ClipTransport.sc` - Timing/quantization
6. `ClipMasterBus.sc` - Master bus
7. `SCClip.sc` - Main class (glues everything together)
8. `ClipMIDIController.sc` - MIDI input
9. `ClipLaunchpad.sc` - Controller-specific code

### Testing as You Go
- After each class: write a small test script
- Don't wait until the end to test integration
- Use `s.plotTree` to verify node order
- Use `s.meter` to check signal flow

### Debugging Tips
```supercollider
// Check node order
s.plotTree;

// Monitor buses
~bus.scope;

// Post clip state
~slot.state.postln;

// Check buffer allocation
Buffer.cachedBuffersDo({ |b| [b.bufnum, b.numFrames].postln });
```

---

## Conclusion

This plan provides a complete architecture for a professional-grade clip launcher in SuperCollider. The modular design allows incremental implementation and testing, with clear separation of concerns.

**Key strengths:**
- Explicit state management (no hidden state bugs)
- Proper buffer allocation strategy (no allocation latency in performance)
- Split latency model (accurate quantization + responsive UI)
- Leverages proven quarks (ddwMixerChannel) for solved problems
- Extensible design (easy to add features later)

**Next steps:**
1. Review this plan, adjust defaults if needed
2. Implement Phase 1 (core infrastructure)
3. Test with hardware synth + manual control
4. Proceed to Phase 2-4 (transport, MIDI, effects)
5. Validate with full performance test

Ready to begin implementation when approved.
