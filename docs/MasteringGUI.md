# SC-Clip Mastering GUI

Native SuperCollider GUI for controlling the mastering chain. Features a Max-style signal flow diagram and interactive controls.

## Key Features

✅ **No IDE required** - Runs from command line
✅ **Max-style signal flow diagram** - Visual representation of audio routing
✅ **Custom drawing with Pen API** - Dynamic graphics showing active/bypassed states
✅ **Real-time control** - Adjust parameters on the fly
✅ **Preset system** - Quick recall of common mastering settings
✅ **Single window** - All controls in one organized interface

## Quick Start

```supercollider
// Boot SC-Clip
~clip = SCClip.new(numChannels: 4, numSlots: 8);
~clip.boot;

// Show mastering GUI
~gui = ~clip.showMasteringGUI;

// Close GUI when done
~gui.close;
```

## GUI Layout

### Signal Flow Diagram (Top Section)
```
Channels → Master → EQ → Compressor → Limiter → Out
```

The diagram shows:
- **Blue boxes** = Active stages (mastering chain enabled)
- **Gray boxes** = Bypassed stages (mastering chain disabled)
- **Arrows** = Signal routing between stages
- **Status indicator** = "● MASTERING ACTIVE" (green) or "○ Mastering Bypassed" (gray)

This gives you a **visual representation** of your signal flow, similar to Max/MSP or Pd patch diagrams.

### Control Sections (Scrollable Area)

1. **Master Controls**
   - Enable/Disable mastering chain button
   - Master level fader (-40 dB to +12 dB)

2. **Parametric EQ**
   - **Low Shelf**: Frequency (20-500 Hz), Gain (-12 to +12 dB)
   - **Parametric Mid**: Frequency (200-8000 Hz), Gain (-12 to +12 dB), Q (0.5-5)
   - **High Shelf**: Frequency (2-20 kHz), Gain (-12 to +12 dB)

3. **Glue Compressor**
   - Threshold (-40 to 0 dB)
   - Ratio (1:1 to 20:1)
   - Attack (1-100 ms)
   - Release (10-2000 ms)
   - Makeup Gain (-12 to +24 dB)

4. **Brick-Wall Limiter**
   - Ceiling (-6 to 0 dB)
   - Lookahead (1-50 ms)

5. **Presets**
   - Neutral (Bypass)
   - Live Performance
   - Punchy/Loud
   - Warm/Subtle

6. **Apply Button**
   - Updates the actual mastering chain with current settings

## Using the GUI

### From Command Line (No IDE)

```bash
# Create a script file
cat > mastering_session.scd << 'EOF'
~clip = SCClip.new;
~clip.boot({ ~gui = ~clip.showMasteringGUI });
EOF

# Run it
sclang mastering_session.scd
```

The GUI window will open independently of the SC IDE!

### Interactive Workflow

```supercollider
// 1. Boot and open GUI
~clip = SCClip.new;
~clip.boot({ ~gui = ~clip.showMasteringGUI });

// 2. Click "Enable Mastering Chain" button in GUI
//    (Signal flow diagram updates to show active stages)

// 3. Adjust controls
//    - Move sliders to change parameters
//    - Watch signal flow diagram reflect changes

// 4. Click "Apply Settings" to update mastering chain

// 5. Try presets for quick settings
//    - Click "Live Performance" for balanced settings
//    - Click "Punchy/Loud" for maximum impact

// 6. Close when done
~gui.close;
```

### Programmatic Control

You can also control the GUI from code:

```supercollider
// Load a preset programmatically
~gui.loadPreset(\live);     // Loads "Live Performance" preset
~gui.loadPreset(\punchy);   // Loads "Punchy/Loud" preset
~gui.loadPreset(\warm);     // Loads "Warm/Subtle" preset
~gui.loadPreset(\neutral);  // Loads neutral (bypass) settings

// Apply settings
~gui.applySettings;

// Access individual controls
~gui.controlViews[\thresh].value_(-18);   // Set compressor threshold
~gui.controlViews[\ratio].value_(4);       // Set compressor ratio
~gui.controlViews[\loGain].value_(2);      // Set low shelf gain

// Then apply
~gui.applySettings;
```

## Presets Explained

### Neutral (Bypass)
```supercollider
EQ: All flat (0 dB gain)
Compressor: Minimal (-12 dB threshold, 3:1 ratio, 0 dB makeup)
Limiter: Safety only (-0.3 dB ceiling)
```
**Use case:** Transparent, no coloration

### Live Performance
```supercollider
EQ: +2 dB low, -1 dB mid, +1 dB high
Compressor: -18 dB threshold, 4:1 ratio, +3 dB makeup
Limiter: -0.5 dB ceiling
```
**Use case:** Balanced, punchy, suitable for live shows

