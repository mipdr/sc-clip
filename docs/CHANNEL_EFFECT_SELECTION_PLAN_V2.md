# Channel Effect Selection Implementation Plan v2

**Status**: Approved with user clarifications

## Overview

Replace hardcoded channel effects (reverb, delay, distortion) with a flexible effect selection system that allows hot-swapping effects in three slots per channel via GUI dropdowns integrated into the existing mastering GUI.

---

## User Decisions (Confirmed)

1. ✅ **GUI Layout**: Option B - Integrate into existing `ClipMasteringGUI` window
2. ✅ **Table Orientation**: Option A - Vertical layout (bottom-to-top signal flow)
3. ✅ **Channels**: Audio channels only (4 channels)
4. ✅ **Parameter Control**: Effect selection only (dropdowns), with single MIDI knob control per effect
5. ✅ **Default Parameters**: Use good-sounding defaults, choose most musical parameter as single control
6. ✅ **Session Save/Load**: Full support for effect chains
7. ✅ **Effect Registration**: Declare parameter configurability (which param is the "main" control)
8. ✅ **Bypass**: Null effect (nil, no synth)
9. ✅ **Signal Path**: Keep all mono (no stereo effects)

---

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

### Current MIDI Control Pattern (TR8s Integration)

From `ClipTR8s4Channel.sc`:
- TR8s knobs send CC messages
- Each knob controls a single parameter
- Current mapping: Reverb knob, Delay knob, Distortion knob
- Need to extend this to work with any selected effect's "main" parameter

---

## Effect Library (Mono Only)

All effects process mono signals. No stereo effects in this implementation.

### Category: Reverb

#### 1. **FreeVerb** (existing as `\channelReverb`)
- **Type**: Basic algorithmic reverb
- **Parameters**: mix, room, damp
- **Main Control**: `mix` (dry/wet balance)
- **Defaults**: mix=0.3, room=0.5, damp=0.5
- **CPU**: Very light (~2-3%)
- **Use case**: General-purpose reverb

#### 2. **GVerb** (new, optional)
- **Type**: Granular reverb (requires sc3-plugins)
- **Parameters**: mix, roomsize, revtime, damping, spread
- **Main Control**: `mix` (dry/wet balance)
- **Defaults**: mix=0.3, roomsize=10, revtime=3, damping=0.5, spread=15
- **CPU**: Moderate (~4-5%)
- **Use case**: Richer, more diffuse reverb
- **Note**: Only register if sc3-plugins available

---

### Category: Delay

#### 3. **Simple Delay** (existing as `\channelDelay`)
- **Type**: CombN comb filter delay
- **Parameters**: mix, delayTime, decayTime
- **Main Control**: `mix` (dry/wet balance)
- **Defaults**: mix=0.3, delayTime=0.3, decayTime=2
- **CPU**: Very light (~1-2%)
- **Use case**: Echo, slapback

#### 4. **Tape Delay** (new)
- **Type**: Variable-speed delay with modulation
- **Parameters**: mix, delayTime, feedback, wobble
- **Main Control**: `mix` (dry/wet balance)
- **Defaults**: mix=0.3, delayTime=0.375, feedback=0.6, wobble=0.002
- **CPU**: Light (~2%)
- **Use case**: Vintage tape echo simulation

---

### Category: Distortion/Saturation

#### 5. **Soft Distortion** (existing as `\channelDistortion`)
- **Type**: tanh waveshaping
- **Parameters**: mix, drive
- **Main Control**: `drive` (distortion amount)
- **Defaults**: mix=1.0, drive=0.5
- **CPU**: Very light (<1%)
- **Use case**: Warm saturation

#### 6. **Hard Clip** (new)
- **Type**: clip2 hard clipping
- **Parameters**: mix, drive, bias
- **Main Control**: `drive` (clipping threshold)
- **Defaults**: mix=1.0, drive=0.5, bias=0
- **CPU**: Very light (<1%)
- **Use case**: Aggressive distortion

---

### Category: Modulation

#### 7. **Chorus** (new)
- **Type**: Modulated delay (20-30ms)
- **Parameters**: mix, rate, depth, voices
- **Main Control**: `depth` (modulation intensity)
- **Defaults**: mix=0.5, rate=0.5, depth=0.01, voices=4
- **CPU**: Light (~2-3%)
- **Use case**: Thickening, doubling

