// SC-Clip Debug Logger Test
//
// Tests the debug logging functionality
//
// Exit codes:
// 0 = All tests passed
// 1 = Test failed

(
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

var assertTrue = { |value, label|
	if (value.not, {
		logFail.("% - expected true, got false".format(label));
	}, {
		logPass.(label);
	});
};

var assertGreater = { |value, threshold, label|
	if (value <= threshold, {
		logFail.("% - expected > %, got %".format(label, threshold, value));
	}, {
		logPass.(label);
	});
};

"========================================".postln;
"SC-Clip Debug Logger Test".postln;
"========================================".postln;

// Test 1: Enable/disable
log.("Testing enable/disable...");
ClipDebugLogger.clear;
ClipDebugLogger.enable;
assertTrue.(ClipDebugLogger.enabled, "Logger enabled");

ClipDebugLogger.disable;
assertTrue.(ClipDebugLogger.enabled.not, "Logger disabled");

// Test 2: Log levels
log.("Testing log levels...");
ClipDebugLogger.setLevel(\minimal);
assertEqual.(ClipDebugLogger.level, \minimal, "Level set to minimal");

ClipDebugLogger.setLevel(\normal);
assertEqual.(ClipDebugLogger.level, \normal, "Level set to normal");

ClipDebugLogger.setLevel(\verbose);
assertEqual.(ClipDebugLogger.level, \verbose, "Level set to verbose");

// Test 3: Basic logging
log.("Testing basic logging...");
ClipDebugLogger.clear;
ClipDebugLogger.enable;
assertEqual.(ClipDebugLogger.history.size, 1, "History cleared (only enable message)");

ClipDebugLogger.log(\test, "Test message 1", level: \normal);
ClipDebugLogger.log(\test, "Test message 2", "details", level: \normal);
assertGreater.(ClipDebugLogger.history.size, 2, "Messages logged");

// Test 4: Log filtering by level
log.("Testing log filtering...");
ClipDebugLogger.clear;

// Set to minimal level
ClipDebugLogger.setLevel(\minimal);
ClipDebugLogger.log(\test, "Minimal message", level: \minimal);
ClipDebugLogger.log(\test, "Normal message", level: \normal);
ClipDebugLogger.log(\test, "Verbose message", level: \verbose);
// Only clear message (minimal) + minimal message should be logged
assertEqual.(ClipDebugLogger.history.size, 3, "Minimal level filters correctly");

// Set to normal level
ClipDebugLogger.clear;
ClipDebugLogger.setLevel(\normal);
ClipDebugLogger.log(\test, "Minimal message", level: \minimal);
ClipDebugLogger.log(\test, "Normal message", level: \normal);
ClipDebugLogger.log(\test, "Verbose message", level: \verbose);
// Clear (minimal) + minimal + normal = 3
assertEqual.(ClipDebugLogger.history.size, 3, "Normal level filters correctly");

// Set to verbose level
ClipDebugLogger.clear;
ClipDebugLogger.setLevel(\verbose);
ClipDebugLogger.log(\test, "Minimal message", level: \minimal);
ClipDebugLogger.log(\test, "Normal message", level: \normal);
ClipDebugLogger.log(\test, "Verbose message", level: \verbose);
// All 4 messages (clear + 3) should be logged
assertEqual.(ClipDebugLogger.history.size, 4, "Verbose level logs everything");

// Test 5: Category-specific logging
log.("Testing category-specific methods...");
ClipDebugLogger.clear;
ClipDebugLogger.setLevel(\normal);

ClipDebugLogger.logSlotState(0, 0, \empty, \armed, "test");
ClipDebugLogger.logRecordStart(0, 0, 4, 42);
ClipDebugLogger.logRecordEnd(0, 0, 2.5);
ClipDebugLogger.logPlayStart(0, 0, 42);
ClipDebugLogger.logPlayStop(0, 0, 5.0);

assertGreater.(ClipDebugLogger.history.size, 5, "Category methods log correctly");

// Test 6: Warning and error logging
log.("Testing warning/error methods...");
ClipDebugLogger.clear;
ClipDebugLogger.setLevel(\minimal);  // Should still log warnings/errors

ClipDebugLogger.logWarning(\test, "Warning message", "details");
ClipDebugLogger.logError(\test, "Error message", "details");

assertEqual.(ClipDebugLogger.history.size, 3, "Warnings/errors logged at minimal level");

// Test 7: Synth tracking
log.("Testing synth tracking...");
ClipDebugLogger.clear;
ClipDebugLogger.setLevel(\verbose);

// Create a fake synth object for testing
var fakeSynth = (nodeID: 1001, isPlaying: true);

ClipDebugLogger.logSynthCreate(\clipPlayer, 0, 0, fakeSynth, [\gate, 1]);
assertEqual.(ClipDebugLogger.activeSynths.size, 1, "Synth added to tracking");

ClipDebugLogger.logSynthFree(\clipPlayer, 0, 0, fakeSynth, "normal stop");
assertEqual.(ClipDebugLogger.activeSynths.size, 0, "Synth removed from tracking");

// Test 8: History management
log.("Testing history management...");
ClipDebugLogger.clear;
ClipDebugLogger.enable;

// Add many entries
100.do { |i|
	ClipDebugLogger.log(\test, "Entry %".format(i), level: \normal);
};

assertGreater.(ClipDebugLogger.history.size, 50, "History accumulates");

ClipDebugLogger.clear;
assertEqual.(ClipDebugLogger.history.size, 1, "History cleared (only clear message remains)");

// Test 9: Save to file
log.("Testing save to file...");
var testPath = "/tmp/sc-clip-debug-test.log".standardizePath;

ClipDebugLogger.clear;
ClipDebugLogger.enable;
ClipDebugLogger.log(\test, "Test log entry", "for file save", level: \normal);

ClipDebugLogger.saveToFile(testPath);

assertTrue.(File.exists(testPath), "Log file created");

// Clean up test file
("rm -f " ++ testPath).systemCmd;

// Test 10: Integration with disabled logger
log.("Testing behavior when disabled...");
ClipDebugLogger.disable;
var historySize = ClipDebugLogger.history.size;

ClipDebugLogger.log(\test, "Should not log", level: \normal);
assertEqual.(ClipDebugLogger.history.size, historySize, "No logging when disabled");

// Print final results
"========================================".postln;
if (testPassed, {
	"ALL TESTS PASSED".postln;
	"========================================".postln;
	0.exit;
}, {
	"TESTS FAILED".postln;
	if (failureMessage.notNil, {
		("Last failure: " ++ failureMessage).postln;
	});
	"========================================".postln;
	1.exit;
});
)
