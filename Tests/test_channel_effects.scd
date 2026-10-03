/*
 * Test: Channel Effect Selection System
 *
 * Tests the flexible effect selection system for channel effects:
 * - Effect registry with mainControl metadata
 * - Effect swapping in slots
 * - Main control parameter setting
 * - Session save/load
 * - GUI integration
 */

(
fork {
	var clip, passed = 0, failed = 0;
	var test = { |name, condition|
		if (condition, {
			passed = passed + 1;
			"✓ %".format(name).postln;
		}, {
			failed = failed + 1;
			"✗ FAILED: %".format(name).postln;
		});
	};

	"\n=== Testing Channel Effect Selection System ===\n".postln;

	// Initialize sc-clip
	clip = SCClip.new(
		server: Server.default,
		numChannels: 4,
		numSlots: 4,
		channelConfig: [
			(type: \audio, hardwareInput: 0),
			(type: \audio, hardwareInput: 1),
			(type: \audio, hardwareInput: 2),
			(type: \audio, hardwareInput: 3)
		]
	);

	0.5.wait;

	// Test 1: Effect registry loaded
	test.("Effect registry initialized",
		ClipEffectRegistry.all.size > 0
	);

	// Test 2: Bypass effect registered
	test.("Bypass effect registered",
		ClipEffectRegistry.get(\bypass).notNil
	);

	// Test 3: All default effects have mainControl defined
	var allHaveMainControl = true;
	ClipEffectRegistry.all.keysValuesDo { |name, meta|
		if (name != \bypass and: { meta[\mainControl].isNil }, {
			"Effect % missing mainControl".format(name).warn;
			allHaveMainControl = false;
		});
	};
	test.("All effects have mainControl metadata", allHaveMainControl);

	// Test 4: Add effect to channel
	var channel0 = clip.grid.getChannel(0);
	channel0.addEffect(\channelReverb, [\mix, 0.5, \room, 0.7, \damp, 0.3], 0);
	0.1.wait;
	test.("Effect added to channel",
		channel0.getEffectName(0) == \channelReverb
	);

	// Test 5: Main control value tracked
	test.("Main control value tracked",
		channel0.getEffectMainValue(0) == 0.5  // mix parameter
	);

	// Test 6: Swap effect in slot
	channel0.addEffect(\channelDelay, [\mix, 0.3, \delayTime, 0.5, \decayTime, 2], 0);
	0.1.wait;
	test.("Effect swapped in slot",
		channel0.getEffectName(0) == \channelDelay
	);

	// Test 7: Remove effect (bypass)
	channel0.removeEffect(0);
	0.1.wait;
	test.("Effect removed (bypass)",
		channel0.getEffectName(0).isNil
	);

	// Test 8: Set effect main control
	channel0.addEffect(\channelLowPass, [\freq, 1000, \resonance, 0.5, \mix, 1.0], 1);
	0.1.wait;
	channel0.setEffectMainControl(1, 500);  // freq is main control for filters
	0.1.wait;
	test.("Set effect main control",
		channel0.getEffectMainValue(1) == 500
	);

	// Test 9: Multiple effects on different channels
	var channel1 = clip.grid.getChannel(1);
	var channel2 = clip.grid.getChannel(2);
	channel1.addEffect(\channelChorus, [\mix, 0.5, \rate, 0.5, \depth, 0.01, \voices, 4], 0);
	channel2.addEffect(\channelDistortion, [\mix, 1.0, \drive, 0.7], 0);
	0.1.wait;
	test.("Multiple effects on different channels",
		channel1.getEffectName(0) == \channelChorus and: {
			channel2.getEffectName(0) == \channelDistortion
		}
	);

	// Test 10: Session save/load
	var sessionPath = "/tmp/test_channel_effects_session".standardizePath;
	"Creating test session at: %".format(sessionPath).postln;

	// Set up effect chains before saving
	channel0.addEffect(\channelReverb, [\mix, 0.4, \room, 0.6, \damp, 0.5], 0);
	channel0.addEffect(\channelDelay, [\mix, 0.3, \delayTime, 0.5, \decayTime, 3], 1);
	channel1.addEffect(\channelPhaser, [\mix, 0.5, \rate, 0.3, \depth, 0.7, \stages, 4], 2);
	0.2.wait;

	clip.saveSession(sessionPath, {
		"Session saved".postln;

		// Clear effects
		channel0.removeEffect(0);
		channel0.removeEffect(1);
		channel1.removeEffect(0);
		channel1.removeEffect(2);
		0.2.wait;

		// Verify cleared
		var clearedOk = channel0.getEffectName(0).isNil and: {
			channel0.getEffectName(1).isNil and: {
				channel1.getEffectName(2).isNil
			}
		};

		if (clearedOk, {
			"Effects cleared, loading session...".postln;

			clip.loadSession(sessionPath, {
				"Session loaded".postln;
				0.2.wait;

				// Verify effects restored
				var ch0eff0 = clip.grid.getChannel(0).getEffectName(0);
				var ch0eff1 = clip.grid.getChannel(0).getEffectName(1);
				var ch1eff2 = clip.grid.getChannel(1).getEffectName(2);

				test.("Session save/load preserves effect chains",
					ch0eff0 == \channelReverb and: {
						ch0eff1 == \channelDelay and: {
							ch1eff2 == \channelPhaser
						}
					}
				);

				// Test 11: Main control values restored
				var ch0mainVal = clip.grid.getChannel(0).getEffectMainValue(0);
				test.("Main control values restored from session",
					ch0mainVal == 0.4  // mix parameter from reverb
				);

				// Print results
				"\n=== Test Results ===".postln;
				"Passed: %".format(passed).postln;
				"Failed: %".format(failed).postln;
				if (failed == 0, {
					"\n✓ All tests passed!".postln;
				}, {
					"\n✗ Some tests failed".postln;
				});

				// Cleanup
				clip.free;
			});
		}, {
			"Failed to clear effects".postln;
			clip.free;
		});
	});
};
)
