/*
 * SC-Clip Boum Effect Example
 *
 * Demonstrates using the Boum effect (compressor + distortion + hi-cut + gate)
 * both as a channel insert effect and on the master bus.
 *
 * Inspired by the OTO BOUM hardware unit: aggressive bus compression with
 * color options (distortion types) and tone shaping (gate + hi-cut).
 */

// ===== Step 1: Boot server and SC-Clip =====

(
s.options.memSize = 8192 * 16;
s.options.numInputBusChannels = 4;
s.options.numOutputBusChannels = 2;

s.waitForBoot {
	"Server booted!".postln;

	~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);
	~clip.boot(action: {
		"SC-Clip ready!".postln;
		~clip.printStatus;
	});
};
)

// ===== Step 2: Add Boum as a channel insert effect =====

// Add Boum to channel 0 with default settings
(
~clip.channels[0].addEffect(\channelBoum);
"Boum effect added to channel 0".postln;
)

// Adjust Boum parameters on channel 0
(
// Gate: reduce noise floor
~clip.channels[0].setEffectParam(\channelBoum, \gateThresh, -50);

// Compressor: punchy settings
~clip.channels[0].setEffectParam(\channelBoum, \thresh, -18);
~clip.channels[0].setEffectParam(\channelBoum, \ratio, 6);
~clip.channels[0].setEffectParam(\channelBoum, \attack, 0.005);
~clip.channels[0].setEffectParam(\channelBoum, \release, 0.2);

// Sidechain HPF: 75 Hz (1 = 75 Hz)
~clip.channels[0].setEffectParam(\channelBoum, \scHPF, 1);

// Distortion: Tube type (1 = asymmetric tube)
~clip.channels[0].setEffectParam(\channelBoum, \type, 1);
~clip.channels[0].setEffectParam(\channelBoum, \drive, 6);  // 6 dB drive

// Hi-cut filter: reduce harsh highs
~clip.channels[0].setEffectParam(\channelBoum, \hicut, 8000);

// Makeup gain and mix
~clip.channels[0].setEffectParam(\channelBoum, \makeupGain, 4);
~clip.channels[0].setEffectParam(\channelBoum, \mix, 0.8);  // 80% wet

"Boum parameters configured on channel 0".postln;
)

// Remove Boum from channel 0
(
~clip.channels[0].removeEffect(\channelBoum);
"Boum removed from channel 0".postln;
)

// ===== Step 3: Use Boum on the master bus =====

// First, ensure mastering chain is active
(
~clip.masterBus.addMasteringChain;
)

// Switch from Glue Compressor to Boum
(
~clip.masterBus.setCompressorType(\boum);
"Master bus now using Boum compressor".postln;
)

// Configure Boum on master bus - "Warm Crunch" preset
(
~clip.masterBus.setBoum(
	// Gate
	gateThresh: -60,  // Fully open

	// Compressor
	thresh: -15,
	ratio: 4,
	attack: 0.01,
	release: 0.25,
	scHPF: 0,  // 20 Hz (0 = 20 Hz)

	// Distortion
	type: 1,  // Tube (asymmetric)
	drive: 3,  // 3 dB

	// Filter and makeup
	hicut: 12000,  // 12 kHz
	makeupGain: 3,  // 3 dB
	mix: 1  // 100% wet
);
"Boum configured with Warm Crunch preset".postln;
)

// Configure Boum - "Heavy Fuzz" preset
(
~clip.masterBus.setBoum(
	// Gate: reduce noise
	gateThresh: -40,

	// Compressor: aggressive
	thresh: -20,
	ratio: 8,
	attack: 0.003,
	release: 0.15,
	scHPF: 2,  // 250 Hz (2 = 250 Hz)

	// Distortion: Fuzz (hard clip)
	type: 2,
	drive: 12,  // 12 dB

	// Filter: dark tone
	hicut: 6000,
	makeupGain: 6,
	mix: 0.7  // 70% wet, 30% dry for parallel compression
);
"Boum configured with Heavy Fuzz preset".postln;
)

// Configure Boum - "Clean Boost" preset
(
~clip.masterBus.setBoum(
	gateThresh: -60,  // Fully open
	thresh: -10,
	ratio: 2.5,
	attack: 0.015,
	release: 0.4,
	scHPF: 0,  // 20 Hz
	type: 0,  // Boost (soft clip)
	drive: 0,  // No drive
	hicut: 20000,  // Full bandwidth
	makeupGain: 0,
	mix: 1
);
"Boum configured with Clean Boost preset".postln;
)

// Switch back to Glue Compressor
(
~clip.masterBus.setCompressorType(\glueComp);
"Master bus switched back to Glue Compressor".postln;
)

// ===== Step 4: Using Boum with the Mastering GUI =====

// Open the mastering GUI
(
~gui = ~clip.showMasteringGUI;
)

/*
 * In the GUI:
 * 1. Select "Boum" from the COMPRESSOR TYPE dropdown
 * 2. Adjust the Boum parameters in the UI:
 *    - Gate threshold
 *    - Compressor settings (thresh, ratio, attack, release)
 *    - Sidechain HPF (dropdown: 20/75/250 Hz)
 *    - Distortion type (dropdown: Boost/Tube/Fuzz/Square)
 *    - Drive amount
 *    - Hi-cut filter frequency
 *    - Makeup gain
 *    - Dry/wet mix
 * 3. Click "Apply Settings" to update
 */

// Close GUI when done
(
~gui.close;
)

// ===== Distortion Types Explained =====
/*
 * Type 0 - Boost (Soft Clip):
 *   Gentle tanh saturation, transparent coloration
 *   Good for: Subtle warmth, gentle compression
 *
 * Type 1 - Tube (Asymmetric):
 *   Asymmetric tanh with DC bias and blocking
 *   Good for: Vintage tube warmth, harmonic richness
 *
 * Type 2 - Fuzz (Hard Clip):
 *   Hard clipping distortion
 *   Good for: Aggressive lo-fi character, drums
 *   NOTE: Will alias at 48kHz - use sparingly
 *
 * Type 3 - Square (Extreme):
 *   Extreme gain + hard clip for square-wave-like tone
 *   Good for: Experimental/creative destruction
 *   NOTE: Will alias heavily at 48kHz
 */

// ===== Sidechain HPF Options =====
/*
 * The sidechain high-pass filter shapes the compressor's response:
 *
 * 0 = 20 Hz:  Full-range compression (default)
 * 1 = 75 Hz:  Reduces bass pumping, focuses on mids/highs
 * 2 = 250 Hz: Heavy bass reduction, compression driven by vocals/snare
 */

// ===== Tips =====
/*
 * 1. Parallel compression: Set mix < 1.0 to blend dry/wet
 * 2. Avoid fuzz/square at high drive - they alias without oversampling
 * 3. Use gate to reduce noise floor when using high drive
 * 4. Sidechain HPF at 75 Hz prevents bass-heavy mixes from pumping
 * 5. Hi-cut filter darkens the tone - try 6-8 kHz for vintage vibe
 */
