/*
 * ClipTR8s4Channel
 *
 * Concrete implementation for Roland TR-8S drum machine as a MIDI controller,
 * using 4 of the 11 available tracks.
 *
 * Hardware specs:
 *   - 11 drum tracks (BD, SD, LT, MT, HT, RS, CP, CH, OH, CC, RC)
 *   - Each track has: fader (LEVEL), 3 knobs (TUNE, DECAY, CTRL)
 *   - Transmits as MIDI CC on a single channel (Basic Channel, default 10)
 *   - Class-compliant USB MIDI on Linux (no driver needed)
 *   - Shows up as ALSA MIDI ports "TR-8S MIDI 1" and "TR-8S MIDI 2"
 *
 * SC-Clip usage (4-channel configuration):
 *   This implementation uses the 4 rightmost TR-8S tracks (CH, OH, CC, RC)
 *   as controllers for SC-Clip's 4 channels, with 1:1 mapping to UMC404's
 *   4 input channels.
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
 *   CH (Closed Hat)    61     62     63    107
 *   OH (Open Hat)      80     81     82    108
 *   CC (Crash)         83     84     85    109
 *   RC (Ride)          86     87     88    110
 *
 * For an 11-channel configuration using all TR-8S tracks, see ClipTR8s11Channel
 * (future implementation).
 */

ClipTR8s4Channel : ClipMIDIController {
	var <tracks;            // Dictionary of track definitions (CC numbers)
	var <channelAssignment; // Which SC-Clip channel each track controls
	var <knobMapping;       // How knobs map to effect slots and parameters

	*new { |clip, midiChannel = 9|  // Basic Channel 10 = 0-indexed 9
		^super.new(clip, midiChannel, "TR-8S 4Ch").initTR8s4Channel;
	}

	initTR8s4Channel {
		// Initialize MIDI and connect all input sources. Routed through
		// ClipMIDI so this only happens once per session even if another
		// controller (e.g. ClipLaunchpadMini) already did it -- calling
		// MIDIIn.connectAll a second time in the same session has been
		// observed to hang sclang on real hardware.
		ClipMIDI.connectAllInputs;

		// Define TR-8S track CC numbers (from Roland MIDI implementation chart)
		tracks = (
			ch: (tune: 61, decay: 62, level: 63, ctrl: 107),  // Closed Hi-Hat
			oh: (tune: 80, decay: 81, level: 82, ctrl: 108),  // Open Hi-Hat
			cc: (tune: 83, decay: 84, level: 85, ctrl: 109),  // Crash Cymbal
			rc: (tune: 86, decay: 87, level: 88, ctrl: 110)   // Ride Cymbal
		);

		// Default 1:1 mapping: 4 TR-8S tracks -> 4 SC-Clip channels
		channelAssignment = (
			ch: 0,  // Closed Hat -> Channel 0
			oh: 1,  // Open Hat   -> Channel 1
			cc: 2,  // Crash      -> Channel 2
			rc: 3   // Ride       -> Channel 3
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

		"ClipTR8s4Channel: Initialized".postln;
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
	setChannelAssignment { |chChannel = 0, ohChannel = 1, ccChannel = 2, rcChannel = 3|
		channelAssignment = (
			ch: chChannel,
			oh: ohChannel,
			cc: ccChannel,
			rc: rcChannel
		);
		"ClipTR8s4Channel: Channel assignment updated".postln;
	}

	// Allow users to customize knob mapping before connecting
	// Example: setKnobMapping([(knob: \ctrl, slot: 0, param: \room, range: [0.0, 1.0])])
	setKnobMapping { |newKnobMapping|
		knobMapping = newKnobMapping;
		"ClipTR8s4Channel: Knob mapping updated".postln;
	}

	// Print current configuration
	printStatus {
		"ClipTR8s4Channel: Connected and configured".postln;
		"  TR-8S tracks -> SC-Clip channels:".postln;
		"    CH (Closed Hat) -> Channel %".format(channelAssignment[\ch]).postln;
		"    OH (Open Hat)   -> Channel %".format(channelAssignment[\oh]).postln;
		"    CC (Crash)      -> Channel %".format(channelAssignment[\cc]).postln;
		"    RC (Ride)       -> Channel %".format(channelAssignment[\rc]).postln;
		"  Knob mappings: % configured".format(knobMapping.size).postln;
		knobMapping.do { |mapping|
			"    % knob -> effect slot %, param %".format(
				mapping[\knob], mapping[\slot], mapping[\param]
			).postln;
		};
	}

	// Override disconnect to clean up
	disconnect {
		"ClipTR8s4Channel: Disconnecting...".postln;
		^super.disconnect;
	}
}
