/*
 * ClipTR8s
 *
 * Concrete implementation for Roland TR-8S drum machine as a MIDI controller.
 *
 * Hardware specs:
 *   - 11 drum tracks (BD, SD, LT, MT, HT, RS, CP, CH, OH, CC, RC)
 *   - Each track has: fader (LEVEL), 3 knobs (TUNE, DECAY, CTRL)
 *   - Transmits as MIDI CC on a single channel (Basic Channel, default 10)
 *   - Class-compliant USB MIDI on Linux (no driver needed)
 *   - Shows up as ALSA MIDI ports "TR-8S MIDI 1" and "TR-8S MIDI 2"
 *
 * SC-Clip usage:
 *   This implementation uses the 4 rightmost TR-8S tracks (CH, OH, CC, RC)
 *   as controllers for SC-Clip's 4 channels, with 1:1 mapping to UMC404's
 *   4 input channels.
 *
 * Mapping:
 *   - LEVEL fader -> SC-Clip channel level (dB)
 *   - CTRL knob   -> Channel effect slot 0 (e.g., reverb mix)
 *   - DECAY knob  -> Channel effect slot 1 (e.g., delay mix)
 *   - TUNE knob   -> Channel effect slot 2 (e.g., distortion mix)
 *
 * CC numbers (from Roland TR-8S MIDI Implementation Chart v1.10):
 *                      TUNE  DECAY  LEVEL  CTRL
 *   CH (Closed Hat)    61     62     63    107
 *   OH (Open Hat)      80     81     82    108
 *   CC (Crash)         83     84     85    109
 *   RC (Ride)          86     87     88    110
 *
 * Other tracks (BD, SD, LT, MT, HT, RS, CP) are not used for control in this
 * implementation, but could be mapped if needed by extending this class.
 */

ClipTR8s : ClipMIDIController {
	var <tracks;            // Dictionary of track definitions (CC numbers)
	var <channelAssignment; // Which SC-Clip channel each track controls
	var <effectsChain;      // Effect configuration for each channel

	*new { |clip, midiChannel = 9|  // Basic Channel 10 = 0-indexed 9
		^super.new(clip, midiChannel, "TR-8S").initTR8s;
	}

	initTR8s {
		// Initialize MIDI if needed (safe to call multiple times)
		if (MIDIClient.initialized.not, { MIDIClient.init });
		MIDIIn.connectAll;

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

		// Default effects chain configuration
		// Subclasses or users can override this before calling connect()
		effectsChain = [
			// Effect slot 0: Reverb (controlled by CTRL knob)
			(
				synthDef: \channelReverb,
				params: [\mix, 0.3, \room, 0.5, \damp, 0.5],
				slot: 0,
				controlParam: \mix,
				controlRange: [0.0, 1.0]
			),
			// Effect slot 1: Delay (controlled by DECAY knob)
			(
				synthDef: \channelDelay,
				params: [\mix, 0.0, \delayTime, 0.3, \decayTime, 2],
				slot: 1,
				controlParam: \mix,
				controlRange: [0.0, 1.0]
			),
			// Effect slot 2: Distortion (controlled by TUNE knob)
			(
				synthDef: \channelDistortion,
				params: [\mix, 0.0, \drive, 0.5],
				slot: 2,
				controlParam: \mix,
				controlRange: [0.0, 1.0]
			)
		];

		"ClipTR8s: Initialized".postln;
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
			var channel = clip.grid.getChannel(chanIdx);

			if (channel.isNil, {
				"ClipTR8s: Warning - Channel % not found, skipping track %".format(chanIdx, track).warn;
			}, {
				// Map fader to channel level
				this.mapFader(cc[\level], chanIdx, -60, 6);

				// Add effects and map knobs
				effectsChain.do { |effectConfig|
					var slot = effectConfig[\slot];
					var synthDef = effectConfig[\synthDef];
					var params = effectConfig[\params];
					var controlParam = effectConfig[\controlParam];
					var controlRange = effectConfig[\controlRange];
					var knobCC;

					// Add effect to channel
					channel.addEffect(synthDef, params, slot);

					// Determine which knob controls this effect slot
					knobCC = case
						{ slot == 0 } { cc[\ctrl] }   // Slot 0: CTRL knob
						{ slot == 1 } { cc[\decay] }  // Slot 1: DECAY knob
						{ slot == 2 } { cc[\tune] }   // Slot 2: TUNE knob
						{ nil };  // No mapping for other slots

					// Map knob to effect parameter
					if (knobCC.notNil, {
						this.mapKnob(
							knobCC,
							chanIdx,
							slot,
							controlParam,
							controlRange[0],
							controlRange[1]
						);
					});
				};
			});
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
		"ClipTR8s: Channel assignment updated".postln;
	}

	// Allow users to customize effects chain before connecting
	setEffectsChain { |newEffectsChain|
		effectsChain = newEffectsChain;
		"ClipTR8s: Effects chain updated".postln;
	}

	// Print current configuration
	printStatus {
		"ClipTR8s: Connected and configured".postln;
		"  TR-8S tracks -> SC-Clip channels:".postln;
		"    CH (Closed Hat) -> Channel %".format(channelAssignment[\ch]).postln;
		"    OH (Open Hat)   -> Channel %".format(channelAssignment[\oh]).postln;
		"    CC (Crash)      -> Channel %".format(channelAssignment[\cc]).postln;
		"    RC (Ride)       -> Channel %".format(channelAssignment[\rc]).postln;
		"  Effects chain: % slots configured per channel".format(effectsChain.size).postln;
	}

	// Override disconnect to clean up
	disconnect {
		"ClipTR8s: Disconnecting...".postln;
		^super.disconnect;
	}
}
