/*
 * SC-Clip Hardware Integration Example
 *
 * This example demonstrates how to integrate MIDI controllers and MIDI clock
 * with SC-Clip.
 *
 * Available classes:
 *   - ClipMIDIController (base class for MIDI controllers)
 *   - ClipTR8s4Channel (concrete implementation for Roland TR-8S as MIDI controller)
 *   - ClipMIDIClock (helper for setting up MIDI clock output)
 *   - ClipLaunchpadMini (grid controller for clip launching)
 *
 * Prerequisites:
 *   - JACK audio server running (handles audio interface routing)
 *   - Hardware connected and powered on
 *   - MIDIClient initialized (done automatically by the classes)
 *
 * Note: Audio interface selection happens in JACK, not in SC-Clip.
 * See your project's start-audio.sh script for JACK device configuration.
 */

// ========================================================================
// Example 1: MIDI Clock Output Setup
// ========================================================================

(
// Boot SC-Clip
~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);

~clip.boot(
	options: (
		numInputBusChannels: 4,  // 4 hardware inputs (from JACK)
		numOutputBusChannels: 2, // Stereo output
		sampleRate: 48000
	),
	action: {
		"SC-Clip ready".postln;

		// Set up MIDI clock output
		// For UMC404HD on Linux:
		~midiOut = ClipMIDIClock.setup(~clip, "UMC404HD 192k", "UMC404HD 192k MIDI 1");

		// For other devices, just change the device name:
		// ~midiOut = ClipMIDIClock.setup(~clip, "YourDevice", "YourDevice MIDI 1");
	}
);
)

// To disable MIDI clock
ClipMIDIClock.disable(~clip);

// ========================================================================
// Example 2: Roland TR-8S MIDI Controller
// ========================================================================

(
// Boot SC-Clip (if not already running)
~clip = ~clip ?? {
	var c = SCClip.new(numChannels: 4, numSlots: 8, server: s);
	c.boot(action: { "SC-Clip ready".postln });
	c;
};

// Wait for boot, then set up effects and connect TR-8S
fork {
	s.sync;  // Wait for server

	// First, add effects to the channels
	// The TR-8S controller will map to these effects, not create them
	4.do { |chanIdx|
		var channel = ~clip.grid.getChannel(chanIdx);

		// Effect slot 0: Reverb (will be controlled by CTRL knob)
		channel.addEffect(\channelReverb, [\mix, 0.3, \room, 0.5, \damp, 0.5], slot: 0);

		// Effect slot 1: Delay (will be controlled by DECAY knob)
		channel.addEffect(\channelDelay, [\mix, 0.0, \delayTime, 0.3, \decayTime, 2], slot: 1);

		// Effect slot 2: Distortion (will be controlled by TUNE knob)
		channel.addEffect(\channelDistortion, [\mix, 0.0, \drive, 0.5], slot: 2);
	};

	// Now connect TR-8S controller to map its knobs to the effects
	~tr8s = ClipTR8s4Channel.new(~clip);
	~tr8s.connect;

	/*
	 * Default mapping (can be customized before calling connect):
	 *   - CH (Closed Hat) track -> SC-Clip channel 0
	 *   - OH (Open Hat) track   -> SC-Clip channel 1
	 *   - CC (Crash) track      -> SC-Clip channel 2
	 *   - RC (Ride) track       -> SC-Clip channel 3
	 *
	 * Each track's controls:
	 *   - LEVEL fader -> channel level (dB)
	 *   - CTRL knob   -> effect slot 0, \mix parameter
	 *   - DECAY knob  -> effect slot 1, \mix parameter
	 *   - TUNE knob   -> effect slot 2, \mix parameter
	 */
};
)

// To disconnect the TR-8S
~tr8s.disconnect;

// ========================================================================
// Example 3: Custom TR-8S Configuration
// ========================================================================

