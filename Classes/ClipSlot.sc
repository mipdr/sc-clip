/*
 * ClipSlot
 *
 * Individual clip/loop slot with state machine for recording and playback.
 * Manages one buffer, RecordBuf synth, and PlayBuf synth.
 *
 * States: \empty, \armed, \recording, \playing, \overdubbing, \queuedToPlay, \queuedToStop, \stopped
 */

ClipSlot {
	var <state;
	var <>buffer;
	var <>loopLengthBeats;
	var <>loopLengthSamples;  // Computed once, never recalculated (prevents drift)
	var <recorderSynth;
	var <playerSynth;
	var <channel;  // Parent ClipChannel
	var <slotIndex;
	var <server;

	*new { |channel, slotIndex|
		^super.newCopyArgs(
			\empty,      // state
			nil,         // buffer
			nil,         // loopLengthBeats
			nil,         // loopLengthSamples
			nil,         // recorderSynth
			nil,         // playerSynth
			channel,     // channel
			slotIndex,   // slotIndex
			channel.server // server
		);
	}

	// Arm slot for recording with specified loop length in beats
	arm { |lengthBeats = 4|
		if (state != \empty and: { state != \stopped }, {
			"ClipSlot: Cannot arm - slot is %".format(state).warn;
			^this;
		});

		// Re-arming a slot that already has a buffer (recording over/
		// replacing previous material): loopLengthBeats/Samples were fixed
		// at the FIRST arm and must not change now -- the buffer itself is
		// never reallocated, so accepting a new lengthBeats here (e.g. the
		// caller's default) would desync the recording window from the
		// buffer's actual size, recording over only part of it.
		if (buffer.notNil, {
			this.setState(\armed);
			^this;
		});

		loopLengthBeats = lengthBeats;

		// Calculate loop length in samples (ONCE - never recalculate!)
		loopLengthSamples = this.calculateLoopLengthSamples(lengthBeats);

		this.allocateBuffer;
	}

	// Calculate loop length in samples based on current tempo
	// This is called ONCE when arming - the result is cached
	calculateLoopLengthSamples { |lengthBeats|
		var tempo = channel.transport.tempo;
		var sampleRate = server.sampleRate;
		var lengthSeconds = lengthBeats * 60 / tempo;
		var lengthSamples = (lengthSeconds * sampleRate).asInteger;

		"ClipSlot[%,%]: Loop length = % beats = % samples @ % BPM"
			.format(channel.channelIndex, slotIndex, lengthBeats, lengthSamples, tempo)
			.postln;

		^lengthSamples;
	}

	// Allocate buffer asynchronously
	allocateBuffer {
		"ClipSlot[%,%]: Allocating buffer (% samples)..."
			.format(channel.channelIndex, slotIndex, loopLengthSamples)
			.postln;

		buffer = Buffer.alloc(
			server: server,
			numFrames: loopLengthSamples,
			numChannels: 1,
			completionMessage: { |buf|
				"ClipSlot[%,%]: Buffer % ready"
					.format(channel.channelIndex, slotIndex, buf.bufnum)
					.postln;
				ClipDebugLogger.logBufferAlloc(
					channel.channelIndex,
					slotIndex,
					loopLengthSamples,
					buf.bufnum
				);
				this.setState(\armed);
			}
		);
	}

	// Start recording (called by transport at quantized time)
	record { |atBeat|
		if (state != \armed, {
			"ClipSlot: Cannot record - not armed (state: %)".format(state).warn;
			^this;
		});

		if (buffer.isNil, {
			"ClipSlot: Cannot record - buffer not ready".error;
			^this;
		});

		// Schedule recording to start at the specified beat
		channel.transport.scheduleAtBeat(atBeat, {
			this.startRecording;
		});
	}

	// Internal: actually start the recording synth
	startRecording {
		var inputBus = channel.inputBus;
		var args;

		"ClipSlot[%,%]: Starting recording..."
			.format(channel.channelIndex, slotIndex)
			.postln;

		args = [
			\inBus, inputBus,
			\bufnum, buffer.bufnum,
			\loop, 1,
			\mode, 0  // 0 = fresh recording
		];

		// Create recorder synth in the recorder group
		recorderSynth = Synth(\clipRecorder, args, channel.recorderGroup, \addToTail);

		ClipDebugLogger.logSynthCreate(
			\clipRecorder,
			channel.channelIndex,
			slotIndex,
			recorderSynth,
			args
		);
		ClipDebugLogger.logRecordStart(
			channel.channelIndex,
			slotIndex,
			loopLengthBeats,
			buffer.bufnum
		);

		this.setState(\recording);

		// Schedule transition to playing after loop completes
		ClipDebugLogger.logSchedule(
			\finishRecording,
			channel.transport.beat + loopLengthBeats,
			channel.channelIndex,
			slotIndex,
			"after % beats".format(loopLengthBeats)
		);
		channel.transport.scheduleAfterBeats(loopLengthBeats, {
			this.finishRecording;
		});
	}

	// Internal: finish recording and start playback
	finishRecording {
		if (recorderSynth.notNil, {
			ClipDebugLogger.logSynthFree(
				\clipRecorder,
				channel.channelIndex,
				slotIndex,
				recorderSynth,
				"recording finished"
			);
			ClipDebugLogger.logRecordEnd(
				channel.channelIndex,
				slotIndex,
				loopLengthBeats * 60 / channel.transport.tempo
			);
			recorderSynth.free;
			recorderSynth = nil;
		});

		"ClipSlot[%,%]: Recording finished, starting playback"
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.startPlayback;
	}

	// Start playback (called by transport at quantized time)
	play { |atBeat|
		if (state != \stopped, {
			ClipDebugLogger.logWarning(
				\slot,
				"Cannot play slot[%,%] - wrong state".format(channel.channelIndex, slotIndex),
				"state: % (expected: stopped)".format(state)
			);
			"ClipSlot: Cannot play - slot is % (must be stopped)".format(state).warn;
			^this;
		});

		// Leave \stopped right away: otherwise a second launch before the
		// quantized start passes the check above again and schedules a second
		// player, orphaning the first (it keeps looping, untracked, after stop).
		this.setState(\queuedToPlay);

		ClipDebugLogger.logSchedule(
			\startPlayback,
			atBeat,
			channel.channelIndex,
			slotIndex,
			"queued to play"
		);
		channel.transport.scheduleAtBeat(atBeat, {
			// Skip if the slot was cleared/changed while queued
			if (state == \queuedToPlay, { this.startPlayback });
		});
	}

	// Cancel a queued play before it starts (its scheduled start checks state)
	cancelPlay {
		if (state == \queuedToPlay, { this.setState(\stopped) });
	}

	// Internal: actually start the playback synth
	startPlayback {
		var outputBus = channel.mixerChannel.inbus;  // ddwMixerChannel input bus
		var args;

		if (buffer.isNil, {
			ClipDebugLogger.logError(
				\slot,
				"Cannot play slot[%,%] - no buffer".format(channel.channelIndex, slotIndex),
				"state: %".format(state)
			);
			"ClipSlot: Cannot play - no buffer".error;
			^this;
		});

		"ClipSlot[%,%]: Starting playback"
			.format(channel.channelIndex, slotIndex)
			.postln;

		// Never drop a reference to a running player -- it would keep looping
		if (playerSynth.notNil, {
			ClipDebugLogger.logWarning(
				\slot,
				"Orphan player detected for slot[%,%]".format(channel.channelIndex, slotIndex),
				"nodeID: % - freeing before creating new player".format(playerSynth.nodeID)
			);
			playerSynth.set(\gate, 0);
			ClipDebugLogger.logSynthFree(
				\clipPlayer,
				channel.channelIndex,
				slotIndex,
				playerSynth,
				"orphan cleanup"
			);
		});

		args = [
			\outBus, outputBus,
			\bufnum, buffer.bufnum,
			\rate, 1,
			\loop, 1,
			\gate, 1,
			\amp, 1
		];

		// Create player synth in the looper group
		playerSynth = Synth(\clipPlayer, args, channel.looperGroup, \addToTail);

		ClipDebugLogger.logSynthCreate(
			\clipPlayer,
			channel.channelIndex,
			slotIndex,
			playerSynth,
			args
		);
		ClipDebugLogger.logPlayStart(
			channel.channelIndex,
			slotIndex,
			buffer.bufnum
		);

		this.setState(\playing);
	}

	// Stop playback (called by transport at quantized time)
	stop { |atBeat|
		if (state != \playing and: { state != \overdubbing }, {
			ClipDebugLogger.logWarning(
				\slot,
				"Cannot stop slot[%,%] - wrong state".format(channel.channelIndex, slotIndex),
				"state: % (expected: playing or overdubbing)".format(state)
			);
			"ClipSlot: Cannot stop - not playing (state: %)".format(state).warn;
			^this;
		});

		this.setState(\queuedToStop);

		ClipDebugLogger.logSchedule(
			\stopPlayback,
			atBeat,
			channel.channelIndex,
			slotIndex,
			"queued to stop"
		);
		channel.transport.scheduleAtBeat(atBeat, {
			this.stopPlayback;
		});
	}

	// Internal: actually stop the playback synth
	stopPlayback {
		if (playerSynth.notNil, {
			ClipDebugLogger.logSynthFree(
				\clipPlayer,
				channel.channelIndex,
				slotIndex,
				playerSynth,
				"normal stop"
			);
			playerSynth.set(\gate, 0);  // Trigger release envelope
			playerSynth = nil;
		});

		if (recorderSynth.notNil, {
			ClipDebugLogger.logSynthFree(
				\clipRecorder,
				channel.channelIndex,
				slotIndex,
				recorderSynth,
				"stopped while overdubbing"
			);
			recorderSynth.free;
			recorderSynth = nil;
		});

		"ClipSlot[%,%]: Stopped"
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.setState(\stopped);
	}

	// Start overdubbing (record + play simultaneously)
	overdub {
		var inputBus;
		var args;

		if (state != \playing, {
			ClipDebugLogger.logWarning(
				\slot,
				"Cannot overdub slot[%,%] - wrong state".format(channel.channelIndex, slotIndex),
				"state: % (expected: playing)".format(state)
			);
			"ClipSlot: Cannot overdub - not playing".warn;
			^this;
		});

		inputBus = channel.inputBus;

		"ClipSlot[%,%]: Starting overdub"
			.format(channel.channelIndex, slotIndex)
			.postln;

		args = [
			\inBus, inputBus,
			\bufnum, buffer.bufnum,
			\loop, 1,
			\mode, 2  // 2 = overdub mode (mix new with existing)
		];

		// Add recorder synth in overdub mode
		recorderSynth = Synth(\clipRecorder, args, channel.recorderGroup, \addToTail);

		ClipDebugLogger.logSynthCreate(
			\clipRecorder,
			channel.channelIndex,
			slotIndex,
			recorderSynth,
			args
		);

		this.setState(\overdubbing);
	}

	// Stop overdubbing (keep playing)
	stopOverdub {
		if (state != \overdubbing, {
			ClipDebugLogger.logWarning(
				\slot,
				"Cannot stop overdub on slot[%,%] - wrong state".format(channel.channelIndex, slotIndex),
				"state: % (expected: overdubbing)".format(state)
			);
			"ClipSlot: Not overdubbing".warn;
			^this;
		});

		if (recorderSynth.notNil, {
			ClipDebugLogger.logSynthFree(
				\clipRecorder,
				channel.channelIndex,
				slotIndex,
				recorderSynth,
				"overdub stopped"
			);
			recorderSynth.free;
			recorderSynth = nil;
		});

		"ClipSlot[%,%]: Stopped overdub, still playing"
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.setState(\playing);
	}

	// Clear slot (free buffer and synths)
	clear {
		var bufnum = if (buffer.notNil, { buffer.bufnum }, { nil });

		this.stopPlayback;

		if (buffer.notNil, {
			ClipDebugLogger.logBufferFree(
				channel.channelIndex,
				slotIndex,
				bufnum
			);
			buffer.free;
			buffer = nil;
		});

		loopLengthBeats = nil;
		loopLengthSamples = nil;

		"ClipSlot[%,%]: Cleared"
			.format(channel.channelIndex, slotIndex)
			.postln;

		this.setState(\empty);
	}

	// Set state and trigger callbacks
	setState { |newState|
		var oldState = state;
		state = newState;

		"ClipSlot[%,%]: % -> %"
			.format(channel.channelIndex, slotIndex, oldState, newState)
			.postln;

		ClipDebugLogger.logSlotState(
			channel.channelIndex,
			slotIndex,
			oldState,
			newState,
			"buffer: %, playerSynth: %, recorderSynth: %".format(
				if (buffer.notNil, { buffer.bufnum }, { "nil" }),
				if (playerSynth.notNil, { playerSynth.nodeID }, { "nil" }),
				if (recorderSynth.notNil, { recorderSynth.nodeID }, { "nil" })
			)
		);

		// Notify channel/grid of state change (for LED updates, etc.)
		channel.slotStateChanged(slotIndex, newState);
	}

	// Query methods
	isEmpty { ^state == \empty }
	isArmed { ^state == \armed }
	isRecording { ^state == \recording }
	isPlaying { ^state == \playing }
	isOverdubbing { ^state == \overdubbing }
	isStopped { ^state == \stopped }
	isQueuedToPlay { ^state == \queuedToPlay }
	hasAudio { ^buffer.notNil }

	// Cleanup
	free {
		this.clear;
	}

}