#### 8. **Phaser** (new)
- **Type**: Allpass filter modulation
- **Parameters**: mix, rate, depth, stages
- **Main Control**: `depth` (sweep intensity)
- **Defaults**: mix=0.5, rate=0.3, depth=0.5, stages=4
- **CPU**: Light (~2-3%)
- **Use case**: Phasing sweep

#### 9. **Flanger** (new)
- **Type**: Short modulated delay (<10ms)
- **Parameters**: mix, rate, depth, feedback
- **Main Control**: `depth` (sweep range)
- **Defaults**: mix=0.5, rate=0.2, depth=0.005, feedback=0.5
- **CPU**: Light (~2-3%)
- **Use case**: Jet-plane whoosh

---

### Category: Filter

#### 10. **Low-Pass Filter** (new)
- **Type**: Resonant low-pass filter
- **Parameters**: freq, resonance, mix
- **Main Control**: `freq` (cutoff frequency)
- **Defaults**: freq=1000, resonance=0.5, mix=1.0
- **CPU**: Very light (<1%)
- **Use case**: Synth-style filtering, remove highs

#### 11. **High-Pass Filter** (new)
- **Type**: Resonant high-pass filter
- **Parameters**: freq, resonance, mix
- **Main Control**: `freq` (cutoff frequency)
- **Defaults**: freq=200, resonance=0.5, mix=1.0
- **CPU**: Very light (<1%)
- **Use case**: Remove low-end

#### 12. **Band-Pass Filter** (new)
- **Type**: Resonant band-pass filter
- **Parameters**: freq, resonance, mix
- **Main Control**: `freq` (center frequency)
- **Defaults**: freq=1000, resonance=0.5, mix=1.0
- **CPU**: Very light (<1%)
- **Use case**: Telephone/radio effect, isolate frequency range

---

### Category: Dynamics

#### 13. **Compressor (Boum)** (existing as `\channelBoum`)
- **Type**: Compressor + distortion + gate + hi-cut
- **Parameters**: thresh, ratio, attack, release, drive, type, hicut, gateThresh, makeupGain, mix
- **Main Control**: `thresh` (compression threshold)
- **Defaults**: thresh=-12, ratio=3, attack=0.01, release=0.3, drive=0, type=0, hicut=20000, gateThresh=-60, makeupGain=0, mix=1.0
- **CPU**: Moderate (~3-4%)
- **Use case**: Compression with color

#### 14. **Simple Compressor** (new)
- **Type**: Basic Compander
- **Parameters**: thresh, ratio, attack, release, mix
- **Main Control**: `thresh` (compression threshold)
- **Defaults**: thresh=-12, ratio=3, attack=0.01, release=0.3, mix=1.0
- **CPU**: Light (~1-2%)
- **Use case**: Transparent compression

---

### Category: Utility

#### 15. **Bypass** (null effect)
- **Type**: No processing
- **Parameters**: None
- **Main Control**: None
- **CPU**: None (no synth)
- **Use case**: Disable slot

---

**Total**: 15 effects (4 existing + 11 new)

---

## Single Control Parameter Design

### Main Control Philosophy

Each effect designates one parameter as the "main control" for MIDI knob mapping:

1. **Mix-based effects** (reverb, delay, modulation): Use `mix` as main control
   - Most intuitive: 0 = dry, 1 = wet
   - Allows blending effect intensity

2. **Drive-based effects** (distortion): Use `drive` as main control
   - Directly controls effect intensity
   - Mix often stays at 100%

3. **Frequency-based effects** (filters): Use `freq` as main control
   - Most musical parameter to sweep
   - Resonance stays relatively constant

4. **Depth-based effects** (modulation): Use `depth` as main control
   - Controls modulation intensity
   - Rate stays relatively constant

5. **Threshold-based effects** (dynamics): Use `thresh` as main control
   - Most critical compression parameter
   - Ratio stays relatively constant

### Effect Registry Metadata

