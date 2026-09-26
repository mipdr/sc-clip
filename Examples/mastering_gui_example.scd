/*
 * SC-Clip Mastering GUI Example
 *
 * Demonstrates the native SuperCollider GUI for mastering chain control.
 * Can be run from command line - no IDE needed!
 *
 * Run with: sclang mastering_gui_example.scd
 */

(
// Boot SC-Clip
~clip = SCClip.new(numChannels: 4, numSlots: 8);

~clip.boot({
	"SC-Clip booted!".postln;
	"".postln;
	"Opening mastering GUI...".postln;

	// Show the mastering GUI
	~gui = ~clip.showMasteringGUI;

	"".postln;
	"=== MASTERING GUI CONTROLS ===".postln;
	"1. Click 'Enable Mastering Chain' to activate".postln;
	"2. Adjust EQ, Compressor, and Limiter parameters".postln;
	"3. Try the preset buttons for quick settings".postln;
	"4. Click 'Apply Settings' to update the mastering chain".postln;
	"".postln;
	"The signal flow diagram at the top shows the routing.".postln;
	"Green boxes = active, Gray boxes = bypassed".postln;
	"".postln;
});
)

// Create a test tone to hear the mastering chain in action
(
~testTone = {
	var freq = LFNoise1.kr(0.5).range(200, 800);
	var sig = SinOsc.ar([freq, freq * 1.5]) * 0.3;
	sig
}.play;

"Test tone playing. Adjust mastering settings to hear the effect.".postln;
)

// Stop test tone
~testTone.free;

// You can also control the GUI programmatically
~gui.loadPreset(\live);  // Load "Live Performance" preset
~gui.applySettings;      // Apply current settings

// Close the GUI
~gui.close;

// Shutdown SC-Clip
~clip.shutdown;

/*
 * QUICK REFERENCE
 *
 * Open GUI:
 *   ~gui = ~clip.showMasteringGUI;
 *
 * Load presets:
 *   ~gui.loadPreset(\neutral);  // Bypass (all flat)
 *   ~gui.loadPreset(\live);     // Live performance (punchy, balanced)
 *   ~gui.loadPreset(\punchy);   // Maximum loudness/punch
 *   ~gui.loadPreset(\warm);     // Warm, subtle compression
 *
 * Close GUI:
 *   ~gui.close;
 *
 * The GUI works WITHOUT the IDE - just run from command line!
 */
