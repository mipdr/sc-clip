/*
 * ClipMIDISlot
 *
 * MIDI clip slot with state machine for recording and playback.
 * Stores MIDI events (note on/off, velocity, timestamp) and plays them back.
 *
 * States: \empty, \armed, \recording, \playing, \stopped, \queuedToPlay, \queuedToStop
 * Note: MIDI clips don't support overdubbing (no \overdubbing state)
 */

ClipMIDISlot {
	var <state;
	var <midiEvents;  // Array of (time: beats, type: \noteOn/\noteOff, note: midi#, vel: 0-127)
	var <loopLengthBeats;
	var <loopStartBeat;  // Beat the loop's current run started at (its phase), nil when not running
	var <channel;  // Parent ClipMIDIChannel
	var <slotIndex;
	var <server;
	var <playRoutine;  // Routine for MIDI playback
	var <recordStartBeat;  // Absolute beat when recording started
	var <activeNotes;  // IdentityDictionary: note# -> press time (for note-off during recording)

	*new { |channel, slotIndex|
		^super.newCopyArgs(
			\empty,      // state
			nil,         // midiEvents
			nil,         // loopLengthBeats
			nil,         // loopStartBeat
			channel,     // channel
			slotIndex,   // slotIndex
			channel.server, // server
			nil,         // playRoutine
			nil,         // recordStartBeat
			nil          // activeNotes
		).init;
	}

	init {
		midiEvents = List.new;
		activeNotes = IdentityDictionary.new;
	}

	// Arm slot for recording with specified loop length in beats
	arm { |lengthBeats = 4|
		if (state != \empty and: { state != \stopped }, {
			"ClipMIDISlot: Cannot arm - slot is %".format(state).warn;
			^this;
		});

		// Re-arming a slot that already has MIDI events: keep the existing
		// loopLengthBeats (same as audio clips - loop length is fixed at first arm)
		if (midiEvents.size > 0, {
			this.setState(\armed);
			^this;
		});

		loopLengthBeats = lengthBeats;

		"ClipMIDISlot[%,%]: Armed for % beats"
			.format(channel.channelIndex, slotIndex, lengthBeats)
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

		// Clear old events if re-recording
		midiEvents.clear;
		activeNotes.clear;

		// Schedule recording to start at the specified beat
		channel.transport.scheduleAtBeat(atBeat, {
			this.startRecording;
		});
	}

	// Internal: actually start recording
	startRecording {
		"ClipMIDISlot[%,%]: Starting MIDI recording..."
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.setState(\recording);

		// Setup MIDI input responder
		this.setupMIDIRecording;

		// Schedule transition to playing after loop completes
		channel.transport.scheduleAfterBeats(loopLengthBeats, {
			this.finishRecording;
		});
	}

	// Setup MIDI recording responder
	setupMIDIRecording {
		var midiInChannel = channel.midiInChannel;
		var transport = channel.transport;

		// Note On
		channel.noteOnFunc = MIDIFunc.noteOn({ |vel, note, chan, src|
			var currentBeat = transport.beat;
			var relativeTime = currentBeat - recordStartBeat;

			// Only record if within loop bounds
			if (relativeTime >= 0 and: { relativeTime < loopLengthBeats }, {
				midiEvents.add((
					time: relativeTime,
					type: \noteOn,
					note: note,
					vel: vel
				));
				activeNotes[note] = relativeTime;

				"ClipMIDISlot: Recorded noteOn - note:% vel:% @ beat:%"
					.format(note, vel, relativeTime.round(0.001))
					.postln;
			});
		}, chan: midiInChannel);

		// Note Off
		channel.noteOffFunc = MIDIFunc.noteOff({ |vel, note, chan, src|
			var currentBeat = transport.beat;
			var relativeTime = currentBeat - recordStartBeat;

			// Only record if within loop bounds
			if (relativeTime >= 0 and: { relativeTime < loopLengthBeats }, {
				midiEvents.add((
					time: relativeTime,
					type: \noteOff,
					note: note,
					vel: 0
				));
				activeNotes.removeAt(note);

				"ClipMIDISlot: Recorded noteOff - note:% @ beat:%"
					.format(note, relativeTime.round(0.001))
					.postln;
			});
		}, chan: midiInChannel);
	}

	// Internal: finish recording and start playback
	finishRecording {
		// Clean up MIDI responders
		if (channel.noteOnFunc.notNil, {
			channel.noteOnFunc.free;
			channel.noteOnFunc = nil;
		});
		if (channel.noteOffFunc.notNil, {
			channel.noteOffFunc.free;
			channel.noteOffFunc = nil;
		});

		// Send note-off for any still-active notes
		activeNotes.keysDo { |note|
			midiEvents.add((
				time: loopLengthBeats,
				type: \noteOff,
				note: note,
				vel: 0
			));
		};
		activeNotes.clear;

		// Sort events by time
		midiEvents = midiEvents.sort({ |a, b| a.time < b.time });

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

		// Never drop a reference to a running routine
		if (playRoutine.notNil, {
			playRoutine.stop;
			playRoutine = nil;
		});

		"ClipMIDISlot[%,%]: Starting MIDI playback (% events)"
			.format(channel.channelIndex, slotIndex, midiEvents.size)
			.postln;

		// Create playback routine
		playRoutine = Routine({
			var midiOut = channel.midiOut;
			var midiOutChannel = channel.midiOutChannel;
			var loopBeat, eventIndex, event, waitBeats, remainingBeats;

			loop {
				loopBeat = 0;
				eventIndex = 0;

				// Play all events in this loop iteration
				while { eventIndex < midiEvents.size } {
					event = midiEvents[eventIndex];
					waitBeats = event.time - loopBeat;

					// Wait until event time
					if (waitBeats > 0, { waitBeats.wait });

					// Send MIDI event
					if (event.type == \noteOn, {
						midiOut.noteOn(midiOutChannel, event.note, event.vel);
					}, {
						midiOut.noteOff(midiOutChannel, event.note, event.vel);
					});

					loopBeat = event.time;
					eventIndex = eventIndex + 1;
				};

				// Wait for remaining loop time
				remainingBeats = loopLengthBeats - loopBeat;
				if (remainingBeats > 0, { remainingBeats.wait });
			};
		});

		// Play on transport clock
		playRoutine.play(channel.transport.clock);

		this.setState(\playing);
	}

	// Stop playback (called by transport at quantized time)
	stop { |atBeat|
		if (state != \playing, {
			"ClipMIDISlot: Cannot stop - not playing (state: %)".format(state).warn;
			^this;
		});

		this.setState(\queuedToStop);

		channel.transport.scheduleAtBeat(atBeat, {
			this.stopPlayback;
		});
	}

	// Internal: actually stop the playback routine
	stopPlayback {
		if (playRoutine.notNil, {
			playRoutine.stop;
			playRoutine = nil;
		});

		// Send all-notes-off
		this.allNotesOff;

		"ClipMIDISlot[%,%]: Stopped"
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.setState(\stopped);
	}

	// Send MIDI all-notes-off
	allNotesOff {
		var midiOut = channel.midiOut;
		var midiOutChannel = channel.midiOutChannel;

		// Send note-off for MIDI notes 0-127
		128.do { |note|
			midiOut.noteOff(midiOutChannel, note, 0);
		};
	}

	// Load MIDI clip from notation string
	loadFromNotation { |notationString, padding = false|
		var parser = ClipMIDINotationParser.new;
		var events, maxTime, clipLength, clipLengthOptions;

		// Parse notation into MIDI events
		events = parser.parse(notationString);

		if (events.isNil or: { events.size == 0 }, {
			"ClipMIDISlot: Failed to parse notation or empty clip".error;
			^this;
		});

		// Calculate clip length from events
		maxTime = events.collect(_.time).maxItem;
		clipLength = maxTime;

		// Apply padding if requested
		if (padding, {
			clipLengthOptions = [1, 2, 4, 8, 16, 32, 64];
			clipLength = clipLengthOptions.detect({ |len| len >= maxTime }) ? 64;
		});

		// Arm with calculated length
		this.arm(clipLength);

		// Set MIDI events
		midiEvents = events;
		loopLengthBeats = clipLength;

		"ClipMIDISlot[%,%]: Loaded % events (% beats%)"
			.format(
				channel.channelIndex,
				slotIndex,
				events.size,
				clipLength,
				if (padding, { ", padded" }, { "" })
			)
			.postln;

		this.setState(\stopped);
	}

	// Clear slot (free events and routine)
	clear {
		this.stopPlayback;

		midiEvents.clear;
		activeNotes.clear;
		loopLengthBeats = nil;
		loopStartBeat = nil;
		recordStartBeat = nil;

		"ClipMIDISlot[%,%]: Cleared"
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.setState(\empty);
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
	isStopped { ^state == \stopped }
	isQueuedToPlay { ^state == \queuedToPlay }
	hasAudio { ^false }  // MIDI slots don't have audio
	hasMIDI { ^midiEvents.size > 0 }

	// Cleanup
	free {
		this.clear;
	}

}