Each effect registered with:
```supercollider
ClipEffectRegistry.register(\effectName, (
    displayName: "Effect Name",
    category: \category,
    mainControl: \paramName,  // NEW: which param for MIDI knob
    parameters: (
        paramName: ControlSpec(...),
        ...
    )
));
```

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
        //     mainControl: \mix,  // NEW
        //     parameters: Dictionary of ControlSpecs
        // )
        effects[synthDefName] = metadata;

        "ClipEffectRegistry: Registered %".format(metadata[\displayName]).postln;
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

    *getMainControl { |synthDefName|
        var meta = effects[synthDefName];
        ^meta !? { meta[\mainControl] };
    }

    *getMainControlSpec { |synthDefName|
        var meta = effects[synthDefName];
        var mainParam = meta !? { meta[\mainControl] };
        ^if (mainParam.notNil, {
            meta[\parameters][mainParam];
        }, {
            nil
        });
    }
}
```

---

### Modified Class: `ClipSynthDefs`

Add new effect SynthDefs:

```supercollider
*addChannelEffects {
    // EXISTING EFFECTS (keep as-is)

    SynthDef(\channelReverb, { |out, mix=0.3, room=0.5, damp=0.5|
        var sig = In.ar(out, 1);
        var verb = FreeVerb.ar(sig, mix, room, damp);
        ReplaceOut.ar(out, verb);
    }).add;

    SynthDef(\channelDelay, { |out, mix=0.3, delayTime=0.3, decayTime=2|
        var sig = In.ar(out, 1);
        var delayed = CombN.ar(sig, 2, delayTime, decayTime, 1 - mix, sig * mix);
        ReplaceOut.ar(out, delayed);
    }).add;

    SynthDef(\channelDistortion, { |out, mix=1.0, drive=0.5|
        var sig = In.ar(out, 1);
        var dist = (sig * (1 + (drive * 10))).tanh;
        var wet = dist * 0.5;
        ReplaceOut.ar(out, (sig * (1 - mix)) + (wet * mix));
    }).add;

    // [channelBoum already exists]

    // NEW EFFECTS

    // Tape Delay
    SynthDef(\channelTapeDelay, { |out, mix=0.3, delayTime=0.375, feedback=0.6, wobble=0.002|
        var sig = In.ar(out, 1);
        var dry = sig;
        var modFreq = LFNoise1.kr(0.2).range(0.001, wobble);
        var delayTimeMod = delayTime * (1 + SinOsc.kr(1.3, 0, modFreq));
        var delayed = LocalIn.ar(1);
        delayed = DelayC.ar(sig + (delayed * feedback), 2, delayTimeMod.clip(0.001, 1.999));
        delayed = LPF.ar(delayed, 8000);  // Tape-like filtering
        LocalOut.ar(delayed);
        ReplaceOut.ar(out, (dry * (1 - mix)) + (delayed * mix));
    }).add;

    // Hard Clip
    SynthDef(\channelHardClip, { |out, mix=1.0, drive=0.5, bias=0|
        var sig = In.ar(out, 1);
        var dry = sig;
        var driven = (sig * (1 + (drive * 10)) + bias).clip2(0.8) * 0.8;
        ReplaceOut.ar(out, (dry * (1 - mix)) + (driven * mix));
    }).add;

    // Chorus
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

    // Phaser
    SynthDef(\channelPhaser, { |out, mix=0.5, rate=0.3, depth=0.5, stages=4|
        var sig = In.ar(out, 1);
        var dry = sig;
        var mod = SinOsc.kr(rate).range(0.0001, 0.01 * depth);
        var wet = sig;
        stages.do {
            wet = AllpassN.ar(wet, 0.02, mod);
        };
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // Flanger
    SynthDef(\channelFlanger, { |out, mix=0.5, rate=0.2, depth=0.005, feedback=0.5|
        var sig = In.ar(out, 1);
        var dry = sig;
        var mod = SinOsc.kr(rate).range(0.001, 0.001 + depth);
        var delayed = LocalIn.ar(1);
        delayed = DelayC.ar(sig + (delayed * feedback), 0.02, mod);
        LocalOut.ar(delayed);
        ReplaceOut.ar(out, (dry * (1 - mix)) + (delayed * mix));
    }).add;

    // Low-Pass Filter
    SynthDef(\channelLowPass, { |out, freq=1000, resonance=0.5, mix=1.0|
        var sig = In.ar(out, 1);
        var dry = sig;
        var wet = RLPF.ar(sig, freq.clip(20, 20000), 1 - (resonance * 0.9));
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // High-Pass Filter
    SynthDef(\channelHighPass, { |out, freq=200, resonance=0.5, mix=1.0|
        var sig = In.ar(out, 1);
        var dry = sig;
        var wet = RHPF.ar(sig, freq.clip(20, 20000), 1 - (resonance * 0.9));
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // Band-Pass Filter
    SynthDef(\channelBandPass, { |out, freq=1000, resonance=0.5, mix=1.0|
        var sig = In.ar(out, 1);
        var dry = sig;
        var wet = BPF.ar(sig, freq.clip(20, 20000), 1 - (resonance * 0.9));
        ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
    }).add;

    // Simple Compressor
    SynthDef(\channelCompressor, { |out, thresh= -12, ratio=3, attack=0.01, release=0.3, mix=1.0|
        var sig = In.ar(out, 1);
        var dry = sig;
        var wet = Compander.ar(sig, sig,
            thresh.dbamp, 1, 1/ratio, attack, release);
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
    // Bypass
    ClipEffectRegistry.register(\bypass, (
        displayName: "Bypass",
        category: \utility,
        mainControl: nil,
        parameters: Dictionary.new
    ));

    // Reverb: FreeVerb
    ClipEffectRegistry.register(\channelReverb, (
        displayName: "Reverb (FreeVerb)",
        category: \reverb,
        mainControl: \mix,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
            room: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            damp: ControlSpec(0, 1, \lin, 0.01, 0.5, "")
        )
    ));

    // Reverb: GVerb (if available)
    if (GVerb.respondsTo(\ar), {
        ClipEffectRegistry.register(\channelGVerb, (
            displayName: "Reverb (GVerb)",
            category: \reverb,
            mainControl: \mix,
            parameters: (
                mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
                roomsize: ControlSpec(1, 300, \exp, 1, 10, "m"),
                revtime: ControlSpec(0.1, 20, \exp, 0.1, 3, "s"),
                damping: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
                spread: ControlSpec(0, 50, \lin, 1, 15, "")
            )
        ));
    });

    // Delay: Simple Delay
    ClipEffectRegistry.register(\channelDelay, (
        displayName: "Delay (Comb)",
        category: \delay,
        mainControl: \mix,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
            delayTime: ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"),
            decayTime: ControlSpec(0.1, 10, \exp, 0.1, 2, "s")
        )
    ));

    // Delay: Tape Delay
    ClipEffectRegistry.register(\channelTapeDelay, (
        displayName: "Delay (Tape)",
        category: \delay,
        mainControl: \mix,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
            delayTime: ControlSpec(0.01, 2, \exp, 0.01, 0.375, "s"),
            feedback: ControlSpec(0, 0.95, \lin, 0.01, 0.6, ""),
            wobble: ControlSpec(0, 0.01, \lin, 0.0001, 0.002, "")
        )
    ));

    // Distortion: Soft
    ClipEffectRegistry.register(\channelDistortion, (
        displayName: "Distortion (Soft)",
        category: \distortion,
        mainControl: \drive,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 1.0, ""),
            drive: ControlSpec(0, 1, \lin, 0.01, 0.5, "")
        )
    ));

    // Distortion: Hard Clip
    ClipEffectRegistry.register(\channelHardClip, (
        displayName: "Distortion (Hard)",
        category: \distortion,
        mainControl: \drive,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 1.0, ""),
            drive: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            bias: ControlSpec(-0.5, 0.5, \lin, 0.01, 0, "")
        )
    ));

    // Modulation: Chorus
    ClipEffectRegistry.register(\channelChorus, (
        displayName: "Chorus",
        category: \modulation,
        mainControl: \depth,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            rate: ControlSpec(0.1, 5, \exp, 0.01, 0.5, "Hz"),
            depth: ControlSpec(0.001, 0.05, \exp, 0.001, 0.01, ""),
            voices: ControlSpec(2, 8, \lin, 1, 4, "")
        )
    ));

    // Modulation: Phaser
    ClipEffectRegistry.register(\channelPhaser, (
        displayName: "Phaser",
        category: \modulation,
        mainControl: \depth,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            rate: ControlSpec(0.1, 5, \exp, 0.01, 0.3, "Hz"),
            depth: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            stages: ControlSpec(2, 12, \lin, 1, 4, "")
        )
    ));

    // Modulation: Flanger
    ClipEffectRegistry.register(\channelFlanger, (
        displayName: "Flanger",
        category: \modulation,
        mainControl: \depth,
        parameters: (
            mix: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            rate: ControlSpec(0.1, 5, \exp, 0.01, 0.2, "Hz"),
            depth: ControlSpec(0.001, 0.01, \exp, 0.0001, 0.005, ""),
            feedback: ControlSpec(0, 0.95, \lin, 0.01, 0.5, "")
        )
    ));

    // Filter: Low-Pass
    ClipEffectRegistry.register(\channelLowPass, (
        displayName: "Filter (Low-Pass)",
        category: \filter,
        mainControl: \freq,
        parameters: (
            freq: ControlSpec(20, 20000, \exp, 1, 1000, "Hz"),
            resonance: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            mix: ControlSpec(0, 1, \lin, 0.01, 1.0, "")
        )
    ));

    // Filter: High-Pass
    ClipEffectRegistry.register(\channelHighPass, (
        displayName: "Filter (High-Pass)",
        category: \filter,
        mainControl: \freq,
        parameters: (
            freq: ControlSpec(20, 20000, \exp, 1, 200, "Hz"),
            resonance: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            mix: ControlSpec(0, 1, \lin, 0.01, 1.0, "")
        )
    ));

    // Filter: Band-Pass
    ClipEffectRegistry.register(\channelBandPass, (
        displayName: "Filter (Band-Pass)",
        category: \filter,
        mainControl: \freq,
        parameters: (
            freq: ControlSpec(20, 20000, \exp, 1, 1000, "Hz"),
            resonance: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
            mix: ControlSpec(0, 1, \lin, 0.01, 1.0, "")
        )
    ));

    // Dynamics: Boum
    ClipEffectRegistry.register(\channelBoum, (
        displayName: "Compressor (Boum)",
        category: \dynamics,
        mainControl: \thresh,
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

    // Dynamics: Simple Compressor
    ClipEffectRegistry.register(\channelCompressor, (
        displayName: "Compressor (Simple)",
        category: \dynamics,
        mainControl: \thresh,
        parameters: (
            thresh: ControlSpec(-60, 0, \lin, 0.1, -12, "dB"),
            ratio: ControlSpec(1, 20, \lin, 0.1, 3, ":1"),
            attack: ControlSpec(0.001, 0.1, \exp, 0.001, 0.01, "s"),
            release: ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"),
            mix: ControlSpec(0, 1, \lin, 0.01, 1.0, "")
        )
    ));

    "ClipEffectRegistry: % effects registered".format(ClipEffectRegistry.all.size).postln;
}
```

---

### Modified Class: `ClipChannel`

Track effect names and main control values:

```supercollider
var <effectNames;       // Dictionary: slot -> synthDefName symbol
var <effectMainValues;  // Dictionary: slot -> current main control value

init { |masterChannel|
    // [existing code...]
    effectNames = Dictionary.new;
    effectMainValues = Dictionary.new;
}

addEffect { |synthDef, args, slot = 0|
    if (effects[slot].notNil, { this.removeEffect(slot) });
    effects[slot] = mixerChannel.playfx(synthDef, args);
    effectNames[slot] = synthDef;

    // Initialize main control value from args or default
    var meta = ClipEffectRegistry.get(synthDef);
    var mainParam = meta !? { meta[\mainControl] };
    if (mainParam.notNil, {
        var spec = meta[\parameters][mainParam];
        var argIndex = args.indexOf(mainParam);
        var value = if (argIndex.notNil, {
            args[argIndex + 1];
        }, {
            spec.default;
        });
        effectMainValues[slot] = value;
    });

    "ClipChannel[%]: Added effect % at slot %".format(
        channelIndex, synthDef, slot).postln;
}

removeEffect { |slot|
    var running = effects[slot];
    if (running.notNil, {
        running.free;
        effects.removeAt(slot);
        effectNames.removeAt(slot);
        effectMainValues.removeAt(slot);
        "ClipChannel[%]: Removed effect at slot %".format(
            channelIndex, slot).postln;
    });
}

getEffectName { |slot|
    ^effectNames[slot];
}

getEffectMainValue { |slot|
    ^effectMainValues[slot];
}

// Enhanced setEffectParam to track main control
setEffectParam { |slot, param, value|
    var synth = effects[slot];
    if (synth.notNil, {
        synth.set(param, value);

        // If this is the main control, track it
        var effectName = effectNames[slot];
        if (effectName.notNil, {
            var mainParam = ClipEffectRegistry.getMainControl(effectName);
            if (param == mainParam, {
                effectMainValues[slot] = value;
            });
        });

        "ClipChannel[%]: Set effect % param % to %".format(
            channelIndex, slot, param, value).postln;
    }, {
        "ClipChannel[%]: No effect at slot %".format(channelIndex, slot).warn;
    });
}

// NEW: Set effect's main control (for MIDI knob)
setEffectMainControl { |slot, value|
    var effectName = effectNames[slot];
    if (effectName.isNil, {
        "ClipChannel[%]: No effect at slot %".format(channelIndex, slot).warn;
        ^this;
    });

    var mainParam = ClipEffectRegistry.getMainControl(effectName);
    if (mainParam.isNil, {
        "ClipChannel[%]: Effect % has no main control".format(
            channelIndex, effectName).warn;
        ^this;
    });

    this.setEffectParam(slot, mainParam, value);
}
```

---

### Modified Class: `ClipMasteringGUI`

Integrate channel effects section into existing GUI:

```supercollider
ClipMasteringGUI {
    var <clip;
    var <window;
    var controlViews;
    var <channelEffectDropdowns;  // NEW: Dictionary: [chanIdx, slotIdx] -> PopUpMenu
    var <numAudioChannels = 4;
    var <numEffectSlots = 3;

    // [existing methods...]

    createWindow {
        var masterSection, eqSection, compressorSection, limiterSection;
        var channelEffectsSection;  // NEW

        // [existing window setup...]

        masterSection = this.masterControls;
        eqSection = this.eqControls;
        compressorSection = this.compressorControls;
        limiterSection = this.limiterControls;
        channelEffectsSection = this.channelEffectsControls;  // NEW

        window.layout = VLayout(
            this.createHeader("SC-CLIP MASTERING", 20),
            HLayout(
                VLayout(
                    masterSection,
                    eqSection,
                    compressorSection,
                    limiterSection
                ).spacing_(20),
                VLayout(
                    channelEffectsSection  // NEW: right side
                ).spacing_(20)
            ).spacing_(30)
        ).margins_(20).spacing_(20);

        this.applySettings;
        this.startMeters;

        window.front;
    }

    // NEW: Channel effects controls
    channelEffectsControls {
        var section = View()
            .background_(ClipMasteringGUI.panelColor);

        var table = GridLayout();
        var effectNames = [\bypass] ++ ClipEffectRegistry.allNames;
        var effectLabels = effectNames.collect { |name|
            if (name == \bypass, {
                "Bypass"
            }, {
                ClipEffectRegistry.get(name)[\displayName] ? name.asString
            });
        };

        channelEffectDropdowns = Dictionary.new;

        // Title
        table.add(
            StaticText()
                .string_("CHANNEL EFFECTS")
                .font_(Font("Helvetica-Bold", 16))
                .stringColor_(Color.white)
                .align_(\center),
            0, 0, 1, numAudioChannels + 1
        );

        // Subtitle (signal flow indicator)
        table.add(
            StaticText()
                .string_("(↑ signal flow: bottom to top)")
                .font_(Font("Helvetica", 10))
                .stringColor_(Color.gray(0.6))
                .align_(\center),
            1, 0, 1, numAudioChannels + 1
        );

        // Column headers (channel labels)
        numAudioChannels.do { |chanIdx|
            var label = StaticText()
                .string_("Ch " ++ (chanIdx + 1))
                .font_(Font("Helvetica-Bold", 12))
                .stringColor_(Color.white)
                .align_(\center);
            table.add(label, 2, chanIdx + 1);
        };

        // Effect slots (reverse order: bottom = first, top = last)
        (numEffectSlots - 1).reverseDo { |slotIdx|
            var rowLabel = StaticText()
                .string_(
                    "Slot " ++ slotIdx ++
                    if (slotIdx == 0, " →",
                        if (slotIdx == (numEffectSlots - 1), " ↑", " ↑"))
                )
                .font_(Font("Helvetica", 11))
                .stringColor_(Color.gray(0.7))
                .align_(\right);

            var gridRow = 3 + (numEffectSlots - 1 - slotIdx);
            table.add(rowLabel, gridRow, 0);

            numAudioChannels.do { |chanIdx|
                var dropdown = PopUpMenu()
                    .items_(effectLabels)
                    .font_(Font("Helvetica", 10))
                    .fixedHeight_(25)
                    .action_({ |menu|
                        this.changeChannelEffect(chanIdx, slotIdx,
                            effectNames[menu.value]);
                    });

                channelEffectDropdowns[[chanIdx, slotIdx]] = dropdown;
                table.add(dropdown, gridRow, chanIdx + 1);

                // Sync to current effect
                this.syncEffectDropdown(chanIdx, slotIdx);
            };
        };

        section.layout = VLayout(table).margins_(15).spacing_(8);

        ^section;
    }

    // NEW: Change channel effect
    changeChannelEffect { |channelIndex, slotIndex, effectName|
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

    // NEW: Sync dropdown to current effect
    syncEffectDropdown { |channelIndex, slotIndex|
        var channel = clip.grid.getChannel(channelIndex);
        var currentEffectName = channel.getEffectName(slotIndex);
        var dropdown = channelEffectDropdowns[[channelIndex, slotIndex]];
        var effectNames = [\bypass] ++ ClipEffectRegistry.allNames;

        if (currentEffectName.isNil, {
            dropdown.value_(0);  // Bypass
        }, {
            var index = effectNames.indexOf(currentEffectName);
            if (index.notNil, {
                dropdown.value_(index);
            }, {
                dropdown.value_(0);  // Bypass if not found
            });
        });
    }
}
```

---

### Modified Class: `SCClip`

Session save/load with full effect chain support:

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
                    var meta = ClipEffectRegistry.get(effectName);
                    var params = Dictionary.new;

                    // Save all parameter values
                    // For now, save main control value
                    var mainParam = meta[\mainControl];
                    if (mainParam.notNil, {
                        params[mainParam] = channel.getEffectMainValue(slotIdx);
                    });

                    (name: effectName, params: params)
                }, {
                    nil
                });
            },
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
    });
};

// In restoreChannelsAndSlots()
channelsData.do { |channelData, chanIdx|
    var channel = grid.getChannel(chanIdx);

    if (channelData[\type] != \midi, {
        // [existing restoration code for level, pan, mute, solo, input...]

        // Restore effects
        if (channelData[\effects].notNil, {
            channelData[\effects].do { |effectData, slotIdx|
                if (effectData.notNil, {
                    var meta = ClipEffectRegistry.get(effectData[\name]);
                    var args = [];

                    // Build args from saved params and defaults
                    meta[\parameters].keysValuesDo { |param, spec|
                        var value = effectData[\params][param] ? spec.default;
                        args = args ++ [param, value];
                    };

                    channel.addEffect(effectData[\name], args, slotIdx);

                    "Restored effect % at channel %, slot %".format(
                        effectData[\name], chanIdx, slotIdx).postln;
                });
            };
        });

        // [existing slot restoration code...]
    });
};
```

---

### Modified Class: `ClipTR8s4Channel` (MIDI Control Integration)

Extend to control selected effect's main parameter:

```supercollider
// Map TR8s knobs to effect slots
// Reverb knob -> Slot 0 main control
// Delay knob -> Slot 1 main control
// Distortion knob -> Slot 2 main control

handleCC { |src, chan, num, val|
    var normalized = val / 127.0;

    case
    // [existing master level, cue mix mappings...]

    // Effect Slot 0 (Reverb knob, CC 18)
    { num == 18 } {
        var channel = clip.grid.getChannel(channelIndex);
        var effectName = channel.getEffectName(0);
        if (effectName.notNil, {
            var spec = ClipEffectRegistry.getMainControlSpec(effectName);
            if (spec.notNil, {
                var mapped = spec.map(normalized);
                channel.setEffectMainControl(0, mapped);
                "TR8s: Channel % Effect Slot 0 -> %".format(
                    channelIndex, mapped).postln;
            });
        });
    }

    // Effect Slot 1 (Delay knob, CC 19)
    { num == 19 } {
        var channel = clip.grid.getChannel(channelIndex);
        var effectName = channel.getEffectName(1);
        if (effectName.notNil, {
            var spec = ClipEffectRegistry.getMainControlSpec(effectName);
            if (spec.notNil, {
                var mapped = spec.map(normalized);
                channel.setEffectMainControl(1, mapped);
                "TR8s: Channel % Effect Slot 1 -> %".format(
                    channelIndex, mapped).postln;
            });
        });
    }

    // Effect Slot 2 (Distortion knob, CC 20)
    { num == 20 } {
        var channel = clip.grid.getChannel(channelIndex);
        var effectName = channel.getEffectName(2);
        if (effectName.notNil, {
            var spec = ClipEffectRegistry.getMainControlSpec(effectName);
            if (spec.notNil, {
                var mapped = spec.map(normalized);
                channel.setEffectMainControl(2, mapped);
                "TR8s: Channel % Effect Slot 2 -> %".format(
                    channelIndex, mapped).postln;
            });
        });
    };
}
```

---

## Implementation Steps

### Phase 1: Core Infrastructure (Effects Registry)
1. Create `ClipEffectRegistry` class
2. Add new effect SynthDefs to `ClipSynthDefs.addChannelEffects()`
3. Implement `ClipSynthDefs.registerDefaultEffects()` with mainControl metadata
4. Call registration in `ClipSynthDefs` startup
5. Test effect registry (verify all effects registered)

### Phase 2: Channel Effect Tracking
6. Modify `ClipChannel` to track effect names and main control values
7. Implement `getEffectName()`, `getEffectMainValue()`, `setEffectMainControl()`
8. Test effect tracking (add/remove effects, verify tracking)

### Phase 3: GUI Integration
9. Modify `ClipMasteringGUI.createWindow()` to add channel effects section
10. Implement `channelEffectsControls()` with effect table
11. Implement `changeChannelEffect()` and `syncEffectDropdown()`
12. Test GUI (open mastering GUI, change effects via dropdowns)

### Phase 4: MIDI Control Integration
13. Modify `ClipTR8s4Channel.handleCC()` to control effect main parameters
14. Test MIDI control (TR8s knobs control selected effect's main param)

### Phase 5: Session Management
15. Update `SCClip.asSessionData()` to save effect chains
16. Update `SCClip.restoreChannelsAndSlots()` to restore effects
17. Test session save/load (save session with effects, reload, verify)

### Phase 6: Testing & Documentation
18. Create `Tests/test_channel_effects.scd`
19. Create `Examples/channel_effects_example.scd`
20. Update documentation

---

## Testing Strategy

### Test Cases (`Tests/test_channel_effects.scd`)

1. **Effect registry**: Verify 15 effects registered
2. **Effect metadata**: Verify all effects have mainControl specified
3. **Effect swapping**: Change effects in all 3 slots, verify synths created/freed
4. **Bypass**: Set slot to bypass (nil), verify synth freed
5. **Main control**: Use `setEffectMainControl()`, verify parameter updated
6. **Multiple channels**: Configure different effects on different channels
7. **Session save/load**: Verify effect chains persist
8. **GUI sync**: Open GUI, verify dropdowns reflect current state
9. **MIDI control**: Send CC, verify effect main param changes
10. **CPU usage**: Monitor CPU with all effects active (should be <25%)

---

## Example Use Cases (`Examples/channel_effects_example.scd`)

1. **Vocal chain**: Channel 0 → Compressor → High-Pass → Reverb
2. **Drum processing**: Channel 1 → Distortion → Chorus → Delay
3. **Bass treatment**: Channel 2 → Low-Pass → Simple Compressor → Bypass
4. **Synth modulation**: Channel 3 → Phaser → Flanger → Tape Delay
5. **Live effect swapping**: Hot-swap delay → chorus during performance
6. **MIDI knob control**: Control effect via TR8s knobs
7. **Session workflow**: Save complex effect chains, reload later

---

## Performance Analysis

### CPU Budget
Target: **Core M laptop** (~2 cores, low power)

**Effect CPU Usage** (mono, 48kHz):
- Bypass: 0%
- FreeVerb: 2-3%
- GVerb: 4-5% (optional)
- Delays: 1-2%
- Distortion: <1%
- Modulation: 2-3%
- Filters: <1%
- Compressors: 1-4%

**Realistic scenario** (4 channels, mixed effects):
- Channel 0: Compressor (2%) + HPF (0.5%) + Reverb (2.5%) = 5%
- Channel 1: Distortion (0.5%) + Chorus (2.5%) + Delay (1.5%) = 4.5%
- Channel 2: LPF (0.5%) + Compressor (2%) + Bypass (0%) = 2.5%
- Channel 3: Phaser (2.5%) + Flanger (2.5%) + Tape Delay (2%) = 7%

**Total**: ~19% CPU for channel effects

With clips, transport, master bus: **~35-40% total CPU** (comfortable on Core M)

---

## Summary

This implementation adds flexible, hot-swappable effect chains to sc-clip channels with:

✅ **15 effects** (4 existing + 11 new)
✅ **Integrated GUI** in `ClipMasteringGUI` with effect table
✅ **Single MIDI knob control** per effect (mainControl parameter)
✅ **Effect registry** with rich metadata
✅ **Session persistence** for effect chains
✅ **TR8s integration** (knobs control selected effect's main param)
✅ **Mono signal path** (no stereo complexity)
✅ **CPU-efficient** (~20% CPU typical use on Core M)

The architecture maintains sc-clip's existing slot system while exposing it through an intuitive GUI integrated into the mastering window, with full MIDI control support following the TR8s precedent.
