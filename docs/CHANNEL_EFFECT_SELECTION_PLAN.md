# Channel Effect Selection Implementation Plan

## Overview

Replace hardcoded channel effects (reverb, delay, distortion) with a flexible effect selection system that allows hot-swapping effects in three slots per channel via GUI dropdowns.

## Current State Analysis

### Hardcoded Effects (in `ClipSynthDefs.sc`)

Currently, three effects are hardcoded per channel:
1. **`\channelReverb`** - FreeVerb (mix, room, damp parameters)
2. **`\channelDelay`** - CombN delay (mix, delayTime, decayTime parameters)
3. **`\channelDistortion`** - tanh waveshaping (mix, drive parameters)
4. **`\channelBoum`** - Already implemented compressor + distortion + hi-cut + gate

### Current Effect Architecture

From `ClipChannel.sc`:
- `effects` - IdentityDictionary mapping slot index → running Synth
- `addEffect(synthDef, args, slot)` - Replace effect in slot
- `removeEffect(slot)` - Free effect in slot
- `setEffectParam(slot, param, value)` - Live parameter updates
- Effects chain through `MixerChannel.playfx()` (from ddwMixerChannel quark)
- Effects use `ReplaceOut.ar` on mono (1-channel) inbus

**Key insight**: The slot system already exists! We just need to expose it in the GUI.

---

## User Requirements

1. **Three effect slots per channel** (maintain existing architecture)
2. **Hot-swappable effects** - Change effects live without stopping audio
3. **GUI with dropdowns** - One dropdown per slot per channel
4. **Effect table UI** - Readable layout with columns=channels, rows=effect stages
5. **Signal flow visualization** - Arrows pointing up (bottom effect = first, top effect = last)
6. **Effect registration system** - Default preselected effects registered at startup
7. **Bypass option** - Add bypass to effect list
8. **Include existing effects** - Reverb, delay, distortion, mono Boum
9. **Performant effects** - CPU-efficient effects suitable for Core M laptop

---

## Open Questions

### 1. GUI Layout and Integration

**Question**: Should the channel effects GUI be:
- **Option A**: A separate window (like `ClipMasteringGUI`)?
- **Option B**: Integrated into an existing GUI?
- **Option C**: A new unified GUI that combines mastering and channel effects?

**My recommendation**: Option A - separate `ClipChannelEffectsGUI` window for these reasons:
- Follows existing pattern (`ClipMasteringGUI`)
- Can be opened/closed independently
- Easier to maintain separation of concerns
- Allows future unified GUI later

---

### 2. Effect Table Layout Orientation

**Question**: You mentioned "arrows should be down to up, as downmost effect is first of chain and upmost one is last". Should the GUI layout be:

**Option A - Vertical Layout** (my interpretation):
```
Channel 1   Channel 2   Channel 3   Channel 4
─────────   ─────────   ─────────   ─────────
  ↑           ↑           ↑           ↑
[Slot 2]    [Slot 2]    [Slot 2]    [Slot 2]   ← Last in chain
  ↑           ↑           ↑           ↑
[Slot 1]    [Slot 1]    [Slot 1]    [Slot 1]   ← Middle
  ↑           ↑           ↑           ↑
[Slot 0]    [Slot 0]    [Slot 0]    [Slot 0]   ← First in chain
```

**Option B - Horizontal Layout**:
```
        Channel 1   Channel 2   Channel 3   Channel 4
Slot 0  [Effect] →  [Effect] →  [Effect] →  [Effect]   ← First
Slot 1  [Effect] →  [Effect] →  [Effect] →  [Effect]   ← Middle
Slot 2  [Effect] →  [Effect] →  [Effect] →  [Effect]   ← Last
```

**My recommendation**: Option A (vertical) - matches your "down to up" description and is more visually intuitive for signal flow.

---

### 3. MIDI Channels in GUI

**Question**: Should the effect GUI include:
- **Audio channels only** (4 channels, columns 0-3)?
- **Audio + MIDI channels** (7 channels total)?

**Note**: MIDI channels (`ClipMIDIChannel`) don't have `mixerChannel.playfx()` capability since they don't route audio through `ddwMixerChannel`. Effects only make sense for audio channels.

**My recommendation**: Audio channels only (4 channels). MIDI channels generate notes, not audio.

---

### 4. Effect Parameter Control in GUI

