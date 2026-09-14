/*
 * ClipSlot
 *
 * Individual clip/loop slot with state machine for recording and playback.
 * Manages one buffer, RecordBuf synth, and PlayBuf synth.
 *
 * States: \empty, \armed, \recording, \playing, \overdubbing, \queuedToStop, \stopped
 */

ClipSlot {
	var <state;
	var <buffer;
	var <loopLengthBeats;
	var <loopLengthSamples;  // Computed once, never recalculated (prevents drift)
	var <recorderSynth;
	var <playerSynth;
	var <channel;  // Parent ClipChannel
	var <slotIndex;
	var <server;

	*new { |channel, slotIndex|
		^super.newCopyArgs(
			state: \empty,
			buffer: nil,
			loopLengthBeats: nil,
			loopLengthSamples: nil,
			recorderSynth: nil,
			playerSynth: nil,
			channel: channel,
			slotIndex: slotIndex,
			server: channel.server
		);
	}

	// Arm slot for recording with specified loop length in beats
	arm { |lengthBeats = 4|
		if (state != \empty and: { state != \stopped }, {
			"ClipSlot: Cannot arm - slot is %".format(state).warn;
			^this;
		});

		loopLengthBeats = lengthBeats;

		// Calculate loop length in samples (ONCE - never recalculate!)
		loopLengthSamples = this.calculateLoopLengthSamples(lengthBeats);

		// If buffer doesn't exist, allocate it (async)
		if (buffer.isNil, {
			this.allocateBuffer;
		}, {
			// Buffer exists, can arm immediately
			this.setState(\armed);
		});
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

		"ClipSlot[%,%]: Starting recording..."
			.format(channel.channelIndex, slotIndex)
			.postln;

		// Create recorder synth in the recorder group
		recorderSynth = Synth(\clipRecorder, [
			\inBus, inputBus,
			\bufnum, buffer.bufnum,
			\loop, 1,
			\mode, 0  // 0 = fresh recording
		], channel.recorderGroup, \addToTail);

		this.setState(\recording);

		// Schedule transition to playing after loop completes
		channel.transport.scheduleAfterBeats(loopLengthBeats, {
			this.finishRecording;
		});
	}

	// Internal: finish recording and start playback
	finishRecording {
		if (recorderSynth.notNil, {
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
			"ClipSlot: Cannot play - slot is % (must be stopped)".format(state).warn;
			^this;
		});

		channel.transport.scheduleAtBeat(atBeat, {
			this.startPlayback;
		});
	}

	// Internal: actually start the playback synth
	startPlayback {
		var outputBus = channel.mixerChannel.inbus;  // ddwMixerChannel input bus

		if (buffer.isNil, {
			"ClipSlot: Cannot play - no buffer".error;
			^this;
		});

		"ClipSlot[%,%]: Starting playback"
			.format(channel.channelIndex, slotIndex)
			.postln;

		// Create player synth in the looper group
		playerSynth = Synth(\clipPlayer, [
			\outBus, outputBus,
			\bufnum, buffer.bufnum,
			\rate, 1,
			\loop, 1,
			\gate, 1,
			\amp, 1
		], channel.looperGroup, \addToTail);

		this.setState(\playing);
	}

	// Stop playback (called by transport at quantized time)
	stop { |atBeat|
		if (state != \playing and: { state != \overdubbing }, {
			"ClipSlot: Cannot stop - not playing (state: %)".format(state).warn;
			^this;
		});

		this.setState(\queuedToStop);

		channel.transport.scheduleAtBeat(atBeat, {
			this.stopPlayback;
		});
	}

	// Internal: actually stop the playback synth
	stopPlayback {
		if (playerSynth.notNil, {
			playerSynth.set(\gate, 0);  // Trigger release envelope
			playerSynth = nil;
		});

		if (recorderSynth.notNil, {
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
		if (state != \playing, {
			"ClipSlot: Cannot overdub - not playing".warn;
			^this;
		});

		var inputBus = channel.inputBus;

		"ClipSlot[%,%]: Starting overdub"
			.format(channel.channelIndex, slotIndex)
			.postln;

		// Add recorder synth in overdub mode
		recorderSynth = Synth(\clipRecorder, [
			\inBus, inputBus,
			\bufnum, buffer.bufnum,
			\loop, 1,
			\mode, 2  // 2 = overdub mode (mix new with existing)
		], channel.recorderGroup, \addToTail);

		this.setState(\overdubbing);
	}

	// Stop overdubbing (keep playing)
	stopOverdub {
		if (state != \overdubbing, {
			"ClipSlot: Not overdubbing".warn;
			^this;
		});

		if (recorderSynth.notNil, {
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
		this.stopPlayback;

		if (buffer.notNil, {
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
	hasAudio { ^buffer.notNil }

	// Cleanup
	free {
		this.clear;
	}

}
