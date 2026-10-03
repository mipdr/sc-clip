// SC-Clip Audio Nudge Test
//
// Checks channel nudging of audio clips against the real server: 2-beat
// loops holding a single click at beat 0.5 are played, the channel's bus is
// recorded from a known beat, and the recorded click positions are compared
// with the beat grid -- while nudging a playing clip, for a clip added after
// the nudge, and after saving and loading a session with nudged channels
// (one earlier, one later).
//
// Boots its own server on port 57140 and points JACK auto-connect at
// nonexistent ports, so it runs beside a live rig without making sound.
//
// Run:
//   SC_JACK_DEFAULT_INPUTS=none SC_JACK_DEFAULT_OUTPUTS=none \
//     QT_QPA_PLATFORM=offscreen sclang Tests/test_nudge.scd
//
// Exit codes:
// 0 = All tests passed
// 1 = Test failed

(
var srv, clip, fails = 0, check, cond = Condition.new;
var bps = 2;  // 120 BPM
var sessionName = "sc-clip-nudge-test";
var makeClickSlot, recordChannel, onsetBeats, checkOnsets;

check = { |name, ok| if (ok) { "PASS %".format(name).postln } { fails = fails + 1; "FAIL %".format(name).postln } };

// Fill a slot with a 2-beat loop holding one click at beat 0.5 (a click at
// beat 0 would be swallowed by the player's attack envelope)
makeClickSlot = { |slot|
	var frames = (2 / bps * srv.sampleRate).round.asInteger;
	var data = FloatArray.newClear(frames);
	data[(0.5 / bps * srv.sampleRate).round.asInteger] = 1;
	Buffer.loadCollection(srv, data, action: { |buf|
		slot.buffer = buf;
		slot.loopLengthBeats = 2;
		slot.loopLengthSamples = frames;
		slot.setState(\stopped);
		cond.unhang;
	});
	cond.hang;
};

// Record a channel's mixer input (where clip players write) for numBeats,
// starting exactly at atBeat. Returns the Buffer.
recordChannel = { |chanIdx, atBeat, numBeats|
	var channel = clip.grid.getChannel(chanIdx);
	var buf = Buffer.alloc(srv, (numBeats / bps * srv.sampleRate).round.asInteger);
	clip.transport.scheduleAtBeat(atBeat, {
		Synth.after(channel.looperGroup, \nudgeTestRec, [\bus, channel.mixerChannel.inbus, \buf, buf]);
	});
	buf
};

// Beats (relative to the recording's start) of the clicks in a recording
onsetBeats = { |buf|
	var onsets = List.new, last = -1000;
	var loaded = Condition(false);  // Inside a Routine the action can run before loadToFloatArray returns
	buf.loadToFloatArray(action: { |data|
		data.do { |x, i|
			if (x.abs > 0.5 and: { i - last > 100 }, { onsets.add(i / srv.sampleRate * bps) });
			if (x.abs > 0.5, { last = i });
		};
		loaded.test = true;
		loaded.signal;
	});
	loaded.wait;
	onsets
};

// First click, then the gaps between clicks (what a nudge changes), each
// within 0.005 beats (2.5 ms): nodes start on 64-sample block boundaries.
// Gaps rather than absolute positions, since server/language clock drift
// (large with long JACK periods) can move a long recording by a few ms.
checkOnsets = { |name, buf, expected|
	var onsets = onsetBeats.(buf);
	var ok = onsets.size == expected.size and: {
		(onsets[0] - expected[0]).abs < 0.005 and: {
			onsets.differentiate.drop(1).every({ |gap, i|
				(gap - expected.differentiate[i + 1]).abs < 0.005 }) } };
	check.("% (got %, expected %)".format(name, onsets.collect(_.round(0.001)), expected), ok);
};

srv = Server(\nudgeTest, NetAddr("127.0.0.1", 57140));
srv.options.numInputBusChannels = 2;
srv.options.numOutputBusChannels = 2;
srv.options.memSize = 8192 * 16;
Server.default = srv;

fork {
	30.wait;
	if (srv.serverRunning.not, { "ERROR: Server boot timed out".postln; 1.exit });
};

srv.waitForBoot({
	var launchBeat, rec, rec0, rec1, slot0, slot1;

	SynthDef(\nudgeTestRec, { |bus, buf|
		RecordBuf.ar(In.ar(bus, 1), buf, loop: 0, doneAction: Done.freeSelf);
	}).add;

	clip = SCClip.new(numChannels: 2, numSlots: 2, server: srv);
	clip.boot(action: { cond.unhang });
	cond.hang;
	srv.sync;
	clip.setTempo(120);
	clip.setQuantization(1);

	slot0 = clip.grid.getSlot(0, 0);
	slot1 = clip.grid.getSlot(1, 0);
	makeClickSlot.(slot0);
	makeClickSlot.(slot1);

	// ===== Nudging a playing clip =====
	clip.launchSlot(0, 0);
	launchBeat = slot0.loopStartBeat;
	rec = recordChannel.(0, launchBeat, 12);
	// Nudge mid-loop, well away from the clicks (loop phase 0.5)
	clip.transport.clock.schedAbs(launchBeat + 3, { clip.nudgeChannel(0, 20); nil });   // +0.04 beat
	clip.transport.clock.schedAbs(launchBeat + 7, { clip.nudgeChannel(0, -50); nil });  // -0.1 beat
	((12 + 1) / bps).wait;
	checkOnsets.("playing nudge", rec, [0.5, 2.5, 4.54, 6.54, 8.44, 10.44]);
	check.("channel nudgeMs", clip.grid.getChannel(0).nudgeMs == -30);

	clip.stopSlot(0, 0);
	1.wait;
	clip.nudgeChannel(1, 40);  // nothing playing: applies to the next launch
	check.("idle channel nudgeMs", clip.grid.getChannel(1).nudgeMs == 40);

	// ===== Save, load, and launch the nudged clips =====
	clip.saveAs(sessionName, action: { cond.unhang });
	cond.hang;
	clip.shutdown;
	1.wait;

	clip = SCClip.load(sessionName, srv, action: { cond.unhang });
	cond.hang;
	srv.sync;
	clip.setQuantization(1);
	slot0 = clip.grid.getSlot(0, 0);
	slot1 = clip.grid.getSlot(1, 0);
	check.("loaded channel nudgeMs (earlier)", clip.grid.getChannel(0).nudgeMs == -30);
	check.("loaded channel nudgeMs (later)", clip.grid.getChannel(1).nudgeMs == 40);

	launchBeat = clip.transport.nextLaunchQuant;
	clip.launchSlot(0, 0);
	clip.launchSlot(1, 0);
	rec0 = recordChannel.(0, launchBeat, 6);
	rec1 = recordChannel.(1, launchBeat, 6);
	((6 + 1) / bps).wait;
	// -30 ms = -0.06 beat, starts that far into the loop at the launch beat
	checkOnsets.("loaded clip nudged earlier", rec0, [0.44, 2.44, 4.44]);
	// +40 ms = +0.08 beat, starts that much after the launch beat
	checkOnsets.("loaded clip nudged later", rec1, [0.58, 2.58, 4.58]);

	// ===== A clip added to the loaded, nudged channel shares its nudge =====
	clip.stopSlot(0, 0);
	clip.stopSlot(1, 0);
	1.wait;
	makeClickSlot.(clip.grid.getSlot(0, 1));
	launchBeat = clip.transport.nextLaunchQuant;
	clip.launchSlot(0, 1);
	rec = recordChannel.(0, launchBeat, 6);
	((6 + 1) / bps).wait;
	checkOnsets.("new clip on nudged channel", rec, [0.44, 2.44, 4.44]);

	clip.shutdown;
	("rm -rf " ++ SCClip.sessionPath(sessionName).quote).unixCmd;
	"\n%".format(if (fails == 0) { "ALL TESTS PASSED" } { "% FAILURES".format(fails) }).postln;
	srv.quit;
	1.wait;
	if (fails == 0) { 0.exit } { 1.exit };
});
)
