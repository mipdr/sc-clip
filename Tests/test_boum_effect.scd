/*
 * SC-Clip Boum Effect Test
 *
 * Tests the Boum effect implementation:
 * - SynthDef compilation (masterBoum, channelBoum)
 * - Compressor slot system (setCompressorType)
 * - Node order verification (EQ → Boum → Limiter)
 * - Parameter setting
 * - Channel-level Boum effect
 *
 * Exit codes:
 * 0 = All tests passed
 * 1 = Test failed
 *
 * Usage:
 *   QT_QPA_PLATFORM=offscreen sclang -D Tests/test_boum_effect.scd
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

var assertNotNil = { |value, label|
	if (value.isNil, {
		logFail.("% is nil".format(label));
	}, {
		logPass.("% is not nil".format(label));
	});
};

"========================================".postln;
"SC-Clip Boum Effect Test".postln;
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

	// ===== TEST 1: Verify SynthDefs exist =====
	log.("Test 1: Verify Boum SynthDefs are registered");
	0.5.wait;

	if (SynthDescLib.global[\masterBoum].isNil, {
		logFail.("SynthDef \\masterBoum not found");
	}, {
		logPass.("SynthDef \\masterBoum is registered");
	});

	if (SynthDescLib.global[\channelBoum].isNil, {
		logFail.("SynthDef \\channelBoum not found");
	}, {
		logPass.("SynthDef \\channelBoum is registered");
	});

	// ===== TEST 2: Create SC-Clip instance =====
	log.("Test 2: Create SC-Clip instance");
	clip = SCClip.new(numChannels: 2, numSlots: 4, server: s);
	clip.boot(action: {
		log.("SC-Clip booted");

		// Small delay to ensure everything is initialized
		0.5.wait;

		// ===== TEST 3: Add mastering chain (default glueComp) =====
		log.("Test 3: Add mastering chain with default glueComp");
		clip.masterBus.addMasteringChain;
		0.2.wait;

		assertNotNil.(clip.masterBus.eqSynth, "EQ synth");
		assertNotNil.(clip.masterBus.compSynth, "Compressor synth");
		assertNotNil.(clip.masterBus.limiterSynth, "Limiter synth");
		assertEqual.(clip.masterBus.compressorType, \glueComp, "Default compressor type");

		// ===== TEST 4: Verify node order (glueComp) =====
		log.("Test 4: Verify node order: EQ → glueComp → Limiter");
		var eqNode = clip.masterBus.eqSynth.nodeID;
		var compNode = clip.masterBus.compSynth.nodeID;
		var limNode = clip.masterBus.limiterSynth.nodeID;

		if (eqNode < compNode and: { compNode < limNode }, {
			logPass.("Node order correct: EQ (%) → Comp (%) → Lim (%)".format(eqNode, compNode, limNode));
		}, {
			logFail.("Node order incorrect: EQ (%), Comp (%), Lim (%)".format(eqNode, compNode, limNode));
		});

		// ===== TEST 5: Switch to Boum =====
		log.("Test 5: Switch compressor type to Boum");
		clip.masterBus.setCompressorType(\boum);
		0.3.wait;

		assertEqual.(clip.masterBus.compressorType, \boum, "Compressor type after switch");
		assertNotNil.(clip.masterBus.compSynth, "Compressor synth after switch");

		// ===== TEST 6: Verify node order (Boum) =====
		log.("Test 6: Verify node order after switch: EQ → Boum → Limiter");
		eqNode = clip.masterBus.eqSynth.nodeID;
		compNode = clip.masterBus.compSynth.nodeID;
		limNode = clip.masterBus.limiterSynth.nodeID;

		if (eqNode < compNode and: { compNode < limNode }, {
			logPass.("Node order correct after switch: EQ (%) → Boum (%) → Lim (%)".format(eqNode, compNode, limNode));
		}, {
			logFail.("Node order incorrect after switch: EQ (%), Boum (%), Lim (%)".format(eqNode, compNode, limNode));
		});

		// ===== TEST 7: Set all Boum parameters =====
		log.("Test 7: Set all Boum parameters");
		clip.masterBus.setBoum(
			thresh: -18,
			ratio: 6,
			attack: 0.005,
			release: 0.2,
			scHPF: 1,  // 75 Hz
			drive: 6,
			type: 1,  // Tube
			hicut: 8000,
			gateThresh: -50,
			makeupGain: 4,
			mix: 0.8,
			bypass: 0
		);
		0.1.wait;
		logPass.("All Boum parameters set without error");

		// ===== TEST 8: Add channel-level Boum effect =====
		log.("Test 8: Add Boum to channel 0");
		clip.channels[0].addEffect(\channelBoum);
		0.2.wait;
		logPass.("Channel-level Boum effect added");

		// ===== TEST 9: Set channel Boum parameters =====
		log.("Test 9: Set channel Boum parameters");
		clip.channels[0].setEffectParam(\channelBoum, \thresh, -20);
		clip.channels[0].setEffectParam(\channelBoum, \drive, 3);
		clip.channels[0].setEffectParam(\channelBoum, \type, 2);  // Fuzz
		clip.channels[0].setEffectParam(\channelBoum, \mix, 0.5);
		0.1.wait;
		logPass.("Channel Boum parameters set without error");

		// ===== TEST 10: Switch back to glueComp =====
		log.("Test 10: Switch back to glueComp");
		clip.masterBus.setCompressorType(\glueComp);
		0.3.wait;

		assertEqual.(clip.masterBus.compressorType, \glueComp, "Compressor type after switch back");
		assertNotNil.(clip.masterBus.compSynth, "Compressor synth after switch back");

		// Verify node order still correct
		eqNode = clip.masterBus.eqSynth.nodeID;
		compNode = clip.masterBus.compSynth.nodeID;
		limNode = clip.masterBus.limiterSynth.nodeID;

		if (eqNode < compNode and: { compNode < limNode }, {
			logPass.("Node order correct after switch back: EQ (%) → Comp (%) → Lim (%)".format(eqNode, compNode, limNode));
		}, {
			logFail.("Node order incorrect after switch back: EQ (%), Comp (%), Lim (%)".format(eqNode, compNode, limNode));
		});

		// ===== TEST 11: Remove channel Boum effect =====
		log.("Test 11: Remove channel Boum effect");
		clip.channels[0].removeEffect(\channelBoum);
		0.1.wait;
		logPass.("Channel Boum effect removed without error");

		// ===== CLEANUP =====
		log.("Cleaning up...");
		0.2.wait;
		clip.shutdown;
		0.5.wait;

		// ===== RESULTS =====
		"========================================".postln;
		if (testPassed, {
			"✓ ALL TESTS PASSED".postln;
			"========================================".postln;
			0.exit;
		}, {
			"✗ TEST FAILED: %".format(failureMessage).postln;
			"========================================".postln;
			1.exit;
		});
	});
});
)
