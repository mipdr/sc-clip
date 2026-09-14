/*
 * ClipChannel
 *
 * Manages one input channel/track with multiple clip slots.
 * Wraps a MixerChannel (from ddwMixerChannel quark) for routing and effects.
 */

ClipChannel {
	var <channelIndex;
	var <numSlots;
	var <slots;  // Array of ClipSlot instances
	var <mixerChannel;  // ddwMixerChannel instance
	var <inputBus;  // Hardware input bus
	var <recorderGroup;  // Group for recorder synths
	var <looperGroup;  // Group for player synths
	var <transport;  // ClipTransport reference
	var <server;

	*new { |channelIndex, numSlots = 8, transport, masterChannel, server|
		^super.newCopyArgs(
			channelIndex: channelIndex,
			numSlots: numSlots,
			slots: nil,
			mixerChannel: nil,
			inputBus: nil,
			recorderGroup: nil,
			looperGroup: nil,
			transport: transport,
			server: server ? Server.default
		).init(masterChannel);
	}

	init { |masterChannel|
		// Create node groups for this channel
		server.bind {
			recorderGroup = Group.new(server);
			looperGroup = Group.after(recorderGroup);
		};

		// Hardware input bus (one per channel)
		inputBus = Bus.audio(server, 1);

		// Create MixerChannel for routing and effects
		// Note: ddwMixerChannel requires the MixerChannel class
		mixerChannel = MixerChannel.new(
			("ClipChan" ++ channelIndex).asSymbol,
			server,
			1,  // 1 input channel (mono hardware input)
			2,  // 2 output channels (stereo to master)
			outbus: masterChannel.inbus,  // Route to master channel
			level: 0.dbamp  // Unity gain
		);

		// Create input monitoring synth (hardware in → mixer in)
		this.createInputMonitor;

		// Create clip slots
		slots = Array.fill(numSlots, { |i|
			ClipSlot.new(this, i);
		});

		"ClipChannel[%]: Initialized with % slots".format(channelIndex, numSlots).postln;
	}

	// Create synth that routes hardware input to mixer channel input
	createInputMonitor {
		server.bind {
			var hardwareInBus = channelIndex;  // Hardware input channel index
			var mixerInBus = mixerChannel.inbus;

			Synth(\inputMonitor, [
				\hardwareIn, hardwareInBus,
				\mixerIn, mixerInBus
			], recorderGroup, \addToHead);
		};
	}

	// Get a specific slot
	getSlot { |index|
		if (index < 0 or: { index >= numSlots }, {
			"ClipChannel[%]: Slot index % out of range (0-%)".format(channelIndex, index, numSlots - 1).error;
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
				var nextBeat = transport.nextQuant;
				slot.record(nextBeat);
			}
			{ slot.isStopped } {
				var nextBeat = transport.nextQuant;
				slot.play(nextBeat);
			}
			{ slot.isEmpty } {
				"ClipChannel[%]: Slot % is empty - arm it first".format(channelIndex, slotIndex).warn;
			}
			{
				"ClipChannel[%]: Slot % already active (%)".format(channelIndex, slotIndex, slot.state).warn;
			};
		});
	}

	// Stop a slot
	stopSlot { |slotIndex|
		var slot = this.getSlot(slotIndex);
		if (slot.notNil, {
			if (slot.isPlaying or: { slot.isOverdubbing }, {
				var nextBeat = transport.nextQuant;
				slot.stop(nextBeat);
			}, {
				"ClipChannel[%]: Slot % not playing (%)".format(channelIndex, slotIndex, slot.state).warn;
			});
		});
	}

	// Stop all playing slots in this channel
	stopAll {
		slots.do { |slot|
			if (slot.isPlaying or: { slot.isOverdubbing }, {
				var nextBeat = transport.nextQuant;
				slot.stop(nextBeat);
			});
		};
	}

	// Clear a slot
	clearSlot { |slotIndex|
		var slot = this.getSlot(slotIndex);
		if (slot.notNil, {
			slot.clear;
		});
	}

	// Start overdubbing on a slot
	overdubSlot { |slotIndex|
		var slot = this.getSlot(slotIndex);
		if (slot.notNil, {
			slot.overdub;
		});
	}

	// Mixer controls

	setLevel { |db|
		mixerChannel.level_(db.dbamp);
	}

	setPan { |position|  // -1 to 1
		mixerChannel.pan_(position);
	}

	solo {
		mixerChannel.solo;
	}

	unSolo {
		mixerChannel.unSolo;
	}

	mute {
		mixerChannel.mute;
	}

	unMute {
		mixerChannel.unMute;
	}

	// Effects management (using MixerChannel's effects slots)

	addEffect { |synthDef, args, slot|
		// Use MixerChannel's playFx method to add an effect
		// slot: optional effect slot number (0-3 typically)
		mixerChannel.playFx(synthDef, args, slot);

		"ClipChannel[%]: Added effect % at slot %".format(channelIndex, synthDef, slot).postln;
	}

	removeEffect { |slot|
		mixerChannel.stopFx(slot);

		"ClipChannel[%]: Removed effect at slot %".format(channelIndex, slot).postln;
	}

	// Callback when a slot changes state (for LED updates, etc.)
	slotStateChanged { |slotIndex, newState|
		// Override this in subclasses or set a callback function
		// Default: just post to console
		// "ClipChannel[%]: Slot % -> %".format(channelIndex, slotIndex, newState).postln;
	}

	// Query methods

	getPlayingSlots {
		^slots.select({ |slot| slot.isPlaying or: { slot.isOverdubbing } });
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
		mixerChannel.free;
		recorderGroup.free;
		looperGroup.free;
		inputBus.free;
	}

}
