/*
 * SC-Clip Save/Load Simple Test
 *
 * Simplified test that validates save/load metadata without requiring
 * a running audio server. This is suitable for CI environments.
 *
 * Exit codes:
 * 0 = All tests passed
 * 1 = Test failed
 */

// Execute immediately - no Routine needed for non-server code
(
var testSessionPath = "/tmp/sc-clip-test-session-simple";
var testPassed = true;
var failureMessage = nil;

// Helper functions
var log = { |msg| ("TEST: " ++ msg).postln; };
var logPass = { |msg| ("PASS: " ++ msg).postln; };
var logFail = { |msg|
	("FAIL: " ++ msg).postln;
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

"========================================".postln;
"SC-Clip Save/Load Simple Test".postln;
"========================================".postln;

// Clean up any existing test session
if (File.exists(testSessionPath +/+ "session.scd"), {
	log.("Cleaning up old test session...");
	("rm -rf " ++ testSessionPath).systemCmd;
	0.2.wait;
});

log.("Creating test session directory...");
File.mkdir(testSessionPath);

// Create a minimal session file manually to test loading
log.("Creating mock session metadata...");
var sessionData = (
	version: "1.0",
	tempo: 140,
	timeSignature: [3, 4],
	quantization: 4,
	masterLevel: -6,
	numChannels: 2,
	numSlots: 4,
	channels: [
		(
			channelIndex: 0,
			level: -3,
			pan: -0.5,
			muted: false,
			soloed: false,
			slots: [
				(slotIndex: 0, loopLengthBeats: 4, state: \stopped, hasAudio: true),
				(slotIndex: 1, loopLengthBeats: nil, state: \empty, hasAudio: false)
			]
		),
		(
			channelIndex: 1,
			level: -9,
			pan: 0.3,
			muted: true,
			soloed: false,
			slots: [
				(slotIndex: 0, loopLengthBeats: 2, state: \stopped, hasAudio: true)
			]
		)
	],
	metronome: (
		enabled: true,
		amp: 0.5
	),
	master: (
		level: -6,
		eq: (
			loFreq: 80, loGain: 1.5,
			midFreq: 1000, midGain: -0.5, midQ: 1.2,
			hiFreq: 8000, hiGain: 2
		),
		compressor: (
			thresh: -12, ratio: 3, attack: 0.01, release: 0.1, makeupGain: 2
		),
		limiter: (
			ceiling: -1, dur: 0.01
		)
	)
);

// Write session file
var sessionFile = File(testSessionPath +/+ "session.scd", "w");
sessionFile.write(sessionData.asCompileString);
sessionFile.close;

logPass.("Session metadata file created");

// Verify file was created
if (File.exists(testSessionPath +/+ "session.scd"), {
	logPass.("Session file exists");
}, {
	logFail.("Session file was not created");
	1.exit;
});

// Try to read it back
log.("Reading session metadata...");
var loadedData = (testSessionPath +/+ "session.scd").load;

if (loadedData.isNil, {
	logFail.("Failed to load session data");
	1.exit;
}, {
	logPass.("Session data loaded");
});

// Verify loaded data matches
assertEqual.(loadedData.version, "1.0", "Version");
assertEqual.(loadedData.tempo, 140, "Tempo");
assertEqual.(loadedData.timeSignature[0], 3, "Time signature numerator");
assertEqual.(loadedData.timeSignature[1], 4, "Time signature denominator");
assertEqual.(loadedData.quantization, 4, "Quantization");
assertEqual.(loadedData.masterLevel, -6, "Master level");
assertEqual.(loadedData.numChannels, 2, "Number of channels");
assertEqual.(loadedData.numSlots, 4, "Number of slots");

// Verify channel data
var chan0 = loadedData.channels[0];
assertEqual.(chan0.channelIndex, 0, "Channel 0 index");
assertEqual.(chan0.level, -3, "Channel 0 level");
assertEqual.(chan0.pan, -0.5, "Channel 0 pan");
assertEqual.(chan0.muted, false, "Channel 0 muted");
assertEqual.(chan0.soloed, false, "Channel 0 soloed");

var chan1 = loadedData.channels[1];
assertEqual.(chan1.channelIndex, 1, "Channel 1 index");
assertEqual.(chan1.level, -9, "Channel 1 level");
assertEqual.(chan1.muted, true, "Channel 1 muted");

// Verify slot data
var slot00 = chan0.slots[0];
assertEqual.(slot00.slotIndex, 0, "Slot [0,0] index");
assertEqual.(slot00.loopLengthBeats, 4, "Slot [0,0] loop length");
assertEqual.(slot00.state, \stopped, "Slot [0,0] state");
assertEqual.(slot00.hasAudio, true, "Slot [0,0] has audio");

var slot01 = chan0.slots[1];
assertEqual.(slot01.state, \empty, "Slot [0,1] state");
assertEqual.(slot01.hasAudio, false, "Slot [0,1] has audio");

// Verify metronome settings
var metro = loadedData.metronome;
assertEqual.(metro.enabled, true, "Metronome enabled");
assertEqual.(metro.amp, 0.5, "Metronome amplitude");

// Verify master settings
var master = loadedData.master;
assertEqual.(master.level, -6, "Master level in data");
assertEqual.(master.eq.loFreq, 80, "Master EQ low freq");
assertEqual.(master.eq.loGain, 1.5, "Master EQ low gain");
assertEqual.(master.compressor.thresh, -12, "Master compressor threshold");
assertEqual.(master.compressor.ratio, 3, "Master compressor ratio");
assertEqual.(master.limiter.ceiling, -1, "Master limiter ceiling");

// Test round-trip: save and reload
log.("Testing session data round-trip...");
var newPath = testSessionPath +/+ "session_copy.scd";
var copyFile = File(newPath, "w");
copyFile.write(loadedData.asCompileString);
copyFile.close;

var reloaded = newPath.load;
assertEqual.(reloaded.tempo, loadedData.tempo, "Round-trip tempo");
assertEqual.(reloaded.numChannels, loadedData.numChannels, "Round-trip numChannels");
assertEqual.(reloaded.channels[0].level, loadedData.channels[0].level, "Round-trip channel 0 level");

logPass.("Round-trip test passed");

// Cleanup
log.("Cleaning up...");
("rm -rf " ++ testSessionPath).systemCmd;

// Print final results and EXIT IMMEDIATELY
"========================================".postln;
if (testPassed, {
	"ALL TESTS PASSED".postln;
	"========================================".postln;
	// Exit with success code
	0.exit;
}, {
	"TESTS FAILED".postln;
	if (failureMessage.notNil, {
		("Last failure: " ++ failureMessage).postln;
	});
	"========================================".postln;
	// Exit with failure code
	1.exit;
});
)

