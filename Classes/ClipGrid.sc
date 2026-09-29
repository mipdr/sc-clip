/*
 * ClipGrid
 *
 * Manages the 2D matrix of clip slots across channels.
 * Coordinates MIDI grid presses, LED feedback, and scene launches.
 */

ClipGrid {
	var <numChannels;
	var <numSlots;  // Slots per channel
	var <channels;  // Array of ClipChannel or ClipMIDIChannel instances
	var <transport;
	var <masterBus;
	var <server;
	var <inputMapping;  // Array: channel index -> hardware input index (audio channels only)
	var <channelConfig;  // Array of (type: \audio or \midi, ...) configs per channel
	var <midiOut;  // MIDIOut instance for MIDI channels

	*new { |numChannels = 4, numSlots = 8, transport, masterBus, server, inputMapping, channelConfig, midiOut|
		^super.newCopyArgs(
			numChannels,              // numChannels
			numSlots,                 // numSlots
			nil,                      // channels
			transport,                // transport
			masterBus,                // masterBus
			server ? Server.default,  // server
			inputMapping,             // inputMapping (nil = default 1:1 mapping for audio channels)
			channelConfig,            // channelConfig (nil = all audio channels)
			midiOut                   // midiOut (required if any MIDI channels)
		).init;
	}

	init {
		// Default to all audio channels if not specified
		if (channelConfig.isNil, {
			channelConfig = Array.fill(numChannels, { |i|
				(type: \audio, hardwareInput: i)
			});
		});

		// Validate channel config
		if (channelConfig.size != numChannels, {
			"ClipGrid: channelConfig size (%) doesn't match numChannels (%)".format(
				channelConfig.size, numChannels
			).error;
			^this;
		});

		// Backward compatibility: support old inputMapping parameter
		if (inputMapping.notNil, {
			"ClipGrid: Using legacy inputMapping parameter".postln;
			channelConfig = Array.fill(numChannels, { |i|
				(type: \audio, hardwareInput: inputMapping[i])
			});
		});

		// Create channels based on config
		channels = Array.fill(numChannels, { |i|
			var config = channelConfig[i];
			var channelType = config[\type] ? \audio;

			if (channelType == \audio, {
				// Audio channel
				var hardwareInput = config[\hardwareInput] ? i;
				ClipChannel.new(
					channelIndex: i,
					numSlots: numSlots,
					transport: transport,
					masterChannel: masterBus.mixerChannel,
					server: server,
					hardwareInputIndex: hardwareInput
				);
			}, {
				// MIDI channel
				var midiInChan = config[\midiInChannel] ? 0;
				var midiOutChan = config[\midiOutChannel] ? 0;

				if (midiOut.isNil, {
					"ClipGrid: MIDI channel % requires midiOut parameter".format(i).error;
					^nil;
				});

				ClipMIDIChannel.new(
					channelIndex: i,
					numSlots: numSlots,
					transport: transport,
					server: server,
					midiInChannel: midiInChan,
					midiOutChannel: midiOutChan,
					midiOut: midiOut,
					midiInSrcID: this.resolveMIDISource(config[\midiInSrc], i)
				);
			});
		});

		// Lets the transport see every clip for \longestClip launch quantization
		transport.clipSource = { channels.collect(_.slots).flatten };

		"ClipGrid: Initialized % channels × % slots = % total slots"
			.format(numChannels, numSlots, numChannels * numSlots)
			.postln;

		// Print channel configuration
		numChannels.do { |i|
			var channel = channels[i];
			var config = channelConfig[i];
			if (config[\type] == \audio, {
				"ClipGrid: Ch% (audio) -> Input%"
					.format(i, config[\hardwareInput] ? i)
					.postln;
			}, {
				"ClipGrid: Ch% (MIDI) -> In:% Out:%"
					.format(i, config[\midiInChannel] ? 0, config[\midiOutChannel] ? 0)
					.postln;
			});
		};
	}

	// MIDI channel config's \midiInSrc -> MIDIEndPoint uid (nil = record from
	// any source). Accepts a uid, or a device name looked up in
	// MIDIClient.sources (e.g. "UMC404HD 192k").
	resolveMIDISource { |src, channelIndex|
		var endPoint;

		if (src.isNil or: { src.isNumber }, { ^src });

		if (MIDIClient.initialized.not, { MIDIClient.init });
		endPoint = MIDIClient.sources.detect({ |ep| ep.device == src });
		if (endPoint.isNil, {
			"ClipGrid: MIDI channel % input device '%' not found -- recording from any source"
				.format(channelIndex, src).warn;
			^nil;
		});
		^endPoint.uid;
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

	// Solos one channel: mutes every other channel and marks this one soloed.
	// ddwMixerChannel has no native solo bus, so it's implemented here as
	// plain cross-channel muting.
	soloChannel { |channelIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channels.do { |ch, i|
				if (i == channelIndex, {
					ch.solo;
					ch.unMute;
				}, {
					ch.unSolo;
					ch.mute;
				});
			};
		});
	}

	// Undoes soloChannel: clears solo and unmutes every channel.
	unSoloChannel { |channelIndex|
		var channel = this.getChannel(channelIndex);
		if (channel.notNil, {
			channels.do { |ch|
				ch.unSolo;
				ch.unMute;
			};
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
		var playing, recording;

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

		playing = this.getAllPlayingSlots.size;
		recording = this.getAllRecordingSlots.size;
		"".postln;
		"Playing: %, Recording: %".format(playing, recording).postln;
		"======================".postln;
	}

	// Cleanup

	free {
		channels.do(_.free);
	}

}
