/*
 * ClipTR8s3Channel
 *
 * Concrete implementation for Roland TR-8S drum machine as a MIDI controller,
 * using the 3 tom tracks (LT, MT, HT) of the 11 available tracks.
 *
 * Hardware specs:
 *   - 11 drum tracks (BD, SD, LT, MT, HT, RS, CP, CH, OH, CC, RC)
 *   - Each track has: fader (LEVEL), 3 knobs (TUNE, DECAY, CTRL)
 *   - Transmits as MIDI CC on a single channel (Basic Channel, default 10)
 *   - Class-compliant USB MIDI on Linux (no driver needed)
 *   - Shows up as ALSA MIDI ports "TR-8S MIDI 1" and "TR-8S MIDI 2"
 *
 * SC-Clip usage (3-channel configuration):
 *   This implementation uses the 3 TR-8S tom tracks (LT, MT, HT) as
 *   controllers for SC-Clip's first 3 channels, with 1:1 mapping to the
 *   UMC404's first 3 input channels. Any further SC-Clip channels (e.g. the
 *   UMC404's 4th input) are left uncontrolled by the TR-8S.
 *
 * Default mapping (can be customized via setKnobMapping):
 *   - LEVEL fader -> SC-Clip channel level (dB)
 *   - CTRL knob   -> Channel effect slot 0 parameter
 *   - DECAY knob  -> Channel effect slot 1 parameter
 *   - TUNE knob   -> Channel effect slot 2 parameter
 *
 * The controller ONLY maps to effects - it does not create them.
 * Effects should be added to channels via SCClip before connecting the controller.
 *
 * CC numbers (from Roland TR-8S MIDI Implementation Chart v1.10):
 *                      TUNE  DECAY  LEVEL  CTRL
 *   LT (Low Tom)       46     47     48    102
 *   MT (Mid Tom)       49     50     51    103
 *   HT (High Tom)      52     53     54    104
 *
 * See ClipTR8s4Channel for the 4-channel variant using CH, OH, CC, RC.
 */

ClipTR8s3Channel : ClipMIDIController {
	var <tracks;            // Dictionary of track definitions (CC numbers)
	var <channelAssignment; // Which SC-Clip channel each track controls
	var <knobMapping;       // How knobs map to effect slots and parameters

	*new { |clip, midiChannel = 9|  // Basic Channel 10 = 0-indexed 9
		^super.new(clip, midiChannel, "TR-8S 3Ch").initTR8s3Channel;
	}

	initTR8s3Channel {
		// Initialize MIDI and connect all input sources. Routed through
		// ClipMIDI so this only happens once per session even if another
		// controller (e.g. ClipLaunchpadMini) already did it -- calling
		// MIDIIn.connectAll a second time in the same session has been
		// observed to hang sclang on real hardware.
		ClipMIDI.connectAllInputs;

		// Define TR-8S track CC numbers (from Roland MIDI implementation chart)
		tracks = (
			lt: (tune: 46, decay: 47, level: 48, ctrl: 102),  // Low Tom
			mt: (tune: 49, decay: 50, level: 51, ctrl: 103),  // Mid Tom
			ht: (tune: 52, decay: 53, level: 54, ctrl: 104)   // High Tom
		);

		// Default 1:1 mapping: 3 TR-8S tracks -> first 3 SC-Clip channels
		channelAssignment = (
			lt: 0,  // Low Tom  -> Channel 0
			mt: 1,  // Mid Tom  -> Channel 1
			ht: 2   // High Tom -> Channel 2
		);

		// Default knob-to-effect mapping
		// Maps knobs to effect slots and parameters (if effects exist)
		knobMapping = [
			// CTRL knob -> Effect slot 0, \mix parameter
			(knob: \ctrl, slot: 0, param: \mix, range: [0.0, 1.0]),
			// DECAY knob -> Effect slot 1, \mix parameter
			(knob: \decay, slot: 1, param: \mix, range: [0.0, 1.0]),
			// TUNE knob -> Effect slot 2, \mix parameter
			(knob: \tune, slot: 2, param: \mix, range: [0.0, 1.0])
		];

		"ClipTR8s3Channel: Initialized".postln;
		^this;
	}

	// Connect and set up all mappings
	connect {
		this.setupChannelMappings;
		this.printStatus;
		^this;
	}

	// Set up fader and knob mappings for all assigned tracks
	setupChannelMappings {
		channelAssignment.keysValuesDo { |track, chanIdx|
			var cc = tracks[track];

			// Map fader to channel level
			this.mapFader(cc[\level], chanIdx, -60, 6);

			// Map knobs to effect parameters (only if effects exist)
			knobMapping.do { |mapping|
				var knobName = mapping[\knob];
				var slot = mapping[\slot];
				var param = mapping[\param];
				var range = mapping[\range];
				var knobCC = cc[knobName];

				if (knobCC.notNil, {
					this.mapKnob(
						knobCC,
						chanIdx,
						slot,
						param,
						range[0],
						range[1]
					);
				});
			};
		};
	}

	// Allow users to customize channel assignment before connecting
	setChannelAssignment { |ltChannel = 0, mtChannel = 1, htChannel = 2|
		channelAssignment = (
			lt: ltChannel,
			mt: mtChannel,
			ht: htChannel
		);
		"ClipTR8s3Channel: Channel assignment updated".postln;
	}

	// Allow users to customize knob mapping before connecting
	// Example: setKnobMapping([(knob: \ctrl, slot: 0, param: \room, range: [0.0, 1.0])])
	setKnobMapping { |newKnobMapping|
		knobMapping = newKnobMapping;
		"ClipTR8s3Channel: Knob mapping updated".postln;
	}

	// Print current configuration
	printStatus {
		"ClipTR8s3Channel: Connected and configured".postln;
		"  TR-8S tracks -> SC-Clip channels:".postln;
		"    LT (Low Tom)  -> Channel %".format(channelAssignment[\lt]).postln;
		"    MT (Mid Tom)  -> Channel %".format(channelAssignment[\mt]).postln;
		"    HT (High Tom) -> Channel %".format(channelAssignment[\ht]).postln;
		"  Knob mappings: % configured".format(knobMapping.size).postln;
		knobMapping.do { |mapping|
			"    % knob -> effect slot %, param %".format(
				mapping[\knob], mapping[\slot], mapping[\param]
			).postln;
		};
	}

	// Override disconnect to clean up
	disconnect {
		"ClipTR8s3Channel: Disconnecting...".postln;
		^super.disconnect;
	}
}
