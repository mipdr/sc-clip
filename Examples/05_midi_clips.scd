/*
 * SC-Clip Example: MIDI Clips
 *
 * MIDI channels sit in the same grid as audio channels and use the same
 * slot workflow (arm/launch/stop/clear, quantized launches, scenes,
 * Launchpad pads), but their clips hold MIDI notes instead of audio: they
 * are played out to a MIDIOut, and recorded from MIDI input.
 *
 * Two ways to fill a MIDI slot:
 *   - Write it from a notation string:  ~clip.writeMidiClip(ch, slot, "c4 e4 g4")
 *   - Record it from a keyboard:        ~clip.armSlot(ch, slot, 8); ~clip.launchSlot(ch, slot)
 *
 * Requirements:
 *   - Something to receive MIDI (hardware synth, soft synth) -- or use
 *     section 2b to hear the clips through SuperCollider's default synth
 *   - Optional: a MIDI keyboard for recording (section 6)
 *
 * Evaluate the blocks one at a time (Ctrl+Enter / Cmd+Enter inside each
 * set of parentheses).
 */

// ===================================
// 1. MIDI setup
// ===================================
(
MIDIClient.init;
MIDIIn.connectAll;
"MIDI destinations:".postln;
MIDIClient.destinations.do { |ep, i| "  % %".format(i, ep).postln };
"MIDI sources:".postln;
MIDIClient.sources.do { |ep| "  %".format(ep).postln };
)

// ===================================
// 2a. MIDI output: a real synth
// ===================================
// Pick the destination your synth is on. On Linux, sends only reach a
// hardware port that SC's out port is subscribed to, hence connect.
(
var dest = MIDIClient.destinations.detect { |ep| ep.device == "UMC404HD 192k" };  // <- your device

~midiOut = MIDIOut(MIDIClient.destinations.indexOf(dest), dest.uid);
if (thisProcess.platform.name == \linux) { ~midiOut.connect(dest) };
~midiInSrc = "UMC404HD 192k";  // device your keyboard is on (for recording), or nil for any
)

// ===================================
// 2b. MIDI output: no hardware, hear it in SuperCollider instead
// ===================================
// Sends to the "Midi Through" loopback port (Linux), and plays whatever
// comes back out of it on SC's default synth, one voice per note.
// (Use this OR 2a.)
(
var through = MIDIClient.destinations.detect { |ep| ep.device == "Midi Through" };
var throughIn = MIDIClient.sources.detect { |ep| ep.device == "Midi Through" };

~midiOut = MIDIOut(MIDIClient.destinations.indexOf(through), through.uid);
if (thisProcess.platform.name == \linux) { ~midiOut.connect(through) };
~midiInSrc = nil;  // record from anything (don't record the loopback: see section 6)

s.waitForBoot {
	~voices = Array.newClear(128 * 16);
	MIDIdef.noteOn(\throughOn, { |vel, note, chan|
		~voices[chan * 128 + note] !? (_.release);
		~voices[chan * 128 + note] = Synth(\default, [
			\freq, note.midicps, \amp, vel / 127 * 0.2, \pan, chan.linlin(0, 1, -0.5, 0.5)
		]);
	}, srcID: throughIn.uid);
	MIDIdef.noteOff(\throughOff, { |vel, note, chan|
		~voices[chan * 128 + note] !? (_.release);
		~voices[chan * 128 + note] = nil;
	}, srcID: throughIn.uid);
	"Monitoring Midi Through on the default synth".postln;
};
)

// ===================================
// 3. Boot SC-Clip with 2 audio + 2 MIDI channels
// ===================================
// channelConfig has one entry per channel. MIDI channel keys:
//   midiOutChannel -- MIDI channel clips are played on (0-15, 0 = "channel 1")
//   midiInChannel  -- MIDI channel recorded from (default 0)
//   midiInSrc      -- device name or uid to record from (default: any source).
//                     Set it if a grid controller sends notes on the same
//                     MIDI channel (a Launchpad Mini does, on channel 0) --
//                     otherwise pad presses get recorded into the clip.
(
~clip = SCClip.new(
	numChannels: 4,
	numSlots: 8,
	server: s,
	channelConfig: [
		(type: \audio, hardwareInput: 0),
		(type: \audio, hardwareInput: 1),
		(type: \midi, midiOutChannel: 0, midiInChannel: 0, midiInSrc: ~midiInSrc),  // channel 2 -> MIDI ch 1
		(type: \midi, midiOutChannel: 1, midiInChannel: 0, midiInSrc: ~midiInSrc)   // channel 3 -> MIDI ch 2
	],
	midiOut: ~midiOut
);
~clip.boot(action: { |clip|
	clip.setTempo(110);
	clip.printStatus;
});
)