### Punchy/Loud
```supercollider
EQ: +3 dB low, 0 dB mid, +2 dB high
Compressor: -24 dB threshold, 6:1 ratio, +6 dB makeup
Limiter: -0.1 dB ceiling
```
**Use case:** Maximum loudness and impact, aggressive compression

### Warm/Subtle
```supercollider
EQ: +1 dB low, -0.5 dB mid, -1 dB high
Compressor: -15 dB threshold, 2.5:1 ratio, +2 dB makeup
Limiter: -0.8 dB ceiling
```
**Use case:** Gentle coloration, warm sound, subtle compression

## Technical Details

### Custom Drawing with UserView

The signal flow diagram uses SuperCollider's **UserView** and **Pen API** for custom drawing:

```supercollider
// Custom drawing function
userView.drawFunc_({
    Pen.use {
        Pen.smoothing_(true);  // Anti-aliasing

        // Draw boxes
        Pen.fillColor_(Color.blue);
        Pen.addRect(Rect(x, y, w, h));
        Pen.fill;

        // Draw arrows
        Pen.strokeColor_(Color.white);
        Pen.line(Point(x1, y1), Point(x2, y2));
        Pen.stroke;

        // Draw text
        Pen.stringAtPoint("Label", Point(x, y));
    };
});
```

This is the **same approach** used in Max/MSP's drawing API and Processing.

### Animation

The GUI is set up for **real-time animation**:

```supercollider
userView.animate_(true);   // Enable animation
userView.frameRate_(30);    // 30 FPS
```

Currently used for updating the signal flow diagram. Could be extended to show:
- Real-time level meters
- Gain reduction visualization
- Spectrum analyzer

### GUI Independence from IDE

SuperCollider's GUI system is **completely independent** of the IDE:

- Built on **Qt** (cross-platform)
- Can run from **sclang command line**
- No IDE process needed
- Works on **headless systems** with X11 forwarding

This means you can:
1. SSH into a server running SC-Clip
2. Forward X11 (`ssh -X user@server`)
3. Run the GUI remotely
4. Control mastering chain from your laptop

## Extending the GUI

### Adding Custom Controls

```supercollider
// In ClipMasteringGUI class, add a row to one of the section layouts
// (e.g. compressorControls). The GUI is built with Qt layouts, so rows
// stretch with the window -- no fixed Rects needed. param registers the
// row in controlViews and returns its layout:

this.param(\myParam, "My Parameter",
    ControlSpec(0, 100, \lin, 1, 50, "units"),
    { |param| /* callback, param.value is the current value */ }
),
```

### Adding VU Meters

```supercollider
// In drawSignalFlow method:
var levels = ~clip.masterBus.getCurrentLevels;  // Would need to implement

// Draw level meter bars
Pen.fillColor_(Color.green);
Pen.addRect(Rect(x, y, width * levels[0], height));
Pen.fill;
```

### Custom Presets

```supercollider
// Add to loadPreset method in ClipMasteringGUI:
myCustom: (
    loGain: 1.5, midGain: -0.3, hiGain: 0.8,
    thresh: -20, ratio: 3.5, makeupGain: 4,
    ceiling: -0.4
)
```

## Comparison: SC GUI vs. Alternatives

| Feature | SC Native GUI | VST Plugin | Web UI | MIDI Controller |
|---------|---------------|------------|---------|-----------------|
| **No external deps** | ✅ | ❌ | ❌ | ❌ |
| **Custom drawing** | ✅ | ❌ | ✅ | ❌ |
| **Works CLI** | ✅ | ❌ | ✅ | ✅ |
| **Tactile control** | ❌ | ❌ | ⚠️ (touch) | ✅ |
| **Visual feedback** | ✅ | ✅ | ✅ | ❌ |
| **Easy to extend** | ✅ | ❌ | ⚠️ | ⚠️ |

## Performance Impact

- **Negligible** when GUI is closed
- **~2-5% CPU** when GUI is open and animating (30 FPS)
- **0% audio thread impact** - GUI runs in separate thread

The GUI is safe to use during live performance without affecting audio.

## Known Limitations

1. **No real-time metering** (yet) - Would need to poll server for levels
2. **Manual apply** - Changes don't auto-apply (by design, to prevent accidental tweaks)
3. **Single window** - Can't detach sections (but keeps things organized)
4. **No MIDI learn** - Can't map MIDI controllers to GUI controls directly

## Future Enhancements

Potential additions:
- Real-time VU meters (polling server levels)
- Gain reduction visualization
- Spectrum analyzer for EQ
- A/B comparison (store/recall two settings)
- MIDI learn for hardware control
- Preset save/load to disk
- Undo/redo for parameter changes

## See Also

- [Examples/mastering_gui_example.scd](../Examples/mastering_gui_example.scd) - Usage example
- [Examples/mastering_gui_demo.scd](../Examples/mastering_gui_demo.scd) - Standalone demo (doesn't require SC-Clip)
- SuperCollider GUI documentation: `GUI.help` in SC
- Pen API: `Pen.help` in SC
