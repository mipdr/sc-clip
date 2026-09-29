/*
 * ClipMIDIChannel
 *
 * Manages one MIDI channel with multiple clip slots.
 * Handles MIDI input (for recording) and MIDI output (for playback).
 */

ClipMIDIChannel {
	var <channelIndex;
	var <numSlots;
	var <slots;  // Array of ClipMIDISlot instances
	var <midiInChannel;  // MIDI input channel number (for recording)
	var <midiOutChannel;  // MIDI output channel number (for playback)
	var <midiOut;  // MIDIOut instance
	var <transport;  // ClipTransport reference
	var <server;
	var <>slotStateAction;  // Optional callback { |slotIndex, newState| }
	var <noteOnFunc;  // MIDIFunc for note on (managed by slot during recording)
	var <noteOffFunc;  // MIDIFunc for note off (managed by slot during recording)

	*new { |channelIndex, numSlots = 8, transport, server, midiInChannel = 0, midiOutChannel = 0, midiOut|
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
			nil,                      // noteOnFunc
			nil                       // noteOffFunc
		).init;
	}

	init {
		// Create clip slots
		slots = Array.fill(numSlots, { |i|
			ClipMIDISlot.new(this, i);
		});

		"ClipMIDIChannel[%]: Initialized with % slots (MIDI in: %, out: %)"
			.format(channelIndex, numSlots, midiInChannel, midiOutChannel)
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
				var nextBeat = transport.nextLaunchQuant;
				slot.record(nextBeat);
			}
			{ slot.isStopped } {
				var nextBeat = transport.nextLaunchQuant;
				slot.play(nextBeat);
			}
			{ slot.isEmpty } {
				"ClipMIDIChannel[%]: Slot % is empty - arm it first".format(channelIndex, slotIndex).warn;
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
				var nextBeat = transport.nextQuant;
				slot.stop(nextBeat);
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
			if (slot.isPlaying, {
				var nextBeat = transport.nextQuant;
				slot.stop(nextBeat);
			});
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
			slot.loadFromNotation(notationString, padding);
		});
	}

	// Callback when a slot changes state (for LED updates, etc.)
	slotStateChanged { |slotIndex, newState|
		slotStateAction.value(slotIndex, newState);
	}

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
		if (noteOnFunc.notNil, { noteOnFunc.free });
		if (noteOffFunc.notNil, { noteOffFunc.free });
	}

}