// ===================================
// 4. Write clips from notation
// ===================================
// Each token is  note:duration:velocity:legato  -- everything after the
// note is optional (defaults: duration 1 beat, velocity 100, legato 0.8).
//   c4 = middle C (MIDI 60); sharps cs4 / c#4, flats df4 / db4
//   chord: [c4 e4 g4]:2    rest: ~:1
// The clip loops over the summed duration of its tokens. Pass padding
// true to round that up to 1, 2, 4, 8, 16, 32 or 64 beats instead.
// writeMidiClip returns true if the notation parsed.

// Melody, 4 beats
~clip.writeMidiClip(2, 0, "c4 d4 e4 g4");

// Same notes with durations, velocities and legato (staccato 0.3)
~clip.writeMidiClip(2, 1, "c4:0.5:120:0.3 c4:0.5:60:0.3 g4:1:110 ~:1 e4:0.5 d4:0.5");

// Chord progression, 8 beats (I - vi - IV - V)
~clip.writeMidiClip(3, 0, "[c3 e3 g3]:2:90:0.95 [a2 c3 e3]:2:80:0.95 [f2 a2 c3]:2:85:0.95 [g2 b2 d3]:2:95:0.95");

// 3-beat pattern padded to a 4-beat loop (last beat silent)
~clip.writeMidiClip(2, 2, "c5:0.5 e5:0.5 g5:1 c6:1", padding: true);

// Bass line with rests, on channel 3's MIDI channel
~clip.writeMidiClip(3, 1, "c2:0.75:120 ~:0.25 c2:0.75:120 ~:0.25 c2:0.5 ~:0.5 g1:0.5:110 as1:0.5");

// Invalid notation is rejected (the slot is left as it was)
~clip.writeMidiClip(2, 3, "c4 h4 e4");  // -> false, "Unknown note name: h"

// ===================================
// 5. Launch, stop, rewrite
// ===================================
// Launches are quantized like audio clips (next bar by default).
// Don't arm a written slot before launching -- arming means "record over it".

~clip.launchSlot(2, 0);   // melody
~clip.launchSlot(3, 0);   // chords

// Rewrite a playing clip: the new version starts at the next loop boundary
~clip.writeMidiClip(2, 0, "e4 g4 a4 c5");
~clip.writeMidiClip(2, 0, "[e4 c5]:0.5 g4:0.5 a4:0.5 g4:0.5 e4:1 d4:1");

// Switching clips: stop one, launch another in the same channel
~clip.stopSlot(2, 0); ~clip.launchSlot(2, 1);

// Mute/solo work on MIDI channels too (mute stops sending note-ons and
// releases held notes). Level, pan and effects are audio-only.
~clip.muteChannel(3);
~clip.grid.getChannel(3).unMute;
~clip.soloChannel(2);
~clip.unSoloChannel(2);

// Scenes and stop-all cover audio and MIDI channels alike
~clip.launchScene(1);
~clip.stopAll;

// Clear a slot (sends note-offs for anything it left sounding)
~clip.clearSlot(2, 2);

// ===================================
// 6. Record a clip from a MIDI keyboard
// ===================================
// Arm with a loop length in beats, then launch: recording starts at the
// next quantization point, runs for the loop length, then the clip starts
// looping straight away. Notes held past the end are closed at the loop end.
// A recording with no notes leaves the slot empty.
//
// Only notes from the channel's midiInSrc device, on its midiInChannel,
// are recorded. (With section 2b's loopback and midiInSrc nil, clips that
// are playing while you record would be recorded too -- stop them first.)
(
~clip.armSlot(2, 4, 8);   // 8 beats = 2 bars in 4/4
~clip.launchSlot(2, 4);
)

// Check what was recorded
~clip.grid.getSlot(2, 4).midiEvents.do(_.postln);

// Re-record over it: arm again (it keeps its loop length) and launch
~clip.stopSlot(2, 4);
(
~clip.armSlot(2, 4);
~clip.launchSlot(2, 4);
)

// ===================================
// 7. Launchpad Mini
// ===================================
// MIDI channels get Launchpad columns like audio channels: pressing an
// empty pad arms + records (length from the clip length column), pressing
// a written/recorded clip plays or stops it, long press clears it.
~launchpad = ~clip.connectController(\launchpadMini, "Launchpad Mini", 8, 4);

// ===================================
// 8. Save and load
// ===================================
// MIDI clips and the channel layout are stored in the session's
// session.scd (no audio files for them).
~clip.saveAs("midi-example");

// Load it back (needs the MIDIOut again, since MIDI channels play through it)
(
~clip.shutdown;
~clip = SCClip.load("midi-example", s,
	action: { |clip| clip.printStatus },
	midiOut: ~midiOut
);
)

// ===================================
// 9. Cleanup
// ===================================
(
~launchpad !? (_.disconnect);
~clip.shutdown;
MIDIdef(\throughOn).free; MIDIdef(\throughOff).free;
)
