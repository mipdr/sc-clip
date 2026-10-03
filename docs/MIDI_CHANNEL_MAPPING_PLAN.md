# MIDI Channel Mapping Implementation Plan

## Overview
Implement MIDI output channel mapping for MIDI channels, following the UI precedent established by audio input mapping. This allows users to route sc-clip MIDI channels to different MIDI output channels (0-15).

## User Requirements

From the original request:
- Map sc-clip MIDI channels to MIDI out channels (0-15)
- Follow UI precedent of audio input mapping
- **Row 8** (user terminology): Select MIDI channel to configure (same row as hardware audio inputs)
- **Row 7** (user terminology): MIDI out channel configuration (appears when MIDI channel selected)
- Row 7 uses **4 pads** (columns 1-4) for binary representation:
  - 0000 = channel 0
  - 0001 = channel 1
  - 1110 = channel 14
  - 1111 = channel 15
- Implement for:
  - Existing 2 MIDI channels on columns 5-6
  - Make reserved column 7 a MIDI channel too
  - **Total: 3 MIDI channels**

## Open Questions for User

### 1. Row/Column Indexing Clarification
**Question**: You mentioned "row 8" and "row 7" for MIDI channel selector and binary selector respectively. Are these **1-indexed** (meaning rows 7 and 6 in 0-indexed code)?

Similarly, you mentioned "columns 1-4" for binary selector - are these **1-indexed** (meaning columns 0-3 in 0-indexed code)?

**My assumption**:
- Row 8 (user) = row 7 (0-indexed in code)
- Row 7 (user) = row 6 (0-indexed in code)
- Columns 1-4 (user) = columns 0-3 (0-indexed in code)

**Why this matters**: Need to ensure we map the correct physical pads on the Launchpad.

---

### 2. Constructor numCols Parameter
**Question**: Should `ClipLaunchpadMini` constructor's `numCols` parameter be updated from 6 to 7?

Currently it's:
```supercollider
*new { |grid, transport, numRows = 6, numCols = 6, numAudioChannels = 4|
```

**My assumption**: Yes, change to `numCols = 7` since we now use all 7 columns (4 audio + 3 MIDI).

**Why this matters**: Affects clip grid iteration and validation logic.

---

### 3. Default MIDI Out Mapping
**Question**: What should the default MIDI out channel be for each MIDI channel?

**My assumption**:
- MIDI channel 0 → MIDI out channel 0
- MIDI channel 1 → MIDI out channel 1
- MIDI channel 2 → MIDI out channel 2

This matches current behavior and is intuitive.

---

### 4. Binary Selector Interaction Model
**Question**: Should each pad **toggle** its bit (press to flip 0↔1) or should there be a different interaction model (like "clear all" then build number)?

**My assumption**: Each pad toggles its bit independently. Press orange pad → turns green (0), press green pad → turns orange (1).

**Why this matters**: Affects user workflow - toggling is fastest but may be less intuitive for users unfamiliar with binary.

---

### 5. Binary Selector Visual Feedback
**Question**: Should the binary selector show the **current** MIDI out channel mapping in binary when a MIDI channel is selected?

**My assumption**: Yes - when you select a MIDI channel, row 6 (cols 0-3) immediately displays the current output channel in binary (green = 0, orange = 1).

**Why this matters**: Essential for user to know current state before making changes.

---

### 6. Binary Selector LED State (No MIDI Channel Selected)
**Question**: When no MIDI channel is selected, should the binary selector pads (row 6, cols 0-3) be:
- **Off** (LED velocity 12)
- **Green** (like when used for audio input mapping)
- Something else?

**My assumption**: Off - this visually indicates the binary selector is inactive.

**Why this matters**: Prevents confusion between audio input mapping mode and MIDI channel mapping mode.

---

### 7. UI Mode Conflict Resolution
**Question**: Row 6 (cols 0-3) is shared between:
- Audio input mapping: Shows which channels are mapped to selected audio input
- MIDI channel mapping: Binary selector for MIDI out channel

Should selecting a MIDI channel (row 7, cols 4-6) **automatically clear** the audio input selection (and vice versa)?

**My assumption**: Yes - selecting a MIDI channel clears `selectedInputIndex` and updates both LED sets. This ensures only one "mode" is active at a time.

**Why this matters**: Prevents ambiguous UI state where both modes appear active.

---

