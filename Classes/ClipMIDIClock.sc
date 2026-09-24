/*
 * ClipMIDIClock
 *
 * Simple utility for setting up MIDI clock output with SC-Clip.
 * This is a lightweight helper that just handles the common pattern of:
 *   1. Initialize MIDIClient
 *   2. Create MIDIOut for a named device
 *   3. Enable SC-Clip's MIDI clock on that output
 *
 * For Linux ALSA routing (connecting SC's virtual port to hardware),
 * you'll need to handle that in your project (e.g., via connect-midi.sh).
 */

ClipMIDIClock {

	// Set up MIDI clock output for SC-Clip
	// Returns the MIDIOut instance on success, nil on failure
	*setup { |clip, deviceName, portName|
		var midiOut;

		// Initialize MIDI if needed
		if (MIDIClient.initialized.not, {
			MIDIClient.init;
			"ClipMIDIClock: Initialized MIDIClient".postln;
		});

		// Create MIDIOut
		midiOut = MIDIOut.newByName(deviceName, portName);

		if (midiOut.isNil, {
			"ClipMIDIClock: Failed to create MIDIOut for device '%' port '%'".format(
				deviceName, portName
			).error;
			"ClipMIDIClock: Available MIDI destinations:".postln;
			MIDIClient.destinations.do(_.postln);
			^nil;
		});

		// Enable clock
		clip.enableMIDIClock(midiOut);

		"ClipMIDIClock: Enabled on device '%' port '%'".format(
			deviceName, portName
		).postln;

		^midiOut;
	}

	// Disable MIDI clock (just delegates to SC-Clip)
	*disable { |clip|
		clip.disableMIDIClock;
		"ClipMIDIClock: Disabled".postln;
	}

}
