/*
 * SC-Clip Input-to-Channel Mapping Test
 *
 * Tests dynamic input-to-channel mapping functionality:
 * - Single input can record to multiple channels (sequentially)
 * - Channels have independent effects despite same input source
 * - Mapping changes update input monitor synth correctly
 * - Session save/load preserves mappings
 *
 * Exit codes:
 * 0 = All tests passed
 * 1 = Test failed
 *
 * Usage:
 *   QT_QPA_PLATFORM=offscreen sclang -D Tests/test_input_channel_mapping.scd
 */

(
var testPassed = true;
var failureMessage = nil;
var clip;

// Helper functions
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

"========================================".postln;
"SC-Clip Input-to-Channel Mapping Test".postln;
"========================================".postln;

// Boot server with timeout
log.("Booting server...");
s.options.memSize = 8192 * 16;
s.options.numInputBusChannels = 4;
s.options.numOutputBusChannels = 2;
s.options.device = "JACK";

// Add boot timeout
fork {
	30.wait;
	if (s.serverRunning.not, {
		"ERROR: Server boot timed out after 30 seconds".postln;
		1.exit;
	});
};

s.waitForBoot({
	log.("Server booted successfully");

	// ===== TEST 1: Create SC-Clip with default mapping =====
	log.("Test 1: Create SC-Clip instance with default mapping");
	clip = SCClip.new(numChannels: 4, numSlots: 4, server: s);
	clip.boot(action: {
		log.("SC-Clip booted");

		0.5.wait;

		// ===== TEST 2: Verify initial mapping (input i -> channel i) =====
		log.("Test 2: Verify default input mapping (input i → channel i)");
		4.do { |i|
			var input = clip.getChannelInput(i);
			assertEqual.(input, i, "Channel % initial mapping".format(i));
		};

		// ===== TEST 3: Remap input 0 to channel 2 =====
		log.("Test 3: Remap input 0 to channel 2");
		clip.setChannelInput(2, 0);
		0.5.wait;

		assertEqual.(clip.getChannelInput(2), 0, "Channel 2 maps to input 0");

		// ===== TEST 4: Verify input 0 is no longer on channel 0 =====
		log.("Test 4: Verify channel 0 lost its input (reassigned to ch 2)");
		var ch0Input = clip.getChannelInput(0);
		if (ch0Input.notNil, {
			logFail.("Channel 0 should have no input (nil), got %".format(ch0Input));
		}, {
			logPass.("Channel 0 has no input (nil)");
		});

		// ===== TEST 5: Disconnect channel 2 (set to nil) =====
		log.("Test 5: Disconnect channel 2 from all inputs");
		clip.setChannelInput(2, nil);
		0.5.wait;

		var ch2Input = clip.getChannelInput(2);
		if (ch2Input.notNil, {
			logFail.("Channel 2 should have no input (nil), got %".format(ch2Input));
		}, {
			logPass.("Channel 2 disconnected (nil)");
		});

		// ===== TEST 6: Map same input to different channels sequentially =====
		log.("Test 6: Map input 1 to channel 0, then to channel 3");

		clip.setChannelInput(0, 1);
		0.3.wait;
		assertEqual.(clip.getChannelInput(0), 1, "Channel 0 maps to input 1");

		clip.setChannelInput(3, 1);
		0.5.wait;

		assertEqual.(clip.getChannelInput(3), 1, "Channel 3 maps to input 1");

		var ch0InputAfter = clip.getChannelInput(0);
		if (ch0InputAfter.notNil, {
			logFail.("Channel 0 should lose input 1 (reassigned to ch 3), got %".format(ch0InputAfter));
		}, {
			logPass.("Channel 0 lost input 1 (reassigned to ch 3)");
		});

		// ===== TEST 7: Session save/load preserves mappings =====
		log.("Test 7: Session save/load preserves input mappings");

		// Set up specific mapping: input 2 -> channel 1, input 3 -> channel 2
		clip.setChannelInput(1, 2);
		clip.setChannelInput(2, 3);
		0.5.wait;

		var sessionPath = "/tmp/sc-clip-test-input-mapping";

		// Save session
		clip.saveAs(sessionPath, action: {
			log.("Session saved");
			0.5.wait;

			// Shutdown and reload
			clip.shutdown;
			0.5.wait;

			clip = SCClip.load(sessionPath, server: s, action: {
				log.("Session loaded");
				0.5.wait;

				// Verify mappings were restored
				assertEqual.(clip.getChannelInput(1), 2, "Ch 1 → input 2 (after load)");
				assertEqual.(clip.getChannelInput(2), 3, "Ch 2 → input 3 (after load)");

				var ch0After = clip.getChannelInput(0);
				if (ch0After.notNil, {
					logFail.("Channel 0 should still have no input after load, got %".format(ch0After));
				}, {
					logPass.("Channel 0 still has no input after load");
				});

				// ===== TEST 8: Verify node structure (input monitor synths) =====
				log.("Test 8: Verify input monitor synths exist for mapped channels");

				var ch1Monitor = clip.grid.channels[1].inputMonitorSynth;
				var ch2Monitor = clip.grid.channels[2].inputMonitorSynth;

				if (ch1Monitor.notNil, {
					logPass.("Channel 1 has input monitor synth");
				}, {
					logFail.("Channel 1 should have input monitor synth");
				});

				if (ch2Monitor.notNil, {
					logPass.("Channel 2 has input monitor synth");
				}, {
					logFail.("Channel 2 should have input monitor synth");
				});

				// ===== CLEANUP =====
				log.("Cleaning up...");
				0.2.wait;
				clip.shutdown;
				0.5.wait;

				// Clean up test session
				("rm -rf " ++ sessionPath).unixCmd;

				// ===== RESULTS =====
				"========================================".postln;
				if (testPassed, {
					"✓ ALL TESTS PASSED".postln;
					"========================================".postln;

					// Explicitly quit the server before exiting sclang to avoid CI timeout
					Server.killAll;
					{ 0.exit }.defer(0.2);
				}, {
					"✗ TEST FAILED: %".format(failureMessage).postln;
					"========================================".postln;

					Server.killAll;
					{ 1.exit }.defer(0.2);
				});
			});
		});
	});
});
)