### 8. Third MIDI Channel Configuration
**Question**: Currently `ClipGrid` is initialized with 4 audio + 2 MIDI channels by default. Should we update the default configuration to 4 audio + 3 MIDI channels?

**My assumption**: Yes - update default `channelConfig` in `SCClip.sc` to:
```supercollider
channelConfig = [
    (type: \audio, hardwareInput: 0),  // Col 0
    (type: \audio, hardwareInput: 1),  // Col 1
    (type: \audio, hardwareInput: 2),  // Col 2
    (type: \audio, hardwareInput: 3),  // Col 3
    (type: \midi, midiInChannel: 0, midiOutChannel: 0),  // Col 4
    (type: \midi, midiInChannel: 0, midiOutChannel: 1),  // Col 5
    (type: \midi, midiInChannel: 0, midiOutChannel: 2),  // Col 6
];
```

**Why this matters**: Affects default session setup and backward compatibility.

---

## Architecture Changes

### Files to Modify

#### 1. `Classes/ClipMIDIChannel.sc`
**Current state**: Already has `midiOutChannel` instance variable ✓

**Changes needed**:
- Add `setMIDIOutChannel(channel)` method to update output channel dynamically
- Validate channel range (0-15)
- Log channel changes

**Implementation**:
```supercollider
setMIDIOutChannel { |newChannel|
    if (newChannel < 0 or: { newChannel > 15 }, {
        "ClipMIDIChannel[%]: Invalid MIDI out channel % (must be 0-15)".format(
            channelIndex, newChannel).error;
        ^this;
    });

    midiOutChannel = newChannel;

    "ClipMIDIChannel[%]: MIDI out channel set to %".format(
        channelIndex, midiOutChannel).postln;
}
```

---

#### 2. `Classes/ClipGrid.sc`
**Changes needed**:
- Add `setMIDIChannelOutput(channelIndex, midiOutChannel)` method
- Add `getMIDIChannelOutput(channelIndex)` query method
- Validate that channel is a MIDI channel (not audio)
- Update default channel configuration to include 3 MIDI channels

**Implementation**:
```supercollider
// Set MIDI output channel for a MIDI channel
setMIDIChannelOutput { |channelIndex, midiOutChannel|
    var channel;

    // Validate channel index
    if (channelIndex < 0 or: { channelIndex >= channels.size }, {
        "ClipGrid: Invalid channel index %".format(channelIndex).error;
        ^this;
    });

    channel = channels[channelIndex];

    // Only MIDI channels support MIDI out channel mapping
    if (channel.isKindOf(ClipMIDIChannel).not, {
        "ClipGrid: Cannot set MIDI out channel on audio channel %".format(channelIndex).error;
        ^this;
    });

    // Validate MIDI out channel (0-15)
    if (midiOutChannel < 0 or: { midiOutChannel > 15 }, {
        "ClipGrid: Invalid MIDI out channel % (must be 0-15)".format(midiOutChannel).error;
        ^this;
    });

    channel.setMIDIOutChannel(midiOutChannel);
}

// Get MIDI output channel for a MIDI channel
getMIDIChannelOutput { |channelIndex|
    var channel;

    if (channelIndex < 0 or: { channelIndex >= channels.size }, {
        ^nil;
    });

    channel = channels[channelIndex];

    if (channel.isKindOf(ClipMIDIChannel), {
        ^channel.midiOutChannel;
    }, {
        ^nil;
    });
}
```

**Update default channel config** in `init` method:
```supercollider
// Default to 4 audio + 3 MIDI if not specified
if (channelConfig.isNil, {
    channelConfig = Array.fill(numChannels, { |i|
        if (i < (numChannels - 3), {
            (type: \audio, hardwareInput: i)
        }, {
            // Last 3 channels are MIDI
            var midiIdx = i - (numChannels - 3);
            (type: \midi, midiInChannel: 0, midiOutChannel: midiIdx)
        });
    });
});
```

---

#### 3. `Classes/ClipLaunchpadMini.sc`
**Changes needed**:
- Update header comment with new grid layout (3 MIDI channels, no reserved column)
- Add instance variables: `selectedMIDIChannelIndex`, `numMIDIChannels`
- Update constructor to accept `numMIDIChannels` parameter
- Update `handleButtonPress()` to route row 7 (cols 4-6) and row 6 (cols 0-3)
- Add `selectMIDIChannel()`, `toggleBinaryDigit()`, `updateMIDIChannelMappingLEDs()` methods
- Update `updateAllLEDs()` to clear cache for MIDI mapping rows

