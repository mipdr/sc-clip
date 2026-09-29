// SC-Clip MIDI Clip Test
//
// Exercises the notation parser and the MIDI slot/channel state machine
// (write, launch, rewrite while playing, mute/solo, scene stop, recording
// with source/channel filtering, empty recordings, session data round trip)
// against a mock MIDIOut. No audio server, MIDI hardware or ALSA needed:
// MIDI input is simulated with MIDIIn.doNoteOnAction.
//
// Run: QT_QPA_PLATFORM=offscreen sclang Tests/test_midi_clips.scd
//
// Exit codes:
// 0 = All tests passed
// 1 = Test failed

(
var p = ClipMIDINotationParser.new, fails = 0, check, sent = List.new, out, transport, grid, slot, slot1, data, path;
check = { |name, ok| if (ok) { "PASS %".format(name).postln } { fails = fails + 1; "FAIL %".format(name).postln } };
out = (latency: 0.2,
	noteOn: { |self, ch, n, v| sent.add([\on, ch, n, v, transport.beat.round(0.01)]) },
	noteOff: { |self, ch, n, v| sent.add([\off, ch, n, v, transport.beat.round(0.01)]) });

// --- Parser ---
check.("shorthand", p.parse("c4 d4 e4").select({ |e| e.type == \noteOn }).collect(_.note) == [60, 62, 64] and: { p.totalBeats == 3 });
check.("chord", p.parse("[c4 e4 g4]:2:100:0.9").select({ |e| e.type == \noteOn }).collect(_.note).sort == [60, 64, 67] and: { p.totalBeats == 2 });
check.("chord then note", p.parse("[c4 e4]:1 g4").detect({ |e| e.note == 67 }).time == 1);
check.("rest advances time", p.parse("c4:1 ~:1:0:0 e4:1").detect({ |e| e.note == 64 }).time == 2 and: { p.totalBeats == 3 });
check.("accidentals/case", p.parse("C#4 eb4 bs3 cf5").select({ |e| e.type == \noteOn }).collect(_.note) == [61, 63, 60, 71]);
check.("repeated note: off before on", p.parse("c4:1:100:1 c4:1:100:1").collect(_.type) == [\noteOn, \noteOff, \noteOn, \noteOff]);
check.("invalid -> nil", p.parse("c4 x9 e4").isNil and: { p.parse("c").isNil } and: { p.parse("[c4 e4").isNil });

// --- Grid of 2 MIDI channels (no server needed) ---
transport = ClipTransport.new(tempo: 480, server: Server.default);  // 8 beats/sec
transport.setQuantization(1);
grid = ClipGrid.new(2, 4, transport, nil, Server.default, nil,
	[(type: \midi, midiInChannel: 0, midiOutChannel: 0, midiInSrc: 1234), (type: \midi, midiOutChannel: 1)], out);
slot = grid.getSlot(0, 0);
slot1 = grid.getSlot(1, 0);

grid.printStatus;
check.("printStatus ok", true);

check.("writeMidiClip padded", slot.loadFromNotation("c4:1 d4:1 e4:2 g4:4", true) and: { slot.loopLengthBeats == 8 } and: { slot.isStopped });
check.("unpadded length = sum of durations", slot1.loadFromNotation("c4 d4 e4") and: { slot1.loopLengthBeats == 3 });
check.("bad notation rejected", grid.getChannel(0).loadMIDIClip(1, "zz") == false and: { grid.getSlot(0, 1).isEmpty });
check.("legato past end clamped", { var s = grid.getSlot(0, 2); s.loadFromNotation("c4:1:100:2"); s.midiEvents.collect(_.time).maxItem == 1 }.value);
grid.clearSlot(0, 2);

Routine {
	var ch0 = grid.getChannel(0);
	grid.launchSlot(0, 0);
	check.("queued", slot.isQueuedToPlay);
	1.5.wait;   // 12 beats @ 8 b/s
	check.("playing", slot.isPlaying);
	check.("notes sent on ch 0", sent.select({ |m| m[0] == \on }).collect(_[2]).asSet == Set[60, 62, 64, 67]);
	check.("midiOut latency = server latency", out.latency == Server.default.latency);

	// swap content while playing
	slot.loadFromNotation("a4:2", true);
	check.("still playing after rewrite", slot.isPlaying);
	sent.clear; 2.wait;
	{ var ons = sent.select({ |m| m[0] == \on }).collect(_[2]); var i = ons.indexOf(69);
  check.("rewritten clip plays at loop boundary", i.notNil and: { ons.copyToEnd(i).every(_ == 69) }) }.value;

	// mute
	ch0.mute; sent.clear; 1.wait;
	check.("muted: no note-ons", sent.select({ |m| m[0] == \on }).isEmpty);
	ch0.unMute;

	// solo/scene/stop via grid
	grid.soloChannel(1); grid.unSoloChannel(1);
	grid.stopScene(0);
	0.5.wait;
	check.("stopScene stopped", slot.isStopped);
	check.("all notes released", slot.soundingNotes.isEmpty);

	// --- Recording with source filter ---
	grid.clearSlot(0, 0);
	grid.armSlot(0, 0, 4);
	grid.launchSlot(0, 0);
	0.3.wait;
	check.("recording", slot.isRecording);
	MIDIIn.doNoteOnAction(9999, 0, 50, 100);    // wrong source (e.g. Launchpad): ignored
	MIDIIn.doNoteOnAction(1234, 0, 60, 90);
	0.1.wait;
	MIDIIn.doNoteOffAction(1234, 0, 60, 0);
	MIDIIn.doNoteOnAction(1234, 1, 61, 90);     // wrong MIDI channel: ignored
	MIDIIn.doNoteOnAction(1234, 0, 64, 80);     // held past loop end
	0.8.wait;
	check.("recording -> playing", slot.isPlaying);
	check.("recorded only filtered notes", slot.midiEvents.collect(_.note).asSet == Set[60, 64]);
	check.("held note closed at loop end", slot.midiEvents.detect({ |e| e.note == 64 and: { e.type == \noteOff } }).time == 4);

	// --- Empty recording ---
	grid.armSlot(1, 1, 2); grid.launchSlot(1, 1);
	0.6.wait;
	check.("empty recording -> empty slot", grid.getSlot(1, 1).isEmpty);

	// --- Session data round trip ---
	path = thisProcess.platform.defaultTempDir +/+ "midi-slot-test.scd";
	data = slot.asSessionData;
	data.writeArchive(path);
	grid.getSlot(1, 2).restoreSessionData(Object.readArchive(path));
	check.("session round trip", grid.getSlot(1, 2).midiEvents.size == slot.midiEvents.size and: { grid.getSlot(1, 2).isStopped });

	// clear while recording is safe
	grid.clearSlot(0, 0);
	grid.armSlot(0, 0, 4); grid.launchSlot(0, 0); 0.2.wait; grid.clearSlot(0, 0); 0.8.wait;
	check.("clear during recording", slot.isEmpty);

	grid.free;
	"\n%".format(if (fails == 0) { "ALL TESTS PASSED" } { "% FAILURES".format(fails) }).postln;
	0.exit;
}.play(AppClock);
)
