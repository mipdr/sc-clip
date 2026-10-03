/*
 * Example: Channel Effect Selection
 *
 * Demonstrates the flexible channel effect system with hot-swappable effects.
 */

// Initialize sc-clip
(
~clip = SCClip.new(
	server: Server.default,
	numChannels: 4,
	numSlots: 4
);
)

// Wait for initialization
(
fork {
	1.wait;
	"SC-Clip initialized. Ready for effect examples.".postln;
	"\nAvailable effects:".postln;
	ClipEffectRegistry.all.keys.do { |name|
		var meta = ClipEffectRegistry.get(name);
		var mainParam = meta[\mainControl] ? "none";
		"  % - Main control: %".format(meta[\displayName], mainParam).postln;
	};
};
)

// === Example 1: Basic Effect Usage ===

// Add reverb to channel 0
(
~clip.grid.getChannel(0).addEffect(\channelReverb, [
	\mix, 0.3,
	\room, 0.5,
	\damp, 0.5
], 0);
)

// Add delay to channel 0 slot 1
(
~clip.grid.getChannel(0).addEffect(\channelDelay, [
	\mix, 0.3,
	\delayTime, 0.375,
	\decayTime, 2
], 1);
)

// Query current effects
(
"Channel 0 effects:".postln;
3.do { |slot|
	var effectName = ~clip.grid.getChannel(0).getEffectName(slot);
	var mainValue = ~clip.grid.getChannel(0).getEffectMainValue(slot);
	if (effectName.notNil, {
		var meta = ClipEffectRegistry.get(effectName);
		"  Slot %: % (main control = %)".format(
			slot,
			meta[\displayName],
			mainValue
		).postln;
	}, {
		"  Slot %: Bypass".format(slot).postln;
	});
};
)

// === Example 2: Hot-Swap Effects ===

// Replace reverb with chorus
(
~clip.grid.getChannel(0).addEffect(\channelChorus, [
	\mix, 0.5,
	\rate, 0.5,
	\depth, 0.01,
	\voices, 4
], 0);
)

// Then swap chorus for phaser
(
~clip.grid.getChannel(0).addEffect(\channelPhaser, [
	\mix, 0.5,
	\rate, 0.3,
	\depth, 0.5,
	\stages, 4
], 0);
)

// Then swap phaser for flanger
(
~clip.grid.getChannel(0).addEffect(\channelFlanger, [
	\mix, 0.5,
	\rate, 0.2,
	\depth, 0.005,
	\feedback, 0.5
], 0);
)

// === Example 3: Filter Sweep with Main Control ===

// Add low-pass filter to channel 1
(
~clip.grid.getChannel(1).addEffect(\channelLowPass, [
	\freq, 5000,
	\resonance, 0.7,
	\mix, 1.0
], 0);
)

// Sweep the filter cutoff (main control)
(
fork {
	var channel = ~clip.grid.getChannel(1);
	20.do { |i|
		var freq = 200 + (i * 950);  // 200 Hz to 20 kHz
		channel.setEffectMainControl(0, freq);
		0.1.wait;
	};
	"Filter sweep complete".postln;
};
)

// === Example 4: Vocal Chain ===

// Channel 0: Vocal processing chain
// Slot 0: High-pass filter (remove low-end)
// Slot 1: Compressor (even out dynamics)
// Slot 2: Reverb (add space)
(
var vocal = ~clip.grid.getChannel(0);

vocal.addEffect(\channelHighPass, [
	\freq, 100,  // Remove sub-100Hz
	\resonance, 0.1,
	\mix, 1.0
], 0);

vocal.addEffect(\channelBoum, [
	\thresh, -18,
	\ratio, 4,
	\attack, 0.005,
	\release, 0.2,
	\mix, 1.0
], 1);

vocal.addEffect(\channelReverb, [
	\mix, 0.25,
	\room, 0.6,
	\damp, 0.7
], 2);

"Vocal chain configured".postln;
)

// === Example 5: Drum Processing ===

