/*
 * SC-Clip Session Save/Load Test
 *
 * This script demonstrates saving and loading sessions.
 *
 * Usage:
 * 1. Run the "Create and Save Session" section to create a test session
 * 2. Run the "Load Session" section to reload it
 */

(
// ===== Create and Save Session =====

var clip;

// Create new session
clip = SCClip.new(numChannels: 2, numSlots: 4);

clip.boot(action: {
	"=== Session created, setting up test data ===".postln;

	// Configure transport
	clip.setTempo(128);
	clip.setTimeSignature(4, 4);
	clip.enableMetronome(0.2);

	// Configure master
	clip.setMasterLevel(-3);

	// Configure channel 0
	clip.setChannelLevel(0, -6);
	clip.setChannelPan(0, -0.5);

	// Configure channel 1
	clip.setChannelLevel(1, -9);
	clip.setChannelPan(1, 0.3);

	// Arm some slots for recording (in a real scenario, you'd record audio)
	// For testing, we'll just arm them to create buffer allocations
	clip.armSlot(0, 0, loopLengthBeats: 4);
	clip.armSlot(0, 1, loopLengthBeats: 8);
	clip.armSlot(1, 0, loopLengthBeats: 2);

	// Wait a moment for buffer allocation
	fork {
		2.wait;

		"=== Saving session ===".postln;

		// Save session to disk
		clip.saveAs(
			path: Platform.userAppSupportDir +/+ "SC-Clip/TestSession",
			action: {
				"=== Session saved! ===".postln;
				"You can now run the 'Load Session' section below to test loading.".postln;
			}
		);
	};
});

// Store in environment for later access
~clip = clip;
)

// ===== Load Session =====

(
var sessionPath = Platform.userAppSupportDir +/+ "SC-Clip/TestSession";

"=== Loading session from % ===".format(sessionPath).postln;

// Shutdown any existing session first
if (~clip.notNil, {
	~clip.shutdown;
});

// Load the saved session
~clip = SCClip.load(
	path: sessionPath,
	server: Server.default,
	action: {
		"=== Session loaded successfully! ===".postln;
		"Checking session state:".postln;
		~clip.printStatus;
	}
);
)

// ===== Print Current Session Status =====

~clip.printStatus;

// ===== Cleanup =====

~clip.shutdown;
