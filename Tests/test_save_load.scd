/*
 * SC-Clip Save/Load Test Suite
 *
 * Comprehensive test for session saving and loading functionality.
 * Tests all available settings and modes:
 * - Transport settings (tempo, time signature, quantization, metronome)
 * - Master bus settings (level, EQ, compressor, limiter)
 * - Channel settings (level, pan, mute, solo)
 * - Grid with multiple clips (stopped and running states)
 * - Recorded audio buffers
 *
 * Exit codes:
 * 0 = All tests passed
 * 1 = Test failed
 *
 * Usage:
 *   QT_QPA_PLATFORM=offscreen sclang -D Tests/test_save_load.scd
 */

(
var testSessionPath = "/tmp/sc-clip-test-session";
var testPassed = true;
var failureMessage = nil;
var clip;

// Helper function to log test results
var log = { |msg|
	("TEST: " ++ msg).postln;
};

var logPass = { |msg|
	("✓ PASS: " ++ msg).postln;
};

var logFail = { |msg|
	("✗ FAIL: " ++ msg).postln;
	testPassed = false;
	failureMessage = msg;
};

var assertEqual = { |actual, expected, label|
	if (actual != expected, {
		logFail.("% - expected: %, got: %".format(label, expected, actual));
	}, {
		logPass.("% - %".format(label, expected));
	});
};

var assertNear = { |actual, expected, tolerance, label|
	var diff = (actual - expected).abs;
	if (diff > tolerance, {
		logFail.("% - expected: % ± %, got: % (diff: %)".format(label, expected, tolerance, actual, diff));
	}, {
		logPass.("% - %".format(label, expected));
	});
};

var assertNotNil = { |value, label|
	if (value.isNil, {
		logFail.("% is nil".format(label));
	}, {
		logPass.("% is not nil".format(label));
	});
};

"========================================".postln;
"SC-Clip Save/Load Test Suite".postln;
"========================================".postln;

// Clean up any existing test session
if (File.exists(testSessionPath +/+ "session.scd"), {
	log.("Cleaning up old test session...");
	("rm -rf " ++ testSessionPath).unixCmd;
	0.5.wait;
});

// Boot server
log.("Booting server...");
s.options.memSize = 8192 * 16;
s.options.numInputBusChannels = 4;
s.options.numOutputBusChannels = 2;

s.waitForBoot({
	log.("Server booted successfully");

	// ===== PHASE 1: Create and configure session =====
	log.("Creating new session...");

	clip = SCClip.new(numChannels: 3, numSlots: 4);

	clip.boot(action: {
		log.("Session created, configuring...");

		// Configure transport settings
		clip.setTempo(135);
		clip.setTimeSignature(7, 8);
		clip.setQuantization(2);
		clip.enableMetronome(0.25);

		// Configure master bus
		clip.setMasterLevel(-6);
		clip.setMasterEQ(
			loFreq: 100, loGain: 2,
			midFreq: 1500, midGain: -1, midQ: 1.5,
			hiFreq: 10000, hiGain: 3
		);
		clip.setMasterCompressor(
			thresh: -15, ratio: 4, attack: 0.005, release: 0.2, makeupGain: 3
		);
		clip.setMasterLimiter(ceiling: -0.5, dur: 0.005);

		// Configure channels
		// Channel 0: normal settings
		clip.setChannelLevel(0, -3);
		clip.setChannelPan(0, -0.7);

		// Channel 1: muted
		clip.setChannelLevel(1, -9);
		clip.setChannelPan(1, 0.5);
		clip.muteChannel(1);

		// Channel 2: soloed
		clip.setChannelLevel(2, -12);
		clip.setChannelPan(2, 0.0);
		clip.soloChannel(2);

		// Create test tone for recording
		log.("Creating test tone...");
		~testTone = {
			var freq = LFNoise1.kr(0.5).range(200, 800);
			SinOsc.ar(freq) * 0.1 ! 4;  // Quad output
		}.play;

		// Wait for tone to start
		0.5.wait;

		// Record some clips with different loop lengths
		log.("Recording test clips...");

		// Channel 0, Slot 0: 2 beats
		clip.armSlot(0, 0, 2);
		clip.launchSlot(0, 0);

		// Channel 0, Slot 1: 4 beats
		clip.armSlot(0, 1, 4);

		// Channel 1, Slot 0: 1 beat
		clip.armSlot(1, 0, 1);
		clip.launchSlot(1, 0);

		// Channel 2, Slot 0: 8 beats
		clip.armSlot(2, 0, 8);
		clip.launchSlot(2, 0);

		// Channel 2, Slot 2: 2 beats (will stay stopped)
		clip.armSlot(2, 2, 2);
		clip.launchSlot(2, 2);

		// Wait for recordings to complete (8 beats at 135 BPM = ~3.56 seconds)
		// Add extra time for safety
		log.("Waiting for recordings to complete...");
		4.5.wait;

		// Stop some clips to create mixed state
		log.("Stopping some clips...");
		clip.stopSlot(2, 2);  // Stop this one so it's in stopped state

		// Wait for stop to take effect
		1.wait;

		// Kill test tone
		~testTone.free;

		// ===== PHASE 2: Save session =====
		log.("Saving session to '%'...".format(testSessionPath));

		clip.saveAs(
			path: testSessionPath,
			action: {
				log.("Session saved successfully");

				// Verify session files exist
				if (File.exists(testSessionPath +/+ "session.scd").not, {
					logFail.("session.scd file not created");
					testPassed = false;
					1.exit;
				}, {
					logPass.("session.scd file created");
				});

				// Verify audio files exist
				var expectedFiles = [
					"channel_0_slot_0.wav",
					"channel_1_slot_0.wav",
					"channel_2_slot_0.wav",
					"channel_2_slot_2.wav"
				];

				expectedFiles.do { |filename|
					var path = testSessionPath +/+ filename;
					if (File.exists(path).not, {
						logFail.("Audio file not created: %".format(filename));
						testPassed = false;
					}, {
						logPass.("Audio file created: %".format(filename));
					});
				};

				// Shutdown original session
				log.("Shutting down original session...");
				clip.shutdown;
				clip = nil;

				// Wait for clean shutdown
				1.wait;

				// ===== PHASE 3: Load session =====
				log.("Loading session from '%'...".format(testSessionPath));

				clip = SCClip.load(
					path: testSessionPath,
					server: s,
					action: {
						log.("Session loaded successfully");

						// Wait a bit for everything to stabilize
						0.5.wait;

						// ===== PHASE 4: Verify loaded session =====
						log.("Verifying loaded session...");

						// Verify transport settings
						assertEqual.(clip.transport.tempo, 135, "Transport tempo");
						assertEqual.(clip.transport.timeSignature[0], 7, "Time signature numerator");
						assertEqual.(clip.transport.timeSignature[1], 8, "Time signature denominator");
						assertEqual.(clip.transport.metronomeEnabled, true, "Metronome enabled");
						assertNear.(clip.transport.metronomeAmp, 0.25, 0.01, "Metronome amplitude");

						// Verify master bus settings
						var masterLevelDb = clip.masterBus.mixerChannel.level.ampdb;
						assertNear.(masterLevelDb, -6, 0.5, "Master level");

						// Verify mastering chain is active
						assertNotNil.(clip.masterBus.eqSynth, "Master EQ synth");
						assertNotNil.(clip.masterBus.compSynth, "Master compressor synth");
						assertNotNil.(clip.masterBus.limiterSynth, "Master limiter synth");

						// Verify grid configuration
						assertEqual.(clip.numChannels, 3, "Number of channels");
						assertEqual.(clip.numSlots, 4, "Number of slots");

						// Verify channel settings
						var chan0Level = clip.grid.getChannel(0).mixerChannel.level.ampdb;
						var chan1Level = clip.grid.getChannel(1).mixerChannel.level.ampdb;
						var chan2Level = clip.grid.getChannel(2).mixerChannel.level.ampdb;

						assertNear.(chan0Level, -3, 0.5, "Channel 0 level");
						assertNear.(chan1Level, -9, 0.5, "Channel 1 level");
						assertNear.(chan2Level, -12, 0.5, "Channel 2 level");

						assertNear.(clip.grid.getChannel(0).mixerChannel.pan, -0.7, 0.05, "Channel 0 pan");
						assertNear.(clip.grid.getChannel(1).mixerChannel.pan, 0.5, 0.05, "Channel 1 pan");
						assertNear.(clip.grid.getChannel(2).mixerChannel.pan, 0.0, 0.05, "Channel 2 pan");

						// Verify mute/solo states
						assertEqual.(clip.grid.getChannel(1).mixerChannel.muted, true, "Channel 1 muted");
						assertEqual.(clip.grid.getChannel(2).isSoloed, true, "Channel 2 soloed");

						// Verify clip states
						var slot00 = clip.grid.getSlot(0, 0);
						var slot01 = clip.grid.getSlot(0, 1);
						var slot10 = clip.grid.getSlot(1, 0);
						var slot20 = clip.grid.getSlot(2, 0);
						var slot22 = clip.grid.getSlot(2, 2);

						// Channel 0, Slot 0: should have audio (stopped)
						assertEqual.(slot00.hasAudio, true, "Slot [0,0] has audio");
						assertEqual.(slot00.loopLengthBeats, 2, "Slot [0,0] loop length");
						assertEqual.(slot00.state, \stopped, "Slot [0,0] state");
						assertNotNil.(slot00.buffer, "Slot [0,0] buffer");

						// Channel 0, Slot 1: should be empty (armed but never launched)
						assertEqual.(slot01.hasAudio, false, "Slot [0,1] has no audio");
						assertEqual.(slot01.state, \empty, "Slot [0,1] state");

						// Channel 1, Slot 0: should have audio (stopped)
						assertEqual.(slot10.hasAudio, true, "Slot [1,0] has audio");
						assertEqual.(slot10.loopLengthBeats, 1, "Slot [1,0] loop length");
						assertEqual.(slot10.state, \stopped, "Slot [1,0] state");

						// Channel 2, Slot 0: should have audio (stopped, was playing)
						assertEqual.(slot20.hasAudio, true, "Slot [2,0] has audio");
						assertEqual.(slot20.loopLengthBeats, 8, "Slot [2,0] loop length");
						assertEqual.(slot20.state, \stopped, "Slot [2,0] state");

						// Channel 2, Slot 2: should have audio (stopped)
						assertEqual.(slot22.hasAudio, true, "Slot [2,2] has audio");
						assertEqual.(slot22.loopLengthBeats, 2, "Slot [2,2] loop length");
						assertEqual.(slot22.state, \stopped, "Slot [2,2] state");

						// Verify buffers are valid and have correct size
						var expectedSamples00 = (2 * 60 / 135 * s.sampleRate).asInteger;
						var expectedSamples10 = (1 * 60 / 135 * s.sampleRate).asInteger;

						assertEqual.(slot00.loopLengthSamples, expectedSamples00, "Slot [0,0] buffer size");
						assertEqual.(slot10.loopLengthSamples, expectedSamples10, "Slot [1,0] buffer size");

						// ===== PHASE 5: Test playback of loaded clips =====
						log.("Testing playback of loaded clips...");

						// Try to play a loaded clip
						clip.launchSlot(0, 0);

						// Wait a bit
						1.wait;

						// Check if it's playing
						var playingState = clip.grid.getSlot(0, 0).state;
						if (playingState != \playing && playingState != \queuedToPlay, {
							logFail.("Loaded clip not playing - state: %".format(playingState));
						}, {
							logPass.("Loaded clip can be played");
						});

						// Stop it
						clip.stopSlot(0, 0);

						// ===== CLEANUP AND FINISH =====
						log.("Cleaning up...");
						clip.shutdown;

						// Clean up test session files
						("rm -rf " ++ testSessionPath).unixCmd;

						// Print final results
						"========================================".postln;
						if (testPassed, {
							"✓ ALL TESTS PASSED".postln;
							"========================================".postln;
							0.exit;
						}, {
							"✗ TESTS FAILED".postln;
							if (failureMessage.notNil, {
								("Last failure: " ++ failureMessage).postln;
							});
							"========================================".postln;
							1.exit;
						});
					}
				);
			}
		);
	});
});
)
