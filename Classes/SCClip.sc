/*
 * SCClip
 *
 * Main class for the SC-Clip system.
 * Coordinates all subsystems: transport, grid, channels, master bus.
 */

SCClip {
	var <server;
	var <transport;
	var <masterBus;
	var <grid;
	var <numChannels;
	var <numSlots;

	*new { |numChannels = 4, numSlots = 8, server|
		^super.newCopyArgs(
			server ? Server.default,  // server
			nil,                      // transport
			nil,                      // masterBus
			nil,                      // grid
			numChannels,              // numChannels
			numSlots                  // numSlots
		);
	}

	// Boot and initialize the system
	boot { |options, action|
		var bootAction = {
			this.init;
			action.value(this);
		};

		if (server.serverRunning.not, {
			// Apply server options if provided
			if (options.notNil, {
				this.applyServerOptions(options);
			});

			server.waitForBoot(bootAction);
		}, {
			// Server already running
			bootAction.value;
		});
	}

	// Apply server options
	applyServerOptions { |options|
		options.keysValuesDo { |key, value|
			server.options.perform(key.asSetter, value);
		};

		"SCClip: Applied server options".postln;
	}

	// Initialize all subsystems
	init {
		"SCClip: Initializing...".postln;

		// Create transport
		transport = ClipTransport.new(
			tempo: 120,
			beatsPerBar: 4,
			timeSignature: [4, 4],
			server: server
		);

		// Create master bus
		masterBus = ClipMasterBus.new(server);

		// Add mastering chain
		masterBus.addMasteringChain;

		// Create grid with channels
		grid = ClipGrid.new(
			numChannels: numChannels,
			numSlots: numSlots,
			transport: transport,
			masterBus: masterBus,
			server: server
		);

		"SCClip: Initialized (% channels × % slots)".format(numChannels, numSlots).postln;
		"SCClip: Tempo = % BPM, Time signature = %/%"
			.format(transport.tempo, transport.timeSignature[0], transport.timeSignature[1])
			.postln;
	}

	// Convenience methods (delegate to subsystems)

	// Transport controls
	setTempo { |bpm|
		transport.setTempo(bpm);
	}

	setTimeSignature { |numerator, denominator|
		transport.setTimeSignature(numerator, denominator);
	}

	setQuantization { |beats|
		transport.setQuantization(beats);
	}

	// Grid controls
	armSlot { |channelIndex, slotIndex, loopLengthBeats = 4|
		grid.armSlot(channelIndex, slotIndex, loopLengthBeats);
	}

	launchSlot { |channelIndex, slotIndex|
		grid.launchSlot(channelIndex, slotIndex);
	}

	stopSlot { |channelIndex, slotIndex|
		grid.stopSlot(channelIndex, slotIndex);
	}

	clearSlot { |channelIndex, slotIndex|
		grid.clearSlot(channelIndex, slotIndex);
	}

	overdubSlot { |channelIndex, slotIndex|
		grid.overdubSlot(channelIndex, slotIndex);
	}

	stopChannel { |channelIndex|
		grid.stopChannel(channelIndex);
	}

	stopAll {
		grid.stopAll;
	}

	launchScene { |slotIndex|
		grid.launchScene(slotIndex);
	}

	// Channel controls
	setChannelLevel { |channelIndex, db|
		grid.setChannelLevel(channelIndex, db);
	}

	setChannelPan { |channelIndex, position|
		grid.setChannelPan(channelIndex, position);
	}

	soloChannel { |channelIndex|
		grid.soloChannel(channelIndex);
	}

	muteChannel { |channelIndex|
		grid.muteChannel(channelIndex);
	}

	addChannelEffect { |channelIndex, synthDef, args, slot|
		grid.addChannelEffect(channelIndex, synthDef, args, slot);
	}

	// Master controls
	setMasterLevel { |db|
		masterBus.setMasterLevel(db);
	}

	setMasterEQ { |loFreq, loGain, midFreq, midGain, midQ, hiFreq, hiGain|
		masterBus.setEQ(loFreq, loGain, midFreq, midGain, midQ, hiFreq, hiGain);
	}

	setMasterCompressor { |thresh, ratio, attack, release, makeupGain|
		masterBus.setCompressor(thresh, ratio, attack, release, makeupGain);
	}

	setMasterLimiter { |ceiling, dur|
		masterBus.setLimiter(ceiling, dur);
	}

	// Sync methods
	enableLink {
		transport.enableLink;
	}

	enableMIDIClock { |midiOut|
		transport.enableMIDIClock(midiOut);
	}

	disableMIDIClock {
		transport.disableMIDIClock;
	}

	useInternalClock {
		transport.useInternalClock;
	}

	// Metronome methods
	enableMetronome { |amp = 0.3|
		transport.enableMetronome(amp);
	}

	disableMetronome {
		transport.disableMetronome;
	}

	setMetronomeVolume { |amp|
		transport.setMetronomeVolume(amp);
	}

	// Query methods
	printStatus {
		grid.printStatus;
	}

	// Metering
	meterMaster {
		masterBus.mixerChannel.play;  // Play master to speakers and enable metering
		"SCClip: Master bus metering enabled (playing to speakers)".postln;
	}

	// MIDI Grid Controller

	connectController { |controllerType, deviceName, numRows, numCols|
		var controller;

		if (controllerType.isNil, {
			"SCClip.connectController: controllerType is required (e.g. \\launchpadMini)".error;
			^nil;
		});

		case
		{ controllerType == \launchpadMini } {
			controller = ClipLaunchpadMini.new(
				grid,
				transport,
				numRows ? 4,   // Default 4 rows for clips
				numCols ? 8    // Default 8 columns (channels)
			);
			controller.connect(deviceName ? "Launchpad Mini");
		}
		{
			"Unknown controller type: %".format(controllerType).error;
			"Available controller types: \\launchpadMini".postln;
			^nil;
		};

		^controller;
	}

	// Cleanup
	shutdown {
		"SCClip: Shutting down...".postln;

		grid.free;
		masterBus.free;
		transport.free;

		"SCClip: Shutdown complete".postln;
	}

	free {
		this.shutdown;
	}

}