**Question**: Should the GUI include:
- **Dropdowns only** (select effect, use default parameters)?
- **Dropdowns + parameter controls** (sliders/knobs for each effect's parameters)?

**Option A - Dropdowns only**:
- Simpler implementation
- Cleaner UI
- Parameters controlled via MIDI/code

**Option B - Dropdowns + parameters**:
- More complex UI (need to show/hide different parameters per effect)
- Self-contained control
- Similar to `ClipMasteringGUI` approach

**My recommendation**: Start with Option A (dropdowns only) for simplicity. Parameters can be controlled via:
- MIDI controllers (existing `setEffectParam()` API)
- Code (API calls)
- Future enhancement: expandable parameter sections

---

### 5. Default Effect Parameters

**Question**: What default parameters should each effect use when initially loaded?

**My recommendation**: Use musically-neutral defaults that sound good:
- **Reverb**: mix=0.3, room=0.5, damp=0.5 (current defaults)
- **Delay**: mix=0.3, delayTime=0.3, decayTime=2 (current defaults)
- **Distortion**: mix=0.3, drive=0.5 (current defaults)
- **Boum**: thresh=-12, ratio=3, drive=0, mix=1 (similar to master Boum)
- **New effects**: conservative settings (see effect list below)

---

### 6. Session Save/Load

**Question**: Should effect slot selections be saved in sessions?

**My recommendation**: Yes - save:
- Effect type for each slot (symbol name like `\channelReverb`)
- Effect parameters (Dictionary of parameter values)
- Restore on session load

This ensures sessions recall exact effect chains.

---

### 7. Effect Registration API

**Question**: Should effect registration be:
- **Simple list** - Just register symbol names (e.g., `[\channelReverb, \channelDelay, ...]`)?
- **Rich metadata** - Register with display names, parameter specs, categories?

**Option A - Simple**:
```supercollider
SCClip.registerEffect(\channelReverb);
SCClip.registerEffect(\channelDelay);
```

**Option B - Rich**:
```supercollider
SCClip.registerEffect(\channelReverb, (
    displayName: "Reverb (FreeVerb)",
    category: \reverb,
    parameters: (
        mix: ControlSpec(0, 1, \lin, 0.01, 0.3),
        room: ControlSpec(0, 1, \lin, 0.01, 0.5),
        damp: ControlSpec(0, 1, \lin, 0.01, 0.5)
    )
));
```

**My recommendation**: Option B (rich metadata) because:
- Enables better GUI labels ("Reverb (FreeVerb)" vs "\channelReverb")
- Supports future parameter UI
- Allows categorization (reverb, delay, modulation, etc.)
- More extensible

---

### 8. Bypass Implementation

**Question**: Should bypass be:
- **Null effect** (slot contains `nil`, no synth running)?
- **Passthrough SynthDef** (explicit `\channelBypass` that does nothing)?

**My recommendation**: Null effect (nil) because:
- More CPU efficient (no synth running)
- Simpler implementation
- `ClipChannel.removeEffect(slot)` already handles this

GUI would show "Bypass" in dropdown, internally sets slot to nil.

---

## Proposed Effect Library

Based on research of SuperCollider built-in UGens and CPU efficiency requirements:

### Category: Reverb

#### 1. **FreeVerb** (already implemented as `\channelReverb`)
- **Type**: Basic algorithmic reverb
- **Parameters**: mix, room, damp
- **CPU**: Very light
- **Use case**: General-purpose reverb
- **Source**: Built-in SC UGen

#### 2. **GVerb** (new)
- **Type**: Granular reverb (if sc3-plugins available)
- **Parameters**: mix, roomsize, revtime, damping, spread
- **CPU**: Moderate
- **Use case**: Richer, more diffuse reverb
- **Source**: sc3-plugins (optional - check availability at startup)
- **Fallback**: If unavailable, don't register

### Category: Delay

#### 3. **Simple Delay** (already implemented as `\channelDelay`)
- **Type**: CombN comb filter delay
- **Parameters**: mix, delayTime, decayTime
- **CPU**: Very light
- **Use case**: Echo, slapback
- **Source**: Built-in SC UGen

#### 4. **Ping-Pong Delay** (new)
- **Type**: Stereo delay with L/R alternation
- **Parameters**: mix, delayTime, feedback
- **CPU**: Light
- **Use case**: Wide stereo delay
- **Source**: Built-in (DelayN + panning)
- **Note**: Requires stereo version of channel effects (see question 9)

### Category: Distortion/Saturation

#### 5. **Soft Distortion** (already implemented as `\channelDistortion`)
- **Type**: tanh waveshaping
- **Parameters**: mix, drive
- **CPU**: Very light
- **Use case**: Warm saturation
- **Source**: Built-in SC UGen

#### 6. **Hard Clip** (new)
- **Type**: clip2 hard clipping
- **Parameters**: mix, drive, bias
- **CPU**: Very light
- **Use case**: Aggressive distortion
- **Source**: Built-in SC UGen

### Category: Modulation

#### 7. **Chorus** (new)
- **Type**: Modulated delay (20-30ms)
- **Parameters**: mix, rate, depth, voices
- **CPU**: Light
- **Use case**: Thickening, doubling
- **Source**: Built-in (DelayC + LFO)

#### 8. **Phaser** (new)
- **Type**: Allpass filter modulation
- **Parameters**: mix, rate, depth, stages
- **CPU**: Light
- **Use case**: Phasing sweep
- **Source**: Built-in (AllpassN + LFO)

#### 9. **Flanger** (new)
- **Type**: Short modulated delay (<10ms)
- **Parameters**: mix, rate, depth, feedback
- **CPU**: Light
- **Use case**: Jet-plane whoosh
- **Source**: Built-in (DelayC + LFO + feedback)

### Category: Filter

#### 10. **Resonant Filter** (new)
- **Type**: Resonant low-pass filter
- **Parameters**: freq, resonance, mix
- **CPU**: Very light
- **Use case**: Synth-style filtering
- **Source**: Built-in (RLPF)

#### 11. **High-Pass Filter** (new)
- **Type**: Resonant high-pass filter
- **Parameters**: freq, resonance, mix
- **CPU**: Very light
- **Use case**: Remove low-end
- **Source**: Built-in (RHPF)

### Category: Dynamics

#### 12. **Compressor** (Boum, already implemented as `\channelBoum`)
- **Type**: Compressor + distortion + gate + hi-cut
- **Parameters**: thresh, ratio, attack, release, drive, type, hicut, gateThresh, makeupGain, mix
- **CPU**: Moderate
- **Use case**: Compression and color
- **Source**: Already implemented

#### 13. **Simple Compressor** (new)
- **Type**: Basic Compander
- **Parameters**: thresh, ratio, attack, release, mix
- **CPU**: Light
- **Use case**: Simple compression
- **Source**: Built-in (Compander)

### Category: Utility

#### 14. **Bypass** (null effect)
- **Type**: No processing
- **Parameters**: None
- **CPU**: None (no synth)
- **Use case**: Disable slot

**Total**: 14 effects (4 existing + 10 new)

---

## Open Question 9: Stereo Channel Effects?

**Question**: Currently all channel effects are **mono** (process 1-channel inbus). Should we:

**Option A**: Keep mono
- Simpler implementation
- Matches current architecture (channels are mono until mixed to stereo master)
- Most effects work fine in mono

**Option B**: Convert to stereo
- More complex (need to handle stereo in `MixerChannel`)
- Enables stereo effects (ping-pong delay, stereo chorus)
- Requires architectural change

**My recommendation**: Option A (keep mono) for initial implementation. Stereo effects can be a future enhancement.

**Impact on ping-pong delay**: Skip for now, or implement as mono with fake stereo (rapid pan modulation).

---

## Architecture Design

### Class: `ClipEffectRegistry`

New class to manage available effects:

```supercollider
ClipEffectRegistry {
    classvar <effects;  // Dictionary: synthDefName -> metadata

    *initClass {
        effects = Dictionary.new;
    }

    *register { |synthDefName, metadata|
        // metadata: (
        //     displayName: "Reverb (FreeVerb)",
        //     category: \reverb,
        //     parameters: Dictionary of ControlSpecs
        // )
        effects[synthDefName] = metadata;
    }

    *get { |synthDefName|
        ^effects[synthDefName];
    }

    *all {
        ^effects;
    }

    *allNames {
        ^effects.keys.asArray.sort;
    }

    *categorized {
        // Return effects organized by category
        var categorized = Dictionary.new;
        effects.keysValuesDo { |name, meta|
            var cat = meta[\category] ? \other;
            if (categorized[cat].isNil, {
                categorized[cat] = List.new;
            });
            categorized[cat].add(name);
        };
        ^categorized;
    }
}
```

---

### Modified Class: `ClipSynthDefs`

Add new effect SynthDefs and register them:

```supercollider
*addChannelEffects {
    // [Existing effects stay the same]

    // NEW: Simple Compressor
    SynthDef(\channelCompressor, { |out,
        thresh= -12, ratio=3, attack=0.01, release=0.3, mix=1|
        var sig = In.ar(out, 1);
        var dry = sig;
        var wet = Compander.ar(sig, sig,
            thresh.dbamp, 1, 1/ratio, attack, release);
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // NEW: Hard Clip
    SynthDef(\channelHardClip, { |out, drive=0.5, bias=0, mix=0.3|
        var sig = In.ar(out, 1);
        var dry = sig;
        var driven = (sig * (1 + (drive * 10)) + bias).clip2(0.8);
        ReplaceOut.ar(out, (dry * (1 - mix)) + (driven * mix));
    }).add;

    // NEW: Chorus
    SynthDef(\channelChorus, { |out, mix=0.5, rate=0.5, depth=0.01, voices=4|
        var sig = In.ar(out, 1);
        var dry = sig;
        var wet = Mix.fill(voices, { |i|
            var phase = (i / voices) * 2pi;
            var mod = SinOsc.kr(rate, phase).range(0.02, 0.02 + depth);
            DelayC.ar(sig, 0.1, mod);
        }) / voices;
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // NEW: Phaser
    SynthDef(\channelPhaser, { |out, mix=0.5, rate=0.3, depth=0.5, stages=4|
        var sig = In.ar(out, 1);
        var dry = sig;
        var mod = SinOsc.kr(rate).range(0.0001, 0.01);
        var wet = sig;
        stages.do {
            wet = AllpassN.ar(wet, 0.02, mod);
        };
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // NEW: Flanger
    SynthDef(\channelFlanger, { |out, mix=0.5, rate=0.2, depth=0.005, feedback=0.5|
        var sig = In.ar(out, 1);
        var dry = sig;
        var mod = SinOsc.kr(rate).range(0.001, 0.001 + depth);
        var delayed = LocalIn.ar(1);
        delayed = DelayC.ar(sig + (delayed * feedback), 0.02, mod);
        LocalOut.ar(delayed);
        ReplaceOut.ar(out, (dry * (1 - mix)) + (delayed * mix));
    }).add;

    // NEW: Resonant Low-Pass Filter
    SynthDef(\channelLowPass, { |out, freq=1000, resonance=0.5, mix=1|
        var sig = In.ar(out, 1);
        var dry = sig;
        var wet = RLPF.ar(sig, freq, 1 - (resonance * 0.9));
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // NEW: Resonant High-Pass Filter
    SynthDef(\channelHighPass, { |out, freq=200, resonance=0.5, mix=1|
        var sig = In.ar(out, 1);
        var dry = sig;
        var wet = RHPF.ar(sig, freq, 1 - (resonance * 0.9));
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // Optionally check for sc3-plugins and add GVerb if available
    if (GVerb.respondsTo(\ar), {
        SynthDef(\channelGVerb, { |out, mix=0.3, roomsize=10, revtime=3,
            damping=0.5, spread=15|
            var sig = In.ar(out, 1);
            var dry = sig;
            var wet = GVerb.ar(sig, roomsize, revtime, damping,
                spread: spread, drylevel: 0, mul: 0.3);
            wet = wet[0] + wet[1] * 0.5;  // Mix stereo to mono
            ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
        }).add;
    });
}

*registerDefaultEffects {
    // Register all default effects with metadata
    ClipEffectRegistry.register(\bypass, (
        displayName: "Bypass",
        category: \utility,
        parameters: Dictionary.new
    ));

    ClipEffectRegistry.register(\channelReverb, (
        displayName: "Reverb (FreeVerb)",
        category: \reverb,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
            room: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            damp: ControlSpec(0, 1, \lin, 0.01, 0.5, "")
        )
    ));

    ClipEffectRegistry.register(\channelDelay, (
        displayName: "Delay (Comb)",
        category: \delay,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
            delayTime: ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"),
            decayTime: ControlSpec(0.1, 10, \exp, 0.1, 2, "s")
        )
    ));

    ClipEffectRegistry.register(\channelDistortion, (
        displayName: "Distortion (Soft)",
        category: \distortion,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
            drive: ControlSpec(0, 1, \lin, 0.01, 0.5, "")
        )
    ));

    ClipEffectRegistry.register(\channelBoum, (
        displayName: "Compressor (Boum)",
        category: \dynamics,
        parameters: (
            thresh: ControlSpec(-60, 0, \lin, 0.1, -12, "dB"),
            ratio: ControlSpec(1, 20, \lin, 0.1, 3, ":1"),
            attack: ControlSpec(0.001, 0.1, \exp, 0.001, 0.01, "s"),
            release: ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"),
            drive: ControlSpec(0, 24, \lin, 0.1, 0, "dB"),
            type: ControlSpec(0, 3, \lin, 1, 0, ""),
            hicut: ControlSpec(200, 20000, \exp, 1, 20000, "Hz"),
            gateThresh: ControlSpec(-80, -20, \lin, 0.1, -60, "dB"),
            makeupGain: ControlSpec(0, 24, \lin, 0.1, 0, "dB"),
            mix: ControlSpec(0, 1, \lin, 0.01, 1, "")
        )
    ));

    // [Register all other new effects...]
}
```

---

### New Class: `ClipChannelEffectsGUI`

GUI for channel effect selection:

```supercollider
ClipChannelEffectsGUI {
    var <clip;
    var <window;
    var <effectDropdowns;  // Dictionary: [channelIndex, slotIndex] -> PopUpMenu
    var <numChannels = 4;  // Audio channels only
    var <numSlots = 3;

    *new { |clipInstance|
        ^super.newCopyArgs(clipInstance).init;
    }

    init {
        effectDropdowns = Dictionary.new;
    }

    show {
        if (window.notNil and: { window.isClosed.not }, {
            window.front;
            ^this;
        });

        this.createWindow;
    }

    createWindow {
        var layout;

        window = Window("SC-Clip Channel Effects", Rect(100, 100, 900, 500))
            .background_(Color.gray(0.08))
            .onClose_({
                "Channel Effects GUI closed".postln;
            });

        window.view.palette = ClipMasteringGUI.palette;

        layout = this.createEffectTable;
        window.layout = VLayout(
            this.createHeader,
            layout
        ).margins_(20).spacing_(20);

        window.front;
    }

    createHeader {
        ^StaticText()
            .string_("Channel Effect Chains (↑ signal flow)")
            .font_(Font("Helvetica-Bold", 16))
            .stringColor_(Color.white)
            .align_(\center)
            .fixedHeight_(40);
    }

    createEffectTable {
        var table = GridLayout();
        var effectNames = [\bypass] ++ ClipEffectRegistry.allNames;
        var effectLabels = effectNames.collect { |name|
            if (name == \bypass, {
                "Bypass"
            }, {
                ClipEffectRegistry.get(name)[\displayName] ? name.asString
            });
        };

        // Column headers (channel labels)
        table.setAlignment(0, \center);
        numChannels.do { |chanIdx|
            var label = StaticText()
                .string_("Channel " ++ (chanIdx + 1))
                .font_(Font("Helvetica-Bold", 14))
                .stringColor_(Color.white)
                .align_(\center);
            table.add(label, 0, chanIdx + 1);  // +1 for row label column
        };

        // Effect slots (row 2 = slot 2, row 1 = slot 1, row 0 = slot 0)
        // Reverse order so bottom is first in chain
        (numSlots - 1).reverseDo { |slotIdx|
            var rowLabel = StaticText()
                .string_("Slot " ++ slotIdx ++ if (slotIdx == 0, " (First)",
                    if (slotIdx == (numSlots - 1), " (Last)", "")))
                .font_(Font("Helvetica", 12))
                .stringColor_(Color.gray(0.7))
                .align_(\right);

            table.add(rowLabel, (numSlots - slotIdx), 0);

            numChannels.do { |chanIdx|
                var dropdown = PopUpMenu()
                    .items_(effectLabels)
                    .fixedHeight_(30)
                    .action_({ |menu|
                        this.changeEffect(chanIdx, slotIdx,
                            effectNames[menu.value]);
                    });

                effectDropdowns[[chanIdx, slotIdx]] = dropdown;
                table.add(dropdown, (numSlots - slotIdx), chanIdx + 1);

                // Set current effect
                this.syncDropdown(chanIdx, slotIdx);
            };
        };

        ^table;
    }

    changeEffect { |channelIndex, slotIndex, effectName|
        var channel = clip.grid.getChannel(channelIndex);

        if (effectName == \bypass, {
            channel.removeEffect(slotIndex);
            "Channel %: Slot % bypassed".format(channelIndex, slotIndex).postln;
        }, {
            var meta = ClipEffectRegistry.get(effectName);
            var defaultArgs = [];

            // Build default args from parameter specs
            meta[\parameters].keysValuesDo { |param, spec|
                defaultArgs = defaultArgs ++ [param, spec.default];
            };

            channel.addEffect(effectName, defaultArgs, slotIndex);
            "Channel %: Slot % set to %".format(
                channelIndex, slotIndex, meta[\displayName]).postln;
        });
    }

    syncDropdown { |channelIndex, slotIndex|
        var channel = clip.grid.getChannel(channelIndex);
        var currentEffect = channel.getEffect(slotIndex);
        var dropdown = effectDropdowns[[channelIndex, slotIndex]];
        var effectNames = [\bypass] ++ ClipEffectRegistry.allNames;

        if (currentEffect.isNil, {
            dropdown.value_(0);  // Bypass
        }, {
            // Try to determine which effect is running
            // (This requires tracking effect names in ClipChannel)
            dropdown.value_(0);  // Default to bypass for now
        });
    }

    close {
        window.close;
    }
}
```

---

### Modified Class: `ClipChannel`

Track effect names for GUI sync:

```supercollider
var <effectNames;  // Dictionary: slot -> synthDefName symbol

init { |masterChannel|
    // [existing code...]
    effectNames = Dictionary.new;
}

addEffect { |synthDef, args, slot = 0|
    if (effects[slot].notNil, { this.removeEffect(slot) });
    effects[slot] = mixerChannel.playfx(synthDef, args);
    effectNames[slot] = synthDef;  // Track name

    "ClipChannel[%]: Added effect % at slot %".format(
        channelIndex, synthDef, slot).postln;
}

removeEffect { |slot|
    var running = effects[slot];
    if (running.notNil, {
        running.free;
        effects.removeAt(slot);
        effectNames.removeAt(slot);  // Clear name
        "ClipChannel[%]: Removed effect at slot %".format(
            channelIndex, slot).postln;
    });
}

getEffectName { |slot|
    ^effectNames[slot];
}
```

---

### Modified Class: `SCClip`

Add convenience method to open GUI:

```supercollider
showChannelEffectsGUI {
    var gui = ClipChannelEffectsGUI(this);
    gui.show;
    ^gui;
}
```

Session save/load:

```supercollider
// In asSessionData()
data[\channels] = grid.channels.collect { |channel, chanIdx|
    if (channel.isKindOf(ClipMIDIChannel), {
        // [existing MIDI channel code...]
    }, {
        Dictionary[
            \level -> channel.mixerChannel.level,
            \pan -> channel.mixerChannel.pan,
            \isMuted -> channel.mixerChannel.muted,
            \isSoloed -> channel.isSoloed,
            \hardwareInputIndex -> channel.hardwareInputIndex,
            \effects -> [0, 1, 2].collect { |slotIdx|  // NEW
                var effectName = channel.getEffectName(slotIdx);
                if (effectName.notNil, {
                    var synth = channel.getEffect(slotIdx);
                    var meta = ClipEffectRegistry.get(effectName);
                    var params = Dictionary.new;

                    // Save current parameter values
                    // (Requires extending ClipChannel to track param values)

                    (name: effectName, params: params)
                }, {
                    nil
                });
            },
            \slots -> channel.slots.collect { ... }
        ]
    });
};

// In restoreChannelsAndSlots()
channelsData.do { |channelData, chanIdx|
    var channel = grid.getChannel(chanIdx);

    if (channelData[\type] != \midi, {
        // [existing restoration code...]

        // Restore effects
        if (channelData[\effects].notNil, {
            channelData[\effects].do { |effectData, slotIdx|
                if (effectData.notNil, {
                    var meta = ClipEffectRegistry.get(effectData[\name]);
                    var args = [];

                    // Build args from saved params
                    effectData[\params].keysValuesDo { |param, value|
                        args = args ++ [param, value];
                    };

                    channel.addEffect(effectData[\name], args, slotIdx);
                });
            };
        });
    });
};
```

---

## Implementation Steps

### Phase 1: Core Infrastructure
1. Create `ClipEffectRegistry` class
2. Add new effect SynthDefs to `ClipSynthDefs`
3. Add effect registration in `ClipSynthDefs.registerDefaultEffects()`
4. Call registration in `ClipSynthDefs.initClass` StartUp
5. Modify `ClipChannel` to track effect names

### Phase 2: GUI
6. Create `ClipChannelEffectsGUI` class
7. Implement effect table layout
8. Wire up dropdown actions
9. Test effect hot-swapping

### Phase 3: Session Management
10. Extend `ClipChannel` to track effect parameters
11. Update `SCClip.asSessionData()` to save effect chains
12. Update `SCClip.restoreChannelsAndSlots()` to restore effects
13. Test session save/load

### Phase 4: Testing & Documentation
14. Create `Tests/test_channel_effects.scd`
15. Create `Examples/channel_effects_example.scd`
16. Update documentation

---

## Testing Strategy

### Test Cases (`Tests/test_channel_effects.scd`)

1. **Effect registry**: Verify all effects are registered
2. **Effect swapping**: Change effects in all 3 slots
3. **Bypass**: Set slot to bypass (nil)
4. **Multiple channels**: Configure different effects on different channels
5. **Session save/load**: Verify effect chains persist
6. **Parameter control**: Test `setEffectParam()` on running effects
7. **GUI sync**: Verify dropdowns reflect current state
8. **CPU usage**: Monitor CPU with all effects active

---

## Example Use Cases (`Examples/channel_effects_example.scd`)

1. **Vocal chain**: Channel 0 → Compressor → EQ → Reverb
2. **Drum processing**: Channel 1 → Distortion → Chorus → Delay
3. **Bass treatment**: Channel 2 → HPF → Compressor → Bypass
4. **Synth modulation**: Channel 3 → Phaser → Flanger → Reverb
5. **Live effect swapping**: Hot-swap delay → chorus during performance
6. **Session workflow**: Save complex effect chains, reload later

---

## Performance Considerations

### CPU Budget
Target: **Core M laptop** (~2 cores, low power)

**Estimated CPU per effect** (mono, 48kHz):
- Bypass: 0% (no synth)
- FreeVerb: 2-3%
- Simple delay: 1-2%
- Distortion: <1%
- Compressor: 1-2%
- Boum: 3-4%
- Chorus: 2-3%
- Phaser: 2-3%
- Flanger: 2-3%
- Filters: <1%

**Worst case**: 4 channels × 3 slots × 4% = **48% CPU**
**Typical use**: 4 channels × 2 active slots × 2.5% = **20% CPU**

Should be manageable on Core M with headroom for clips + transport.

---

## Future Enhancements

1. **Parameter UI**: Expandable sections per effect with sliders
2. **Stereo effects**: Upgrade channels to stereo signal path
3. **Effect presets**: Save/load effect parameter presets
4. **User effect registration**: Allow users to register custom SynthDefs
5. **Effect categories in GUI**: Organize dropdown by category (reverb, delay, etc.)
6. **Visual feedback**: Show effect activity (meters, lights)
7. **MIDI mapping**: Map MIDI CCs to effect parameters
8. **sc3-plugins integration**: Auto-detect and register sc3-plugins effects

---

## Summary

This implementation adds flexible, hot-swappable effect chains to sc-clip channels with:
- **14 effects** (4 existing + 10 new + bypass)
- **Clean GUI** with effect table visualization
- **Effect registry** for extensibility
- **Session persistence** for workflow
- **CPU-efficient** design suitable for Core M laptop

The architecture maintains sc-clip's existing slot system while exposing it through an intuitive GUI that follows the established `ClipMasteringGUI` pattern.

---

## Sources

Research for this plan:
- [Tour of UGens](https://doc.sccode.org/Guides/Tour_of_UGens.html) - SuperCollider built-in UGen reference
- [SuperCollider sc3-plugins](https://supercollider.github.io/sc3-plugins/) - Community plugin collection
- [Simple Phaser Effect](http://superdupercollider.blogspot.com/2009/05/simple-phaser-effect.html) - Implementation guide
- [Chorus, Flanger and Phaser Effects Explained](https://www.masteringbox.com/learn/chorus-flanger-and-phaser) - Effect theory
- [Time Domain Effects](https://thormagnusson.gitbooks.io/scoring/content/PartIII/chapter12.html) - SuperCollider effects implementation