(
// Boot SC-Clip with custom tempo
~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);
~clip.boot(action: {
	~clip.setTempo(128);

	// Add effects to channels
	4.do { |chanIdx|
		var channel = ~clip.grid.getChannel(chanIdx);

		// Effect slot 0: Reverb with custom settings
		channel.addEffect(\channelReverb, [\mix, 0.5, \room, 0.7, \damp, 0.3], slot: 0);

		// Effect slot 1: Delay with custom settings
		channel.addEffect(\channelDelay, [\mix, 0.2, \delayTime, 0.25, \decayTime, 3], slot: 1);

		// Slot 2: No effect - TUNE knob won't do anything
	};

	// Create TR-8S controller
	~tr8s = ClipTR8s4Channel.new(~clip);

	// Customize channel assignment before connecting
	~tr8s.setChannelAssignment(
		chChannel: 2,  // CH track -> channel 2
		ohChannel: 3,  // OH track -> channel 3
		ccChannel: 0,  // CC track -> channel 0
		rcChannel: 1   // RC track -> channel 1
	);

	// Customize knob mapping to control different parameters
	~tr8s.setKnobMapping([
		// CTRL knob -> reverb \room parameter instead of \mix
		(knob: \ctrl, slot: 0, param: \room, range: [0.0, 1.0]),
		// DECAY knob -> delay \decayTime parameter
		(knob: \decay, slot: 1, param: \decayTime, range: [1.0, 5.0]),
		// TUNE knob -> not mapped (no effect in slot 2)
	]);

	// Connect with custom configuration
	~tr8s.connect;
});
)

// ========================================================================
// Example 4: Generic MIDI Controller (not TR-8S specific)
// ========================================================================

(
// Boot SC-Clip
~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);
~clip.boot(action: {
	// Create a generic MIDI controller
	// Listen on MIDI channel 0 (1 in 1-indexed notation)
	~controller = ClipMIDIController.new(~clip, midiChannel: 0, deviceName: "MyController");

	// Map CC 7 to channel 0 level
	~controller.mapFader(ccNum: 7, channelIndex: 0, minDb: -60, maxDb: 6);

	// Map CC 10 to channel 0 pan
	~controller.mapChannelPan(ccNum: 10, channelIndex: 0);

	// Map CC 1 (mod wheel) to master level
	~controller.mapMasterLevel(ccNum: 1, minDb: -60, maxDb: 0);

	// Map CC 74 to tempo
	~controller.mapTempo(ccNum: 74, minBPM: 80, maxBPM: 160);

	// Add an effect and map a knob to it
	~clip.grid.getChannel(0).addEffect(\channelReverb, [\mix, 0.3], slot: 0);
	~controller.mapKnob(ccNum: 91, channelIndex: 0, slot: 0, param: \mix, lo: 0.0, hi: 1.0);

	"Generic MIDI controller configured".postln;
});
)

// To disconnect
~controller.disconnect;

// ========================================================================
// Example 5: Complete Setup with MIDI Clock and TR-8S
// ========================================================================

(
~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);

~clip.boot(
	options: (
		numInputBusChannels: 4,
		numOutputBusChannels: 2,
		sampleRate: 48000
	),
	action: {
		"SC-Clip ready".postln;

		// Set up MIDI clock output
		~midiOut = ClipMIDIClock.setup(~clip, "UMC404HD 192k", "UMC404HD 192k MIDI 1");

		// Add effects to channels before connecting TR-8S
		4.do { |chanIdx|
			var channel = ~clip.grid.getChannel(chanIdx);
			channel.addEffect(\channelReverb, [\mix, 0.3, \room, 0.5, \damp, 0.5], slot: 0);
			channel.addEffect(\channelDelay, [\mix, 0.0, \delayTime, 0.3, \decayTime, 2], slot: 1);
			channel.addEffect(\channelDistortion, [\mix, 0.0, \drive, 0.5], slot: 2);
		};

		// Set up TR-8S controller
		~tr8s = ClipTR8s4Channel.new(~clip);
		~tr8s.connect;

		"Hardware integration complete".postln;
	}
);
)

// Cleanup
(
~tr8s.disconnect;
ClipMIDIClock.disable(~clip);
~clip.shutdown;
)
