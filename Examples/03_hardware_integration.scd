/*
 * SC-Clip Hardware Integration Example
 *
 * This example demonstrates how to integrate audio interfaces and MIDI
 * controllers with SC-Clip using the hardware integration classes.
 *
 * Available classes:
 *   - ClipAudioInterface (abstract base class for audio interfaces)
 *   - ClipUMC404 (concrete implementation for Behringer UMC404HD)
 *   - ClipMIDIController (base class for MIDI controllers)
 *   - ClipTR8s (concrete implementation for Roland TR-8S as MIDI controller)
 *
 * Prerequisites:
 *   - JACK audio server running (for UMC404 example)
 *   - Hardware connected and powered on
 *   - MIDIClient initialized (done automatically by the classes)
 */

// ========================================================================
// Example 1: Behringer UMC404HD Audio Interface
// ========================================================================

(
// Boot SC-Clip
~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);

~clip.boot(
	options: (
		numInputBusChannels: 4,  // UMC404 has 4 inputs
		numOutputBusChannels: 2, // Stereo output (main L/R)
		sampleRate: 48000
	),
	action: {
		"SC-Clip ready".postln;

		// Create and connect UMC404 interface
		// Pass project directory for connect-midi.sh script
		~projectDir = PathName(thisProcess.nowExecutingPath).pathOnly ++ "../";
		~umc404 = ClipUMC404.new(~projectDir);
		~umc404.connect(~clip);

		// Enable MIDI thru (hardware MIDI IN -> MIDI OUT)
		~umc404.enableMIDIThru;

		// Enable MIDI clock output
		~midiOut = ~umc404.getMIDIOut;
		if (~midiOut.notNil, {
			~clip.enableMIDIClock(~midiOut);
			"MIDI clock enabled".postln;
		});
	}
);
)

// To disconnect the UMC404
~umc404.disconnect;

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

// Wait for boot, then connect TR-8S
fork {
	s.sync;  // Wait for server

	// Create and connect TR-8S controller
	~tr8s = ClipTR8s.new(~clip);
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
	 *   - CTRL knob   -> reverb mix (effect slot 0)
	 *   - DECAY knob  -> delay mix (effect slot 1)
	 *   - TUNE knob   -> distortion mix (effect slot 2)
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

	// Create TR-8S controller
	~tr8s = ClipTR8s.new(~clip);

	// Customize channel assignment before connecting
	~tr8s.setChannelAssignment(
		chChannel: 2,  // CH track -> channel 2
		ohChannel: 3,  // OH track -> channel 3
		ccChannel: 0,  // CC track -> channel 0
		rcChannel: 1   // RC track -> channel 1
	);

	// Customize effects chain
	~tr8s.setEffectsChain([
		// Effect slot 0: Reverb (CTRL knob)
		(
			synthDef: \channelReverb,
			params: [\mix, 0.5, \room, 0.7, \damp, 0.3],
			slot: 0,
			controlParam: \mix,
			controlRange: [0.0, 1.0]
		),
		// Effect slot 1: Delay (DECAY knob)
		(
			synthDef: \channelDelay,
			params: [\mix, 0.2, \delayTime, 0.25, \decayTime, 3],
			slot: 1,
			controlParam: \mix,
			controlRange: [0.0, 1.0]
		)
		// Slot 2 omitted - TUNE knob won't do anything
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
// Example 5: Complete Setup with Both UMC404 and TR-8S
// ========================================================================

(
~projectDir = PathName(thisProcess.nowExecutingPath).pathOnly ++ "../";

~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);

~clip.boot(
	options: (
		numInputBusChannels: 4,
		numOutputBusChannels: 2,
		sampleRate: 48000
	),
	action: {
		"SC-Clip ready".postln;

		// Set up UMC404 audio interface
		~umc404 = ClipUMC404.new(~projectDir);
		~umc404.connect(~clip);
		~umc404.enableMIDIThru;

		// Enable MIDI clock
		~midiOut = ~umc404.getMIDIOut;
		if (~midiOut.notNil, {
			~clip.enableMIDIClock(~midiOut);
		});

		// Set up TR-8S controller
		~tr8s = ClipTR8s.new(~clip);
		~tr8s.connect;

		"Hardware integration complete".postln;
	}
);
)

// Cleanup
(
~tr8s.disconnect;
~umc404.disconnect;
~clip.shutdown;
)
