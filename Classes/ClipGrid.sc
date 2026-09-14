/*
 * ClipGrid
 *
 * Manages the 2D matrix of clip slots across channels.
 * Coordinates MIDI grid presses, LED feedback, and scene launches.
 */

ClipGrid {
	var <numChannels;
	var <numSlots;  // Slots per channel
	var <channels;  // Array of ClipChannel instances
	var <transport;
	var <masterBus;
	var <server;

	*new { |numChannels = 4, numSlots = 8, transport, masterBus, server|
		^super.newCopyArgs(
			numChannels: numChannels,
			numSlots: numSlots,
			channels: nil,
			transport: transport,
			masterBus: masterBus,
			server: server ? Server.default
		).init;
	}

	init {
		// Create channels
		channels = Array.fill(numChannels, { |i|
			ClipChannel.new(
				channelIndex: i,
				numSlots: numSlots,
				transport: transport,
				masterChannel: masterBus.mixerChannel,
				server: server
			);
		});

		"ClipGrid: Initialized % channels × % slots = % total slots"
			.format(numChannels, numSlots, numChannels * numSlots)
			.postln;
	}

	// Get a specific slot
	getSlot { |channelIndex, slotIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			^channel.getSlot(slotIndex);
		}, {
			^nil;
		});
	}

	// Get a specific channel
	getChannel { |channelIndex|
		if (channelIndex < 0 or: { channelIndex >= numChannels }, {
			"ClipGrid: Channel index % out of range (0-%)".format(channelIndex, numChannels - 1).error;
			^nil;
		});
		^channels[channelIndex];
	}

	// Arm a slot for recording
	armSlot { |channelIndex, slotIndex, loopLengthBeats = 4|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.armSlot(slotIndex, loopLengthBeats);
		});
	}

	// Launch a slot (start recording if armed, play if stopped)
	launchSlot { |channelIndex, slotIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.launchSlot(slotIndex);
		});
	}

	// Stop a slot
	stopSlot { |channelIndex, slotIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.stopSlot(slotIndex);
		});
	}

	// Clear a slot
	clearSlot { |channelIndex, slotIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.clearSlot(slotIndex);
		});
	}

	// Start overdubbing on a slot
	overdubSlot { |channelIndex, slotIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.overdubSlot(slotIndex);
		});
	}

	// Stop all slots in a channel (scene stop)
	stopChannel { |channelIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.stopAll;
		});
	}

	// Stop all playing slots in all channels (global stop)
	stopAll {
		channels.do(_.stopAll);
		"ClipGrid: Stopped all playing slots".postln;
	}

	// Launch a scene (all slots at a given index across channels)
	launchScene { |slotIndex|
		channels.do { |channel|
			channel.launchSlot(slotIndex);
		};
		"ClipGrid: Launched scene % (slot % across all channels)".format(slotIndex, slotIndex).postln;
	}

	// Stop a scene
	stopScene { |slotIndex|
		channels.do { |channel|
			var slot = channel.getSlot(slotIndex);
			if (slot.isPlaying or: { slot.isOverdubbing }, {
				var nextBeat = transport.nextQuant;
				slot.stop(nextBeat);
			});
		};
	}

	// Channel controls (delegate to ClipChannel)

	setChannelLevel { |channelIndex, db|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.setLevel(db);
		});
	}

	setChannelPan { |channelIndex, position|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.setPan(position);
		});
	}

	soloChannel { |channelIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.solo;
		});
	}

	muteChannel { |channelIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.mute;
		});
	}

	// Add effect to a channel
	addChannelEffect { |channelIndex, synthDef, args, slot|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channel.addEffect(synthDef, args, slot);
		});
	}

	// Query methods

	// Get all playing slots across the grid
	getAllPlayingSlots {
		var playingSlots = List.new;
		channels.do { |channel, chanIdx|
			channel.slots.do { |slot, slotIdx|
				if (slot.isPlaying or: { slot.isOverdubbing }, {
					playingSlots.add([chanIdx, slotIdx, slot]);
				});
			};
		};
		^playingSlots;
	}

	// Get all recording slots
	getAllRecordingSlots {
		var recordingSlots = List.new;
		channels.do { |channel, chanIdx|
			channel.slots.do { |slot, slotIdx|
				if (slot.isRecording, {
					recordingSlots.add([chanIdx, slotIdx, slot]);
				});
			};
		};
		^recordingSlots;
	}

	// Get grid status summary
	printStatus {
		"=== ClipGrid Status ===".postln;
		"Tempo: % BPM, Time: %/%"
			.format(
				transport.tempo,
				transport.timeSignature[0],
				transport.timeSignature[1]
			).postln;
		"Current beat: %".format(transport.beat.round(0.01)).postln;
		"".postln;

		channels.do { |channel, chanIdx|
			"Channel %:".format(chanIdx).postln;
			channel.slots.do { |slot, slotIdx|
				if (slot.state != \empty, {
					"  Slot %: % (% beats)"
						.format(slotIdx, slot.state, slot.loopLengthBeats ? "?")
						.postln;
				});
			};
		};

		var playing = this.getAllPlayingSlots.size;
		var recording = this.getAllRecordingSlots.size;
		"".postln;
		"Playing: %, Recording: %".format(playing, recording).postln;
		"======================".postln;
	}

	// Cleanup

	free {
		channels.do(_.free);
	}

}
