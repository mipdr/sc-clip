/*
 * SC-Clip Debug Logging Example
 *
 * This example demonstrates how to use debug logging to troubleshoot
 * unexpected behaviors in SC-Clip.
 *
 * Scenarios covered:
 * 1. Normal workflow with debug logging
 * 2. Detecting orphan synths
 * 3. Tracking state transitions
 * 4. Saving logs for later analysis
 */

(
// Boot SC-Clip with debug logging enabled
~clip = SCClip.new(numChannels: 3, numSlots: 4);

~clip.boot({
	"===========================================".postln;
	"SC-Clip Debug Logging Example".postln;
	"===========================================".postln;

	// Enable debug logging at normal level
	~clip.enableDebug(\normal);

	"Debug logging enabled. Watch the console for detailed events.".postln;
	"".postln;

	// Create a test tone for recording
	~testTone = {
		var freq = LFNoise1.kr(0.5).range(200, 800);
		SinOsc.ar(freq) * 0.1 ! 4;  // Quad output
	}.play;

	"Test tone playing...".postln;
});
)

// ===== Example 1: Normal Recording Workflow =====
// Watch the console to see state transitions and synth lifecycle
(
"".postln;
"===== Example 1: Normal Recording Workflow =====".postln;

// Arm slot [0, 0] for 4-beat recording
~clip.armSlot(0, 0, 4);

// Launch recording (will start at next quantized beat)
~clip.launchSlot(0, 0);

"Recording armed and launched. Watch console for state changes...".postln;
"(Recording will auto-transition to playing after 4 beats)".postln;
)

// Wait a few seconds, then check the log
(
fork {
	6.wait;

	"".postln;
	"===== Debug Summary After Recording =====".postln;
	~clip.printDebugSummary;

	"".postln;
	"===== Active Synths =====".postln;
	~clip.printActiveSynths;

	"".postln;
	"Notice: Slot [0,0] should have a clipPlayer synth running".postln;
}
)

// ===== Example 2: Multiple Slots =====
(
"".postln;
"===== Example 2: Recording Multiple Slots =====".postln;

// Record on channel 1, slot 0 (2 beats)
~clip.armSlot(1, 0, 2);
~clip.launchSlot(1, 0);

// Record on channel 2, slot 1 (8 beats)
~clip.armSlot(2, 1, 8);
~clip.launchSlot(2, 1);

"Multiple recordings started. Check console for interleaved events.".postln;
)

// ===== Example 3: Stop and Check for Orphans =====
(
fork {
	10.wait;

	"".postln;
	"===== Example 3: Stopping Clips =====".postln;

	// Stop all playing clips
	~clip.stopSlot(0, 0);
	~clip.stopSlot(1, 0);

	2.wait;

	"".postln;
	"===== Checking for Orphan Synths =====".postln;
	var orphans = ~clip.checkForOrphans;

	if (orphans.size > 0, {
		"WARNING: Found % orphan synth(s)!".format(orphans.size).warn;
		"This indicates synths that are still running but not tracked.".postln;
	}, {
		"GOOD: No orphan synths detected.".postln;
	});

	"".postln;
	"===== Current Active Synths =====".postln;
	~clip.printActiveSynths;
}
)

// ===== Example 4: Verbose Logging =====
(
"".postln;
"===== Example 4: Verbose Logging =====".postln;
"Switching to verbose mode for detailed synth tracking...".postln;

// Switch to verbose logging
~clip.clearDebugLog;  // Clear previous logs for clarity
~clip.enableDebug(\verbose);

// Arm and launch a new slot
~clip.armSlot(0, 1, 2);
~clip.launchSlot(0, 1);

"Recording started with verbose logging. Notice the detailed synth info.".postln;
)

// ===== Example 5: View Log History =====
(
fork {
	15.wait;

	"".postln;
	"===== Example 5: Reviewing Log History =====".postln;
	"Printing last 30 log entries:".postln;
	"".postln;

	~clip.printDebugHistory(30);
}
)

// ===== Example 6: Save Debug Log =====
(
fork {
	18.wait;

	"".postln;
	"===== Example 6: Saving Debug Log =====".postln;

	var logPath = "~/Desktop/sc-clip-example-debug.log".standardizePath;
	~clip.saveDebugLog(logPath);

	"".postln;
	"You can now open the log file in a text editor:".postln;
	logPath.postln;
	"Share this file when reporting issues!".postln;
}
)

// ===== Example 7: Simulating an Issue (Rapid Launches) =====
(
"".postln;
"===== Example 7: Detecting Issues =====".postln;
"Simulating rapid launches (may cause state conflicts)...".postln;

// Clear logs to focus on this scenario
~clip.clearDebugLog;
~clip.enableDebug(\normal);

// Arm a slot
~clip.armSlot(0, 2, 4);

// Launch it multiple times rapidly (before quantization triggers)
// This SHOULD be handled gracefully, but let's verify
~clip.launchSlot(0, 2);
0.1.wait;
~clip.launchSlot(0, 2);  // Second launch before first starts
0.1.wait;
~clip.launchSlot(0, 2);  // Third launch

"Rapid launches sent. Check console for warnings...".postln;
)

// Review what happened
(
fork {
	2.wait;

	"".postln;
	"===== Reviewing Rapid Launch Scenario =====".postln;
	~clip.printDebugHistory(20);

	"".postln;
	"Look for warnings about state conflicts in the log above.".postln;
}
)

// ===== Cleanup =====
(
fork {
	25.wait;

	"".postln;
	"===== Final Summary =====".postln;
	~clip.printDebugSummary;

	"".postln;
	"===== Cleanup =====".postln;

	// Stop test tone
	if (~testTone.notNil, { ~testTone.free });

	// Stop all clips
	~clip.stopAll;

	2.wait;

	// Shutdown SC-Clip
	~clip.shutdown;

	// Disable debug logging
	~clip.disableDebug;

	"".postln;
	"Example complete!".postln;
	"".postln;
	"Key takeaways:".postln;
	"1. Enable debug logging with ~clip.enableDebug(\\normal)".postln;
	"2. Use ~clip.printDebugSummary to see what happened".postln;
	"3. Use ~clip.checkForOrphans to detect orphan synths".postln;
	"4. Use ~clip.saveDebugLog(path) to save logs for sharing".postln;
	"5. Switch to \\verbose mode for detailed synth tracking".postln;
}
)

/*
 * TROUBLESHOOTING WORKFLOWS
 *
 * If you experience issues like orphan loops or infinite recording:
 *
 * 1. Enable verbose logging BEFORE reproducing:
 *    ~clip.enableDebug(\verbose);
 *
 * 2. Reproduce the issue
 *
 * 3. Check for orphans:
 *    ~clip.checkForOrphans;
 *
 * 4. Review the log:
 *    ~clip.printDebugHistory;
 *
 * 5. Save for analysis:
 *    ~clip.saveDebugLog("~/Desktop/my-issue.log");
 *
 * 6. Share the log file when reporting the issue
 */
