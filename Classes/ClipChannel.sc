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
	var <effects;  // IdentityDictionary: slot -> running effect instance (from mixerChannel.playfx)
	var <isSoloed;  // ddwMixerChannel has no solo concept -- tracked here; actual
	                // cross-channel silencing is done by ClipGrid:soloChannel/unSoloChannel
	var <hardwareInputIndex;  // Which hardware input this channel reads from (nil = no input)

	*new { |channelIndex, numSlots = 8, transport, masterChannel, server, hardwareInputIndex|
		^super.newCopyArgs(
			channelIndex,             // channelIndex
			numSlots,                 // numSlots
			nil,                      // slots
			nil,                      // mixerChannel
			nil,                      // inputBus
			nil,                      // recorderGroup
			nil,                      // looperGroup
			transport,                // transport
			server ? Server.default,  // server
			nil,                      // effects
			false,                    // isSoloed
			hardwareInputIndex        // hardwareInputIndex (nil = defaults to channelIndex for backward compat)
		).init(masterChannel);
	}

	init { |masterChannel|
		// Default to channelIndex for backward compatibility
		if (hardwareInputIndex.isNil, {
			hardwareInputIndex = channelIndex;
		});
		effects = IdentityDictionary.new;

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

	// Create synth that routes hardware input to mixer channel input (live
	// monitoring) and to this channel's dedicated inputBus (what ClipSlot's
	// recorder/overdub synths actually read from)
	createInputMonitor {
		server.bind {
			var mixerInBus = mixerChannel.inbus;

			Synth(\inputMonitor, [
				\hardwareIn, hardwareInputIndex,  // Use explicit hardware input mapping
				\mixerIn, mixerInBus,
				\recordBus, inputBus
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

	// ddwMixerChannel has no solo support; this just tracks the flag.
	// Use ClipGrid:soloChannel/unSoloChannel for the actual audible effect.
	solo {
		isSoloed = true;
	}

	unSolo {
		isSoloed = false;
	}

	mute {
		mixerChannel.mute(true);
	}

	unMute {
		mixerChannel.mute(false);
	}

	// Effects management. ddwMixerChannel itself has no slot concept -- it just
	// serially chains whatever's playfx'd into its effectgroup (each one
	// ReplaceOut's the channel's inbus in the order added). We layer a slot
	// index on top so callers (e.g. a MIDI controller mapping) can reference
	// "the effect in slot N" without holding onto the Synth themselves.

	addEffect { |synthDef, args, slot = 0|
		if (effects[slot].notNil, { this.removeEffect(slot) });
		effects[slot] = mixerChannel.playfx(synthDef, args);

		"ClipChannel[%]: Added effect % at slot %".format(channelIndex, synthDef, slot).postln;
	}

	removeEffect { |slot|
		var running = effects[slot];
		if (running.notNil, {
			running.free;
			effects.removeAt(slot);
			"ClipChannel[%]: Removed effect at slot %".format(channelIndex, slot).postln;
		});
	}

	// Live-update a running effect's parameter (e.g. from a MIDI CC handler)
	setEffectParam { |slot, param, value|
		var running = effects[slot];
		if (running.notNil, {
			running.set(param, value);
		}, {
			"ClipChannel[%]: No effect at slot % to set % on".format(channelIndex, slot, param).warn;
		});
	}

	getEffect { |slot|
		^effects[slot];
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
		effects.do(_.free);
		mixerChannel.free;
		recorderGroup.free;
		looperGroup.free;
		inputBus.free;
	}

}
