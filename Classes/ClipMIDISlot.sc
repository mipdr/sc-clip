/*
 * ClipMIDISlot
 *
 * MIDI clip slot with state machine for recording and playback.
 * Stores MIDI events (note on/off, velocity, timestamp) and plays them back.
 *
 * States: \empty, \armed, \recording, \playing, \stopped, \queuedToPlay, \queuedToStop
 * Note: MIDI clips don't support overdubbing (no \overdubbing state)
 *
 * Events are Events of (time: beats from loop start, type: \noteOn/\noteOff,
 * note: 0-127, vel: 0-127), kept sorted by time.
 */

ClipMIDISlot {
	var <state;
	var <midiEvents;  // List of events, sorted by time
	var <loopLengthBeats;
	var <loopStartBeat;  // Beat the loop's current run started at (its phase), nil when not running
	var <channel;  // Parent ClipMIDIChannel
	var <slotIndex;
	var <server;
	var <playRoutine;  // Routine for MIDI playback
	var <recordStartBeat;  // Absolute beat when recording started
	var <activeNotes;  // IdentitySet of notes held down during recording
	var <soundingNotes;  // IdentitySet of notes this slot has sent a note-on for (and no note-off yet)
	var noteOnFunc, noteOffFunc;  // Recording responders, only alive while \recording

	*new { |channel, slotIndex|
		^super.newCopyArgs(
			\empty,      // state
			nil,         // midiEvents
			nil,         // loopLengthBeats
			nil,         // loopStartBeat
			channel,     // channel
			slotIndex,   // slotIndex
			channel.server // server
		).init;
	}

	init {
		midiEvents = List.new;
		activeNotes = IdentitySet.new;
		soundingNotes = IdentitySet.new;
	}

	// Arm slot for recording with specified loop length in beats
	arm { |lengthBeats = 4|
		if (state != \empty and: { state != \stopped }, {
			"ClipMIDISlot: Cannot arm - slot is %".format(state).warn;
			^this;
		});

		// Re-arming a slot that already has MIDI events: keep the existing
		// loopLengthBeats (same as audio clips - loop length is fixed at first arm)
		if (midiEvents.size == 0, { loopLengthBeats = lengthBeats });

		"ClipMIDISlot[%,%]: Armed for % beats (recording replaces any existing events)"
			.format(channel.channelIndex, slotIndex, loopLengthBeats)
			.postln;

		this.setState(\armed);
	}

	// Start recording (called by transport at quantized time)
	record { |atBeat|
		if (state != \armed, {
			"ClipMIDISlot: Cannot record - not armed (state: %)".format(state).warn;
			^this;
		});

		loopStartBeat = atBeat;
		recordStartBeat = atBeat;

		channel.transport.scheduleAtBeat(atBeat, {
			// Skip if the slot was cleared (or recording already started
			// from a double launch) while queued
			if (state == \armed, { this.startRecording });
		});
	}

	// Internal: actually start recording
	startRecording {
		"ClipMIDISlot[%,%]: Starting MIDI recording (% beats)..."
			.format(channel.channelIndex, slotIndex, loopLengthBeats)
			.postln;

		midiEvents = List.new;
		activeNotes.clear;
		this.setState(\recording);
		this.setupMIDIRecording;

		channel.transport.scheduleAfterBeats(loopLengthBeats, {
			if (state == \recording, { this.finishRecording });
		});
	}

	// Setup MIDI recording responders, filtered by the channel's MIDI input
	// channel and (if set) source device, so e.g. Launchpad button presses
	// aren't recorded as notes
	setupMIDIRecording {
		this.freeRecordFuncs;

		noteOnFunc = MIDIFunc.noteOn({ |vel, note|
			// Running-status note-on with velocity 0 is a note-off
			this.recordEvent(if (vel > 0, \noteOn, \noteOff), note, vel);
		}, chan: channel.midiInChannel, srcID: channel.midiInSrcID);

		noteOffFunc = MIDIFunc.noteOff({ |vel, note|
			this.recordEvent(\noteOff, note, 0);
		}, chan: channel.midiInChannel, srcID: channel.midiInSrcID);
	}

	recordEvent { |type, note, vel|
		var relativeTime = channel.transport.beat - recordStartBeat;

		if (state != \recording or: { relativeTime < 0 } or: { relativeTime >= loopLengthBeats }, { ^this });

		// A note-off for a note pressed before recording started has no
		// matching note-on in the clip -- drop it
		if (type == \noteOff and: { activeNotes.includes(note).not }, { ^this });

		midiEvents.add((time: relativeTime, type: type, note: note, vel: if (type == \noteOn, vel, 0)));
		if (type == \noteOn, { activeNotes.add(note) }, { activeNotes.remove(note) });

		"ClipMIDISlot[%,%]: Recorded % note:% vel:% @ beat:%"
			.format(channel.channelIndex, slotIndex, type, note, vel, relativeTime.round(0.001))
			.postln;
	}

	freeRecordFuncs {
		noteOnFunc !? { noteOnFunc.free; noteOnFunc = nil };
		noteOffFunc !? { noteOffFunc.free; noteOffFunc = nil };
	}

	// Internal: finish recording and start playback
	finishRecording {
		this.freeRecordFuncs;

		// Close any notes still held at the loop end
		activeNotes.do { |note|
			midiEvents.add((time: loopLengthBeats, type: \noteOff, note: note, vel: 0));
		};
		activeNotes.clear;

		if (midiEvents.isEmpty, {
			"ClipMIDISlot[%,%]: Recording finished with no notes -- slot left empty"
				.format(channel.channelIndex, slotIndex)
				.warn;
			this.clear;
			^this;
		});

		midiEvents = ClipMIDISlot.sortEvents(midiEvents);

		"ClipMIDISlot[%,%]: Recording finished (% events), starting playback"
			.format(channel.channelIndex, slotIndex, midiEvents.size)
			.postln;

		this.startPlayback;
	}

	// Start playback (called by transport at quantized time)
	play { |atBeat|
		if (state != \stopped, {
			"ClipMIDISlot: Cannot play - slot is % (must be stopped)".format(state).warn;
			^this;
		});

		// Leave \stopped right away to prevent double-launching
		this.setState(\queuedToPlay);
		loopStartBeat = atBeat;

		channel.transport.scheduleAtBeat(atBeat, {
			// Skip if the slot was cleared/changed while queued
			if (state == \queuedToPlay, { this.startPlayback });
		});
	}

	// Cancel a queued play before it starts
	cancelPlay {
		if (state == \queuedToPlay, { this.setState(\stopped) });
	}

	// Internal: actually start the playback routine
	startPlayback {
		if (midiEvents.size == 0, {
			"ClipMIDISlot: Cannot play - no MIDI events".error;
			^this;
		});

		this.stopRoutine;

		// Send MIDI with the same latency audio clips get from their server
		// bundles, so MIDI and audio clips launched together line up
		channel.midiOut.latency = server.latency;

		"ClipMIDISlot[%,%]: Starting MIDI playback (% events)"
			.format(channel.channelIndex, slotIndex, midiEvents.size)
			.postln;

		playRoutine = Routine({
			var events, length, loopBeat;

			loop {
				// Snapshot per loop iteration, so a clip rewritten while
				// playing (loadFromNotation) switches over at the loop boundary
				events = midiEvents.copy;
				length = loopLengthBeats;
				loopBeat = 0;
				loopStartBeat = channel.transport.beat;

				events.do { |event|
					if (event.time > loopBeat, {
						(event.time - loopBeat).wait;
						loopBeat = event.time;
					});
					this.sendEvent(event);
				};

				(length - loopBeat).max(0).wait;
			};
		});

		playRoutine.play(channel.transport.clock);

		this.setState(\playing);
	}

	sendEvent { |event|
		var midiOut = channel.midiOut;
		var midiOutChannel = channel.midiOutChannel;

		if (event.type == \noteOn, {
			if (channel.isMuted.not, {
				midiOut.noteOn(midiOutChannel, event.note, event.vel);
				soundingNotes.add(event.note);
			});
		}, {
			if (soundingNotes.includes(event.note), {
				midiOut.noteOff(midiOutChannel, event.note, 0);
				soundingNotes.remove(event.note);
			});
		});
	}

	// Stop playback (called by transport at quantized time)
	stop { |atBeat|
		if (state != \playing, {
			"ClipMIDISlot: Cannot stop - not playing (state: %)".format(state).warn;
			^this;
		});

		this.setState(\queuedToStop);

		channel.transport.scheduleAtBeat(atBeat, {
			if (state == \queuedToStop, { this.stopPlayback });
		});
	}

	// Internal: actually stop the playback routine
	stopPlayback {
		this.stopRoutine;
		loopStartBeat = nil;

		"ClipMIDISlot[%,%]: Stopped"
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.setState(\stopped);
	}

	stopRoutine {
		playRoutine !? { playRoutine.stop; playRoutine = nil };
		this.releaseNotes;
	}

	// Note-off for every note this slot left sounding (on stop, mute, clear)
	releaseNotes {
		soundingNotes.do { |note|
			channel.midiOut.noteOff(channel.midiOutChannel, note, 0);
		};
		soundingNotes.clear;
	}

	// Load MIDI clip from notation string. The loop length is the summed
	// duration of every token (rests included), rounded up to the next of
	// 1, 2, 4, ... 64 beats when padding is true. Writing into a playing slot
	// swaps the clip at its next loop boundary. Returns true on success.
	loadFromNotation { |notationString, padding = false|
		var parser = ClipMIDINotationParser.new;
		var events, clipLength;

		if (state == \recording, {
			"ClipMIDISlot[%,%]: Cannot load notation while recording".format(channel.channelIndex, slotIndex).warn;
			^false;
		});

		events = parser.parse(notationString);

		if (events.isNil or: { events.isEmpty }, {
			"ClipMIDISlot[%,%]: Failed to parse notation or clip has no notes: \"%\""
				.format(channel.channelIndex, slotIndex, notationString).error;
			^false;
		});

		clipLength = parser.totalBeats;
		if (padding, {
			clipLength = [1, 2, 4, 8, 16, 32, 64].detect({ |len| len >= clipLength }) ? clipLength;
		});

		this.setEvents(events, clipLength);

		"ClipMIDISlot[%,%]: Loaded % events (% beats%)"
			.format(channel.channelIndex, slotIndex, events.size, clipLength, if (padding, ", padded", ""))
			.postln;

		^true;
	}

	// Replace the clip's events and loop length. Note-offs past the loop end
	// (legato > 1 on the last note) are pulled back to it.
	setEvents { |events, lengthBeats|
		midiEvents = ClipMIDISlot.sortEvents(events.collect({ |event|
			event.copy.put(\time, event.time.min(lengthBeats))
		}));
		loopLengthBeats = lengthBeats;

		// Empty/armed slots become launchable; running ones keep running and
		// pick up the new events at the next loop boundary
		if ([\empty, \armed].includes(state), { this.setState(\stopped) });
	}

	*sortEvents { |events|
		^List.newFrom(events).sort({ |a, b|
			(a.time < b.time) or: { a.time == b.time and: { a.type == \noteOff } }
		});
	}

	// Clear slot (free events and routine)
	clear {
		this.freeRecordFuncs;
		this.stopRoutine;

		midiEvents = List.new;
		activeNotes.clear;
		loopLengthBeats = nil;
		loopStartBeat = nil;
		recordStartBeat = nil;

		"ClipMIDISlot[%,%]: Cleared"
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.setState(\empty);
	}

	// Session saving: plain arrays so the data survives writeArchive
	asSessionData {
		^Dictionary[
			\hasAudio -> false,
			\hasMIDI -> this.hasMIDI,
			\loopLengthBeats -> loopLengthBeats,
			\midiEvents -> midiEvents.collect({ |ev| [ev.time, ev.type, ev.note, ev.vel] }).asArray
		];
	}

	restoreSessionData { |data|
		if (data[\hasMIDI] == true, {
			this.setEvents(
				data[\midiEvents].collect({ |ev| (time: ev[0], type: ev[1], note: ev[2], vel: ev[3]) }),
				data[\loopLengthBeats]
			);
		});
	}

	// Set state and trigger callbacks
	setState { |newState|
		var oldState = state;
		state = newState;

		"ClipMIDISlot[%,%]: % -> %"
			.format(channel.channelIndex, slotIndex, oldState, newState)
			.postln;

		// Notify channel/grid of state change (for LED updates, etc.)
		channel.slotStateChanged(slotIndex, newState);
	}

	// Query methods
	isEmpty { ^state == \empty }
	isArmed { ^state == \armed }
	isRecording { ^state == \recording }
	isPlaying { ^state == \playing }
	isOverdubbing { ^false }  // MIDI slots don't overdub
	isStopped { ^state == \stopped }
	isQueuedToPlay { ^state == \queuedToPlay }
	hasAudio { ^false }  // MIDI slots don't have audio
	hasMIDI { ^midiEvents.size > 0 }
	hasContent { ^this.hasMIDI }

	// Cleanup
	free {
		this.clear;
	}

}