**New header comment**:
```supercollider
/*
 * Grid Layout (XY mode):
 *   - 8x8 grid
 *   - Columns 0-3: Audio channels (4 channels)
 *   - Columns 4-6: MIDI channels (3 channels)
 *   - Column 7: Controls (clip length selector + metronome)
 *   - Rows 0-5: Clip slots (6 slots per channel)
 *   - Row 6: Binary MIDI out channel selector (cols 0-3, when MIDI channel selected)
 *            OR Audio channel selector (cols 0-3, when audio input selected)
 *   - Row 7: MIDI channel selector (cols 4-6) + Audio input selector (cols 0-3) + metronome (col 7)
 *
 * Input-to-Channel Mapping (rows 6-7, cols 0-3):
 *   - Row 7 (cols 0-3): Input selector - press to select which input to configure
 *   - Row 6 (cols 0-3): Channel selector - press to map selected input to channel
 *   - Green = unselected, Orange = selected/mapped
 *
 * MIDI Channel Mapping (rows 6-7, cols 4-6 and 0-3):
 *   - Row 7 (cols 4-6): MIDI channel selector - press to select which MIDI channel to configure
 *   - Row 6 (cols 0-3): Binary MIDI out channel selector (when MIDI channel selected)
 *     - Each pad represents a binary digit: col 0 = bit 0 (1), col 1 = bit 1 (2), col 2 = bit 2 (4), col 3 = bit 3 (8)
 *     - Binary encoding: 0000 = ch 0, 0001 = ch 1, 1111 = ch 15
 *     - Green = 0, Orange = 1
 *     - Press pad to toggle bit (0↔1)
 */
```

**Updated constructor**:
```supercollider
var <selectedMIDIChannelIndex;  // Currently selected MIDI channel (nil = none)
var <numMIDIChannels;           // Number of MIDI channels

*new { |grid, transport, numRows = 6, numCols = 7, numAudioChannels = 4, numMIDIChannels = 3|
    ^super.new(grid, transport, numRows, numCols)
        .initClipLength
        .initInputMapping(numAudioChannels)
        .initMIDIChannelMapping(numMIDIChannels);
}

initMIDIChannelMapping { |numMIDI|
    numMIDIChannels = numMIDI;
    selectedMIDIChannelIndex = nil;
}
```

**Button handling updates**:
```supercollider
handleButtonPress { |row, col, velocity, isNoteOn|
    // Row 7 (MIDI channel selector), cols 4-6
    if (row == 7 and: { col >= 4 and: { col < (4 + numMIDIChannels) } }, {
        if (isNoteOn, { this.selectMIDIChannel(col - 4) });
        ^this;
    });

    // Row 6 (binary selector), cols 0-3
    // Only active if MIDI channel selected AND no audio input selected
    if (row == 6 and: { col < 4 } and: {
        selectedMIDIChannelIndex.notNil and: { selectedInputIndex.isNil }
    }, {
        if (isNoteOn, { this.toggleBinaryDigit(col) });
        ^this;
    });

    // ... existing button handling
}
```

**New methods**:
```supercollider
selectMIDIChannel { |midiChanIdx|
    if (selectedMIDIChannelIndex == midiChanIdx, {
        // Deselect if clicking same MIDI channel again
        selectedMIDIChannelIndex = nil;
        "ClipLaunchpadMini: MIDI channel deselected".postln;
    }, {
        selectedMIDIChannelIndex = midiChanIdx;
        selectedInputIndex = nil;  // Clear audio input selection
        "ClipLaunchpadMini: Selected MIDI channel % for mapping".format(midiChanIdx).postln;
    });

    this.updateMIDIChannelMappingLEDs;
    this.updateInputMappingLEDs;  // Clear audio input LEDs
}

toggleBinaryDigit { |digitIndex|
    var actualChannelIndex = numAudioChannels + selectedMIDIChannelIndex;
    var currentOutChannel = grid.getMIDIChannelOutput(actualChannelIndex) ? 0;
    var bitValue = 2.pow(digitIndex).asInteger;
    var newOutChannel;

    // Toggle the bit
    newOutChannel = currentOutChannel.bitXor(bitValue);

    grid.setMIDIChannelOutput(actualChannelIndex, newOutChannel);

    "ClipLaunchpadMini: MIDI channel % out channel set to % (binary: %)"
        .format(selectedMIDIChannelIndex, newOutChannel,
                newOutChannel.asBinaryDigits(4).join).postln;

    this.updateMIDIChannelMappingLEDs;
}

updateMIDIChannelMappingLEDs {
    // Row 7 (MIDI channel selector): Show all MIDI channels, highlight selected
    numMIDIChannels.do { |midiChanIdx|
        this.sendLEDMessage(7, 4 + midiChanIdx,
            if (midiChanIdx == selectedMIDIChannelIndex, \orange, \green));
    };

    // Row 6 (binary selector): Only show when MIDI channel selected
    4.do { |digitIdx|
        var color;

        if (selectedMIDIChannelIndex.isNil, {
            color = \off;  // Hidden when no MIDI channel selected
        }, {
            var actualChannelIndex = numAudioChannels + selectedMIDIChannelIndex;
            var outChannel = grid.getMIDIChannelOutput(actualChannelIndex) ? 0;
            var bitValue = 2.pow(digitIdx).asInteger;
            var bitIsSet = (outChannel.bitAnd(bitValue) > 0);

            color = if (bitIsSet, \orange, \green);
        });

        this.sendLEDMessage(6, digitIdx, color);
    };
}
```

