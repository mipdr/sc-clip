# Session Saving and Loading

SC-Clip supports saving and loading complete sessions, including all audio loops, mixer settings, transport configuration, and more.

## Features

- **Idiomatic SuperCollider approach**: Uses `Dictionary.writeArchive` for metadata (human-readable .scd format)
- **Audio preservation**: Exports audio buffers as high-quality .wav files
- **Complete state capture**: Saves tempo, mixer levels, pan, mute/solo, loop lengths, and more
- **Async I/O**: Non-blocking save/load operations with completion callbacks
- **Critical data preservation**: Stores `loopLengthSamples` to prevent drift on tempo changes

## Session Structure

When you save a session, SC-Clip creates a directory with the following structure:

```
MySong/
├── session.scd              # Session metadata (readable SC code)
├── channel_0_slot_0.wav     # Audio buffer for channel 0, slot 0
├── channel_0_slot_1.wav     # Audio buffer for channel 0, slot 1
├── channel_1_slot_0.wav     # Audio buffer for channel 1, slot 0
└── ...
```

### Metadata (session.scd)

The `session.scd` file is a SuperCollider Dictionary archive containing:

- **Transport settings**: tempo, time signature, quantization, metronome state
- **Master bus**: level, mastering chain settings (EQ, compressor, limiter)
- **Grid configuration**: number of channels and slots
- **Channel settings**: level, pan, mute/solo state
- **Slot metadata**: loop lengths (beats and samples), state, audio file references

### Audio Files

Audio buffers are exported as 32-bit float WAV files, preserving the exact sample data recorded in each clip slot.

## API

### Saving a Session

```supercollider
// Save to a new location
clip.saveAs(
    path: "/path/to/MySong",
    action: {
        "Session saved!".postln;
    }
);
```

**Parameters:**
- `path`: Directory path where the session will be saved (created if it doesn't exist)
- `action`: Optional callback function executed when save is complete

### Loading a Session

```supercollider
// Load a saved session (class method)
~clip = SCClip.load(
    path: "/path/to/MySong",
    server: Server.default,
    action: {
        "Session loaded!".postln;
        ~clip.printStatus;
    }
);
```

**Parameters:**
- `path`: Directory path containing the saved session
- `server`: The server to use (defaults to `Server.default`)
- `action`: Optional callback function executed when load is complete

**Returns:** A new `SCClip` instance with the loaded session state

## Saved State

### Transport
- Tempo (BPM)
- Beats per bar
- Time signature
- Quantization setting
- Sync mode (internal/link/midiclock)
- Metronome enabled/disabled and volume

### Master Bus
- Master level (dB)
- EQ settings (frequencies, gains, Q)
- Compressor settings (threshold, ratio, attack, release, makeup gain)
- Limiter settings (ceiling, duration)

### Channels
- Level (dB)
- Pan position (-1 to 1)
- Mute state
- Solo state

### Clip Slots
- Audio buffer content (.wav files)
- Loop length in beats
- Loop length in samples (critical for tempo-independent playback)
- Slot state (armed, stopped, etc.)

## Example Workflow

```supercollider
// 1. Create a session and record some clips
~clip = SCClip.new(numChannels: 4, numSlots: 8);

~clip.boot(action: {
    // Set tempo and time signature
    ~clip.setTempo(120);
    ~clip.setTimeSignature(4, 4);

    // Configure channel levels
    ~clip.setChannelLevel(0, -6);
    ~clip.setChannelLevel(1, -9);

    // ... arm slots, record clips, etc ...

    // After recording, save the session
    ~clip.saveAs(
        path: Platform.userAppSupportDir +/+ "SC-Clip/MySong",
        action: { "Saved!".postln; }
    );
});

// 2. Later, load the session
~clip.shutdown;  // Clean up current session first

~clip = SCClip.load(
    path: Platform.userAppSupportDir +/+ "SC-Clip/MySong",
    action: {
        "Loaded!".postln;

        // All clips, settings, and audio are restored
        // You can now launch clips, adjust settings, etc.
        ~clip.launchSlot(0, 0);
    }
);
```

## Important Notes

### Async Operations

Both save and load operations are asynchronous because they involve disk I/O and buffer operations. Always use the completion callback if you need to perform actions after the operation completes:

```supercollider
// ❌ Wrong - save might not be complete yet
~clip.saveAs("/path/to/session");
"Saved!".postln;  // This runs immediately, before save is done

// ✅ Correct - use callback
~clip.saveAs("/path/to/session", action: {
    "Saved!".postln;  // This runs after save completes
});
```

### Server State

When loading a session, ensure the server is in a clean state. If you have a running session, shut it down first:

```supercollider
~clip.shutdown;  // Cleans up current session
~clip = SCClip.load("/path/to/session");
```

### Slot States

Loaded clips default to the `\stopped` state, even if they were playing when the session was saved. This prevents unexpected audio playback on load. Launch clips manually after loading:

```supercollider
~clip = SCClip.load("/path", action: {
    // Manually launch clips you want to play
    ~clip.launchSlot(0, 0);
    ~clip.launchSlot(1, 2);
});
```

### Path Management

Use `Platform.userAppSupportDir` for portable session storage:

```supercollider
// Platform-independent path
var sessionDir = Platform.userAppSupportDir +/+ "SC-Clip/MySessions";

~clip.saveAs(sessionDir +/+ "MySong");
```

### Not Saved

The following ephemeral state is **not** saved:
- Running synths (all playback is stopped on load)
- Current transport position (beat counter resets)
- MIDI controller mappings
- Link sync connections
- Channel effects (plugins/insert effects) - TODO for future enhancement

## Implementation Details

### Dictionary.writeArchive

SC-Clip uses SuperCollider's built-in `Object.writeArchive` method, which generates human-readable `.scd` code that recreates the data structure when executed. This is the idiomatic way to serialize SC objects.

### Buffer Write/Read

Audio buffers are written using `Buffer.write` with the following settings:
- Format: 32-bit float WAV
- Channels: 1 (mono)
- Frames: Exact loop length in samples

This ensures lossless audio preservation and maintains sample-accurate loop points.

### Loop Length Preservation

The critical `loopLengthSamples` field is saved and restored exactly. This prevents drift when the tempo changes, because sample-based playback is tempo-independent.

## See Also

- [Examples/session_save_load_test.scd](../Examples/session_save_load_test.scd) - Complete test script
- [SuperCollider Archive documentation](https://doc.sccode.org/Classes/Archive.html)
- [Buffer.write documentation](https://doc.sccode.org/Classes/Buffer.html#-write)