// Channel 1: Drum processing
// Slot 0: Distortion (add grit)
// Slot 1: Chorus (widen)
// Slot 2: Tape delay (rhythmic echo)
(
var drums = ~clip.grid.getChannel(1);

drums.addEffect(\channelHardClip, [
	\mix, 1.0,
	\drive, 0.3,
	\bias, 0
], 0);

drums.addEffect(\channelChorus, [
	\mix, 0.3,
	\rate, 0.7,
	\depth, 0.008,
	\voices, 3
], 1);

drums.addEffect(\channelTapeDelay, [
	\mix, 0.2,
	\delayTime, 0.375,  // Dotted eighth
	\feedback, 0.4,
	\wobble, 0.001
], 2);

"Drum chain configured".postln;
)

// === Example 6: Bass Treatment ===

// Channel 2: Bass processing
// Slot 0: Low-pass filter (focus low-end)
// Slot 1: Soft distortion (add warmth)
// Slot 2: Bypass
(
var bass = ~clip.grid.getChannel(2);

bass.addEffect(\channelLowPass, [
	\freq, 800,
	\resonance, 0.4,
	\mix, 1.0
], 0);

bass.addEffect(\channelDistortion, [
	\mix, 1.0,
	\drive, 0.4
], 1);

// Slot 2 stays bypassed (nil)

"Bass chain configured".postln;
)

// === Example 7: Synth Modulation ===

// Channel 3: Synth with heavy modulation
// Slot 0: Phaser (sweeping filter)
// Slot 1: Flanger (metallic chorus)
// Slot 2: GVerb (if available) or FreeVerb
(
var synth = ~clip.grid.getChannel(3);

synth.addEffect(\channelPhaser, [
	\mix, 0.6,
	\rate, 0.25,
	\depth, 0.8,
	\stages, 6
], 0);

synth.addEffect(\channelFlanger, [
	\mix, 0.4,
	\rate, 0.15,
	\depth, 0.007,
	\feedback, 0.6
], 1);

// Try GVerb if available, otherwise FreeVerb
if (ClipEffectRegistry.get(\channelGVerb).notNil, {
	synth.addEffect(\channelGVerb, [
		\mix, 0.3,
		\roomsize, 20,
		\revtime, 4,
		\damping, 0.5,
		\spread, 20
	], 2);
	"Using GVerb".postln;
}, {
	synth.addEffect(\channelReverb, [
		\mix, 0.3,
		\room, 0.7,
		\damp, 0.4
	], 2);
	"Using FreeVerb (GVerb not available)".postln;
});

"Synth modulation chain configured".postln;
)

// === Example 8: MIDI Control with TR-8S ===

// If you have a TR-8S, map knobs to effect main controls
(
// Connect TR-8S controller (assumes TR-8S on MIDI channel 10)
~tr8s = ClipTR8s4Channel(~clip, 9);  // 0-indexed: 9 = channel 10
~tr8s.connect;

/*
 * Default TR-8S mapping:
 *   CTRL knob  -> Effect slot 0 main control
 *   DECAY knob -> Effect slot 1 main control
 *   TUNE knob  -> Effect slot 2 main control
 *
 * The knobs automatically control whatever effect is loaded in each slot.
 * For example:
 *   - If slot 0 has \channelReverb, CTRL knob controls mix
 *   - If slot 0 has \channelLowPass, CTRL knob controls freq
 *   - If slot 0 has \channelDistortion, CTRL knob controls drive
 */
)

// === Example 9: Session Save/Load ===

// Save current effect chains
(
~clip.saveSession("/tmp/my_effect_chains".standardizePath, {
	"Session saved with all effect chains".postln;
});
)

// Load saved effect chains
(
~clip.loadSession("/tmp/my_effect_chains".standardizePath, {
	"Session loaded - all effect chains restored".postln;
});
)

// === Example 10: Open Mastering GUI ===

// Open GUI to see channel effects table
(
~gui = ClipMasteringGUI(~clip);
~gui.show;
)

/*
 * The GUI shows:
 * - Left side: Master bus controls (EQ, compressor, limiter)
 * - Middle: Channel effects table (dropdown selectors)
 * - Right side: Level meters
 *
 * Use the dropdowns to hot-swap effects while playing!
 */

// === Cleanup ===
(
~clip.free;
~gui !? { ~gui.close };
)