**Update `updateAllLEDs`**:
```supercollider
updateAllLEDs {
    super.updateAllLEDs;

    // Clear cache for MIDI channel mapping rows
    numMIDIChannels.do { |col|
        ledCache.removeAt((7 * 100) + (4 + col));  // Row 7 (MIDI channel selector)
    };
    4.do { |col|
        ledCache.removeAt((6 * 100) + col);  // Row 6 (binary selector)
    };

    this.updateClipLengthLEDs;
    this.updateMetronomeLED;
    this.updateInputMappingLEDs;
    this.updateMIDIChannelMappingLEDs;
}
```

---

#### 4. `Classes/SCClip.sc`
**Changes needed**:
- Update default channel configuration to 4 audio + 3 MIDI
- Save MIDI out channel mapping in `asSessionData()`
- Restore MIDI out channel mapping in `restoreChannelsAndSlots()`
- Add public wrapper methods

**Default channel config update**:
```supercollider
// In SCClip class *new method
if (channelConfig.isNil, {
    channelConfig = [
        (type: \audio, hardwareInput: 0),
        (type: \audio, hardwareInput: 1),
        (type: \audio, hardwareInput: 2),
        (type: \audio, hardwareInput: 3),
        (type: \midi, midiInChannel: 0, midiOutChannel: 0),
        (type: \midi, midiInChannel: 0, midiOutChannel: 1),
        (type: \midi, midiInChannel: 0, midiOutChannel: 2),
    ];
    numChannels = 7;
});
```

**Session save/load**:
```supercollider
// In asSessionData() - save MIDI out channel
data[\channels] = grid.channels.collect { |channel, chanIdx|
    if (channel.isKindOf(ClipMIDIChannel), {
        Dictionary[
            \type -> \midi,
            \midiInChannel -> channel.midiInChannel,
            \midiOutChannel -> channel.midiOutChannel,  // SAVE THIS
            \slots -> channel.slots.collect { |slot|
                if (slot.buffer.notNil, {
                    Dictionary[
                        \path -> slot.bufferPath,
                        \loopLengthBeats -> slot.loopLengthBeats
                    ]
                }, {
                    nil
                })
            }
        ]
    }, {
        // ... audio channel data
    });
};

// In restoreChannelsAndSlots() - restore MIDI out channel
channelsData.do { |channelData, chanIdx|
    if (channelData[\type] == \midi, {
        var midiChannel = grid.getChannel(chanIdx);

        // Restore MIDI out channel if saved
        if (channelData[\midiOutChannel].notNil, {
            midiChannel.setMIDIOutChannel(channelData[\midiOutChannel]);
        });

        // ... rest of restoration
    });
};
```

**Public API wrappers**:
```supercollider
setMIDIChannelOutput { |channelIndex, midiOutChannel|
    grid.setMIDIChannelOutput(channelIndex, midiOutChannel);
}

getMIDIChannelOutput { |channelIndex|
    ^grid.getMIDIChannelOutput(channelIndex);
}
```

---

#### 5. `Tests/test_midi_channel_mapping.scd`
**New file**: Comprehensive test suite

