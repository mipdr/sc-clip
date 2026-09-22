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

	unSoloChannel { |channelIndex|
		grid.unSoloChannel(channelIndex);
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

	// ===== Session Saving/Loading =====

	/*
	 * Save session to disk using SuperCollider's idiomatic approach:
	 * - Session metadata (tempo, mixer settings, clip states) → .scd file via Dictionary.writeArchive
	 * - Audio buffers → .wav files via Buffer.write
	 *
	 * Directory structure:
	 * <path>/
	 *   session.scd          # Metadata dictionary
	 *   channel_0_slot_0.wav # Audio buffers (only for non-empty slots)
	 *   channel_0_slot_1.wav
	 *   ...
	 */

	// Save session to a new location
	saveAs { |path, action|
		var sessionDir = PathName(path);
		var metadataPath = sessionDir.fullPath +/+ "session.scd";
		var sessionData;

		// Validate path
		if (path.isNil or: { path.isEmpty }, {
			"SCClip.saveAs: Invalid path".error;
			^this;
		});

		// Create directory if it doesn't exist
		File.mkdir(sessionDir.fullPath);

		"SCClip: Saving session to '%'...".format(path).postln;

		// Collect all session metadata
		sessionData = this.collectSessionData;

		// Write metadata to .scd file
		sessionData.writeArchive(metadataPath);
		"SCClip: Wrote metadata to '%'".format(metadataPath).postln;

		// Export audio buffers asynchronously
		this.exportAudioBuffers(sessionDir.fullPath, {
			"SCClip: Session saved successfully to '%'".format(path).postln;
			action.value(this);
		});
	}

	// Collect all session state into a Dictionary
	collectSessionData {
		var data = Dictionary.new;

		// Transport settings
		data[\transport] = Dictionary[
			\tempo -> transport.tempo,
			\beatsPerBar -> transport.beatsPerBar,
			\timeSignature -> transport.timeSignature,
			\quantization -> if (transport.quantization == 0, {
				0
			}, {
				transport.quantization.quant
			}),
			\syncMode -> transport.syncMode,
			\metronomeEnabled -> transport.metronomeEnabled,
			\metronomeAmp -> transport.metronomeAmp
		];

		// Master bus settings
		data[\master] = Dictionary[
			\level -> masterBus.mixerChannel.level,
			\hasMasteringChain -> masterBus.eqSynth.notNil
		];

		// Master EQ settings (if active)
		if (masterBus.eqSynth.notNil, {
			data[\masterEQ] = Dictionary[
				\loFreq -> 80,   // Store current values if accessible
				\loGain -> 0,
				\midFreq -> 1000,
				\midGain -> 0,
				\midQ -> 1,
				\hiFreq -> 8000,
				\hiGain -> 0
			];
		});

		// Master compressor settings (if active)
		if (masterBus.compSynth.notNil, {
			data[\masterComp] = Dictionary[
				\thresh -> -12,
				\ratio -> 3,
				\attack -> 0.01,
				\release -> 0.3,
				\makeupGain -> 0
			];
		});

		// Master limiter settings (if active)
		if (masterBus.limiterSynth.notNil, {
			data[\masterLimiter] = Dictionary[
				\ceiling -> -0.3,
				\dur -> 0.01
			];
		});

		// Grid configuration
		data[\grid] = Dictionary[
			\numChannels -> numChannels,
			\numSlots -> numSlots
		];

		// Channel and slot data
		data[\channels] = grid.channels.collect { |channel, chanIdx|
			Dictionary[
				\level -> channel.mixerChannel.level,
				\pan -> channel.mixerChannel.pan,
				\isMuted -> channel.mixerChannel.muted,
				\isSoloed -> channel.isSoloed,
				\slots -> channel.slots.collect { |slot, slotIdx|
					if (slot.hasAudio, {
						Dictionary[
							\hasAudio -> true,
							\loopLengthBeats -> slot.loopLengthBeats,
							\loopLengthSamples -> slot.loopLengthSamples,
							\state -> slot.state,
							\audioFile -> "channel_%_slot_%.wav".format(chanIdx, slotIdx)
						]
					}, {
						Dictionary[\hasAudio -> false]
					})
				}
			]
		};

		^data;
	}

	// Export all audio buffers to .wav files
	exportAudioBuffers { |dirPath, completionAction|
		var totalExports = 0;

		// Count how many buffers need exporting
		grid.channels.do { |channel, chanIdx|
			channel.slots.do { |slot, slotIdx|
				if (slot.hasAudio, {
					totalExports = totalExports + 1;
				});
			};
		};

		if (totalExports == 0, {
			"SCClip: No audio buffers to export".postln;
			completionAction.value;
			^this;
		});

		// Buffer.write's completionMessage is built and evaluated on the
		// CLIENT right away, to become the OSC message bytes sent alongside
		// /b_write -- it is not a callback the server invokes once the write
		// actually finishes (that's only true of Buffer.read/alloc's `action`).
		// So completion here can't be tracked by counting inside it; instead,
		// queue all the writes, then use server.sync (inside a Routine, since
		// it yields) to wait until scsynth has actually processed every
		// queued command before declaring the export done.
		Routine {
			grid.channels.do { |channel, chanIdx|
				channel.slots.do { |slot, slotIdx|
					if (slot.hasAudio, {
						var fileName = "channel_%_slot_%.wav".format(chanIdx, slotIdx);
						var filePath = dirPath +/+ fileName;

						slot.buffer.write(
							path: filePath,
							headerFormat: "wav",
							sampleFormat: "float",
							numFrames: slot.loopLengthSamples
						);

						"SCClip: Queued export of %".format(fileName).postln;
					});
				};
			};

			server.sync;

			"SCClip: All % audio buffer(s) written".format(totalExports).postln;
			completionAction.value;
		}.play;
	}

	// Load session from disk
	*load { |path, server, action|
		var sessionDir = PathName(path);
		var metadataPath = sessionDir.fullPath +/+ "session.scd";
		var sessionData;
		var scclip;

		// Validate path
		if (File.exists(metadataPath).not, {
			"SCClip.load: Session file not found at '%'".format(metadataPath).error;
			^nil;
		});

		"SCClip: Loading session from '%'...".format(path).postln;

		// Read metadata
		sessionData = Object.readArchive(metadataPath);

		if (sessionData.isNil, {
			"SCClip.load: Failed to read session metadata from '%'".format(metadataPath).error;
			^nil;
		});

		// Create new SCClip instance with saved grid dimensions
		scclip = SCClip.new(
			numChannels: sessionData[\grid][\numChannels],
			numSlots: sessionData[\grid][\numSlots],
			server: server
		);

		// Boot and initialize with saved settings
		scclip.boot(action: {
			scclip.restoreSessionData(sessionData, sessionDir.fullPath, action);
		});

		^scclip;
	}

	// Restore session data after boot
	restoreSessionData { |sessionData, dirPath, completionAction|
		"SCClip: Restoring session state...".postln;

		// Restore transport settings
		this.setTempo(sessionData[\transport][\tempo]);
		this.setTimeSignature(
			sessionData[\transport][\timeSignature][0],
			sessionData[\transport][\timeSignature][1]
		);

		if (sessionData[\transport][\quantization] == 0, {
			this.setQuantization(0);
		}, {
			this.setQuantization(sessionData[\transport][\quantization]);
		});

		// Restore metronome state
		if (sessionData[\transport][\metronomeEnabled], {
			this.enableMetronome(sessionData[\transport][\metronomeAmp]);
		});

		// Restore master level
		this.setMasterLevel(sessionData[\master][\level].ampdb);

		// Restore mastering chain settings if they were active
		if (sessionData[\masterEQ].notNil, {
			var eq = sessionData[\masterEQ];
			this.setMasterEQ(
				eq[\loFreq], eq[\loGain],
				eq[\midFreq], eq[\midGain], eq[\midQ],
				eq[\hiFreq], eq[\hiGain]
			);
		});

		if (sessionData[\masterComp].notNil, {
			var comp = sessionData[\masterComp];
			this.setMasterCompressor(
				comp[\thresh], comp[\ratio],
				comp[\attack], comp[\release],
				comp[\makeupGain]
			);
		});

		if (sessionData[\masterLimiter].notNil, {
			var lim = sessionData[\masterLimiter];
			this.setMasterLimiter(lim[\ceiling], lim[\dur]);
		});

		// Restore channels and slots (including audio buffers)
		this.restoreChannelsAndSlots(sessionData[\channels], dirPath, completionAction);
	}

	// Restore channel mixer settings and slot audio
	restoreChannelsAndSlots { |channelsData, dirPath, completionAction|
		var loadCount = 0;
		var totalLoads = 0;
		var soloedChanIdx = nil;

		// Count how many buffers need loading
		channelsData.do { |channelData|
			channelData[\slots].do { |slotData|
				if (slotData[\hasAudio], {
					totalLoads = totalLoads + 1;
				});
			};
		};

		// Restore mixer settings for every channel up front -- this must not
		// be skipped when there's no audio to load (e.g. a session saved
		// before anything was recorded still has level/pan/mute/solo to
		// restore).
		channelsData.do { |channelData, chanIdx|
			var channel = grid.getChannel(chanIdx);

			channel.setLevel(channelData[\level].ampdb);
			channel.setPan(channelData[\pan]);

			if (channelData[\isMuted], { channel.mute });
			if (channelData[\isSoloed], { soloedChanIdx = chanIdx });
		};

		// Solo is cross-channel (mutes every other channel); apply it once,
		// after every channel's own mute state above, so it takes the same
		// precedence it had when the session was saved.
		if (soloedChanIdx.notNil, { grid.soloChannel(soloedChanIdx) });

		if (totalLoads == 0, {
			"SCClip: No audio buffers to load".postln;
			completionAction.value(this);
			^this;
		});

		// Restore slot audio
		channelsData.do { |channelData, chanIdx|
			var channel = grid.getChannel(chanIdx);

			channelData[\slots].do { |slotData, slotIdx|
				if (slotData[\hasAudio], {
					var slot = channel.getSlot(slotIdx);
					var audioPath = dirPath +/+ slotData[\audioFile];

					"SCClip: Loading %...".format(slotData[\audioFile]).postln;

					// Load buffer from disk
					Buffer.read(
						server: server,
						path: audioPath,
						action: { |buf|
							// Restore slot state
							slot.buffer = buf;
							slot.loopLengthBeats = slotData[\loopLengthBeats];
							slot.loopLengthSamples = slotData[\loopLengthSamples];

							// Set slot state (typically \stopped for saved clips)
							if (slotData[\state] == \playing, {
								slot.setState(\stopped);  // Don't auto-play on load
							}, {
								slot.setState(slotData[\state]);
							});

							loadCount = loadCount + 1;
							"SCClip: Loaded % (%/%)".format(
								slotData[\audioFile],
								loadCount,
								totalLoads
							).postln;

							// Call completion action when all loads are done
							if (loadCount == totalLoads, {
								"SCClip: Session loaded successfully".postln;
								completionAction.value(this);
							});
						}
					);
				});
			};
		};
	}

}
