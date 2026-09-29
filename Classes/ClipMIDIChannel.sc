/*
 * ClipMIDIChannel
 *
 * Manages one MIDI channel with multiple clip slots.
 * Handles MIDI input (for recording) and MIDI output (for playback).
 *
 * Mirrors the parts of ClipChannel's interface that ClipGrid, controllers
 * and session saving call on every channel: mute/solo work (muting suppresses
 * note-ons), level/pan/effects/overdub are audio-only and just warn.
 */

ClipMIDIChannel {
	var <channelIndex;
	var <numSlots;
	var <slots;  // Array of ClipMIDISlot instances
	var <midiInChannel;  // MIDI input channel number (for recording), nil = any
	var <midiOutChannel;  // MIDI output channel number (for playback)
	var <midiOut;  // MIDIOut instance
	var <transport;  // ClipTransport reference
	var <server;
	var <>slotStateAction;  // Optional callback { |slotIndex, newState| }
	var <midiInSrcID;  // MIDIEndPoint uid to record from, nil = any source
	var <isMuted = false;
	var <isSoloed = false;  // Tracked here; cross-channel silencing is done by ClipGrid:soloChannel

	*new { |channelIndex, numSlots = 8, transport, server, midiInChannel = 0, midiOutChannel = 0, midiOut, midiInSrcID|
		^super.newCopyArgs(
			channelIndex,             // channelIndex
			numSlots,                 // numSlots
			nil,                      // slots
			midiInChannel,            // midiInChannel
			midiOutChannel,           // midiOutChannel
			midiOut,                  // midiOut
			transport,                // transport
			server ? Server.default,  // server
			nil,                      // slotStateAction
			midiInSrcID               // midiInSrcID
		).init;
	}

	init {
		slots = Array.fill(numSlots, { |i|
			ClipMIDISlot.new(this, i);
		});

		"ClipMIDIChannel[%]: Initialized with % slots (MIDI in: % from %, out: %)"
			.format(channelIndex, numSlots, midiInChannel ? "any", midiInSrcID ? "any source", midiOutChannel)
			.postln;
	}

	// Get a specific slot
	getSlot { |index|
		if (index < 0 or: { index >= numSlots }, {
			"ClipMIDIChannel[%]: Slot index % out of range (0-%)"
				.format(channelIndex, index, numSlots - 1).error;
			^nil;
		});
		^slots[index];
	}

	// Arm a slot for recording
	armSlot { |slotIndex, loopLengthBeats = 4|
		var slot = this.getSlot(slotIndex);
		if (slot.notNil, {
			slot.arm(loopLengthBeats);
		});
	}

	// Launch a slot (start recording if armed, start playing if stopped)
	launchSlot { |slotIndex|
		var slot = this.getSlot(slotIndex);
		if (slot.notNil, {
			case
			{ slot.isArmed } {
				slot.record(transport.nextLaunchQuant);
			}
			{ slot.isStopped } {
				slot.play(transport.nextLaunchQuant);
			}
			{ slot.isEmpty } {
				"ClipMIDIChannel[%]: Slot % is empty - write a clip or arm it first".format(channelIndex, slotIndex).warn;
			}
			{
				"ClipMIDIChannel[%]: Slot % already active (%)".format(channelIndex, slotIndex, slot.state).warn;
			};
		});
	}

	// Stop a slot
	stopSlot { |slotIndex|
		var slot = this.getSlot(slotIndex);
		if (slot.notNil, {
			case
			{ slot.isPlaying } {
				slot.stop(transport.nextQuant);
			}
			{ slot.isQueuedToPlay } {
				slot.cancelPlay;
			}
			{
				"ClipMIDIChannel[%]: Slot % not playing (%)".format(channelIndex, slotIndex, slot.state).warn;
			};
		});
	}

	// Stop all playing slots in this channel
	stopAll {
		slots.do { |slot|
			if (slot.isPlaying, { slot.stop(transport.nextQuant) });
			if (slot.isQueuedToPlay, { slot.cancelPlay });
		};
	}

	// Clear a slot
	clearSlot { |slotIndex|
		var slot = this.getSlot(slotIndex);
		if (slot.notNil, {
			slot.clear;
		});
	}

	// Load MIDI clip from notation string
	loadMIDIClip { |slotIndex, notationString, padding = false|
		var slot = this.getSlot(slotIndex);
		if (slot.notNil, {
			^slot.loadFromNotation(notationString, padding);
		});
		^false;
	}

	overdubSlot { |slotIndex|
		"ClipMIDIChannel[%]: MIDI clips don't support overdubbing".format(channelIndex).warn;
	}

	// Callback when a slot changes state (for LED updates, etc.)
	slotStateChanged { |slotIndex, newState|
		slotStateAction.value(slotIndex, newState);
	}

	// Mixer-style controls

	mute {
		isMuted = true;
		slots.do(_.releaseNotes);
	}

	unMute {
		isMuted = false;
	}

	solo {
		isSoloed = true;
	}

	unSolo {
		isSoloed = false;
	}

	setLevel { |db|
		"ClipMIDIChannel[%]: Level is audio-only (set it on the receiving instrument)".format(channelIndex).warn;
	}

	setPan { |position|
		"ClipMIDIChannel[%]: Pan is audio-only (set it on the receiving instrument)".format(channelIndex).warn;
	}

	addEffect { |synthDef, args, slot = 0|
		"ClipMIDIChannel[%]: Effects are audio-only".format(channelIndex).warn;
	}

	mixerChannel { ^nil }

	// Query methods

	getPlayingSlots {
		^slots.select({ |slot| slot.isPlaying });
	}

	getRecordingSlots {
		^slots.select({ |slot| slot.isRecording });
	}

	getArmedSlots {
		^slots.select({ |slot| slot.isArmed });
	}

	// Cleanup

	free {
		slots.do(_.free);
	}

}