**Test cases**:
1. Verify default mapping (MIDI ch 0→out 0, ch 1→out 1, ch 2→out 2)
2. Change MIDI out channel (set ch 0 to output on channel 5)
3. Binary encoding verification (test all 16 values: 0-15)
4. Multiple MIDI channels (configure all 3 with different outputs)
5. Session save/load (verify MIDI out mappings persist)
6. Query API (verify `getMIDIChannelOutput()` returns correct values)
7. Invalid inputs (test error handling for invalid channel numbers)
8. Audio channel rejection (verify error when trying to set MIDI out on audio channel)

---

#### 6. `Examples/midi_channel_mapping_example.scd`
**New file**: Usage examples and tutorials

**Examples**:
1. **Multi-timbral setup**: Route 3 MIDI channels to 3 different synths
2. **Binary selector tutorial**: Step-by-step guide to binary encoding
3. **Launchpad workflow**: Visual guide to UI interaction
4. **Session management**: Save and reload complex routing setups
5. **Live performance workflow**: Quick routing changes during performance
6. **Drum machine routing**: Separate MIDI channels for kick, snare, hats

---

## Testing Strategy

### Unit Tests
- Default MIDI out channel assignment
- `setMIDIChannelOutput()` validation (range 0-15)
- `getMIDIChannelOutput()` returns correct values
- Audio channel rejection (error handling)
- Binary digit operations (bit manipulation correctness)

### Integration Tests
- Session save/load preserves MIDI out mappings
- Multiple MIDI channels with different outputs
- Launchpad LED updates reflect state correctly
- Mode switching (audio input mapping ↔ MIDI channel mapping)

### Functional Tests
- MIDI messages route to correct output channel
- Real-time channel switching during playback
- Binary selector UI workflow (toggle bits to build channel number)

---

## Implementation Workflow

1. **Update ClipMIDIChannel**: Add `setMIDIOutChannel()` method
2. **Update ClipGrid**: Add MIDI out channel mapping API and default config
3. **Update ClipLaunchpadMini**: Add UI for MIDI channel selection and binary selector
4. **Update SCClip**: Add session save/load and public API
5. **Write tests**: Create comprehensive test suite
6. **Write examples**: Create usage examples and tutorials
7. **Test on hardware**: Verify with real Launchpad Mini
8. **Update documentation**: Update user-facing docs if needed

---

## Binary Encoding Reference

For user reference, here's how the 4-pad binary selector works:

| Col 0 (bit 0) | Col 1 (bit 1) | Col 2 (bit 2) | Col 3 (bit 3) | MIDI Channel |
|---------------|---------------|---------------|---------------|--------------|
| Green (0)     | Green (0)     | Green (0)     | Green (0)     | 0            |
| Orange (1)    | Green (0)     | Green (0)     | Green (0)     | 1            |
| Green (0)     | Orange (1)    | Green (0)     | Green (0)     | 2            |
| Orange (1)    | Orange (1)    | Green (0)     | Green (0)     | 3            |
| Green (0)     | Green (0)     | Orange (1)    | Green (0)     | 4            |
| ...           | ...           | ...           | ...           | ...          |
| Orange (1)    | Orange (1)    | Orange (1)    | Orange (1)    | 15           |

Each column represents a power of 2:
- Col 0 = 2^0 = 1
- Col 1 = 2^1 = 2
- Col 2 = 2^2 = 4
- Col 3 = 2^3 = 8

To set channel 11 (binary 1011):
- Col 0: Orange (1 × 1 = 1)
- Col 1: Orange (1 × 2 = 2)
- Col 2: Green (0 × 4 = 0)
- Col 3: Orange (1 × 8 = 8)
- Total: 1 + 2 + 0 + 8 = 11 ✓

---

## Backward Compatibility

**Breaking changes**:
- Default channel configuration changes from 4 audio + 2 MIDI to 4 audio + 3 MIDI
- Launchpad Mini column 6 (previously reserved) now used for 3rd MIDI channel

**Migration path**:
- Existing sessions with 2 MIDI channels will load correctly
- Users can manually configure back to 2 MIDI channels if needed
- No changes to existing MIDI channel functionality (fully backward compatible)

---

## Summary

This implementation adds flexible MIDI output channel routing with an intuitive binary selector UI on the Launchpad Mini. The design follows the established pattern from audio input mapping, making it familiar to users. The binary selector provides access to all 16 MIDI channels (0-15) using only 4 pads, maximizing the limited Launchpad grid space.

Once the open questions above are resolved, implementation can proceed following this plan.
