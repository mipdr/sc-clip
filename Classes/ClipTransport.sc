/*
 * ClipTransport
 *
 * Manages tempo clock, beat quantization, and scheduling.
 * Supports TempoClock (default), LinkClock, and MIDI clock sync.
 */

ClipTransport {
	var <clock;
	var <tempo;  // BPM
	var <beatsPerBar;
	var <timeSignature;  // e.g. [4, 4] for 4/4
	var <quantization;  // Quant object for launch/stop timing
	var <quantMode;  // How clip launches are quantized: \grid or \longestClip (see nextLaunchQuant)
	var <>clipSource;  // Function returning all ClipSlots (set by ClipGrid) -- used by \longestClip
	var <syncMode;  // \internal, \link, or \midiclock
	var <server;
	var <metronomeEnabled;
	var <metronomeTask;
	var <metronomeAmp;
	var <midiClockEnabled;
	var <midiClockOut;   // caller-supplied MIDIOut (e.g. MIDIOut.newByName(...)) -- keeps this class hardware-agnostic
	var <midiClockTask;

	*new { |tempo = 120, beatsPerBar = 4, timeSignature, server|
		^super.newCopyArgs(
			nil,                      // clock
			tempo,                    // tempo
			beatsPerBar,              // beatsPerBar
			timeSignature ? [4, 4],   // timeSignature (default 4/4)
			nil,                      // quantization
			\grid,                    // quantMode
			nil,                      // clipSource
			\internal,                // syncMode
			server ? Server.default,  // server
			false,                    // metronomeEnabled
			nil,                      // metronomeTask
			0.3,                      // metronomeAmp
			false,                    // midiClockEnabled
			nil,                      // midiClockOut
			nil                       // midiClockTask
		).init;
	}

	init {
		// Create internal TempoClock by default
		clock = TempoClock.new(tempo / 60);  // TempoClock uses beats per second

		// Default quantization: 1 bar
		quantization = Quant(beatsPerBar, 0, 0);

		"ClipTransport: Initialized @ % BPM, %/% time, quantization = % beats"
			.format(tempo, timeSignature[0], timeSignature[1], beatsPerBar)
			.postln;
	}

	// Set tempo in BPM
	setTempo { |bpm|
		tempo = bpm;
		clock.tempo = bpm / 60;  // Convert to beats per second

		"ClipTransport: Tempo set to % BPM".format(bpm).postln;
	}

	// Set time signature (e.g. [3, 4] for 3/4, [6, 8] for 6/8)
	setTimeSignature { |numerator, denominator|
		timeSignature = [numerator, denominator];
		beatsPerBar = numerator;  // Beats per bar = numerator

		// Update quantization to match new bar length
		this.setQuantization(beatsPerBar);

		"ClipTransport: Time signature set to %/%".format(numerator, denominator).postln;
	}

	// Set quantization in beats (typically 1, 4, 8, etc.)
	// Pass 0 for no quantization (immediate)
	setQuantization { |beats|
		if (beats == 0, {
			quantization = 0;  // No quantization
			"ClipTransport: Quantization disabled (immediate mode)".postln;
		}, {
			quantization = Quant(beats, 0, 0);
			"ClipTransport: Quantization set to % beats".format(beats).postln;
		});
	}

	// Set how clip launches are quantized:
	//   \grid        -- next multiple of the quantization setting (default)
	//   \longestClip -- next time the longest running clip wraps back to its
	//                   start, so e.g. a 4-bar clip launched while an 8-bar
	//                   clip plays waits for the 8-bar phrase to restart.
	//                   Falls back to \grid when no clip is running.
	// Stops always use \grid.
	setQuantMode { |mode|
		if ([\grid, \longestClip].includes(mode).not, {
			"ClipTransport: Unknown quant mode % (use \\grid or \\longestClip)".format(mode).error;
			^this;
		});
		quantMode = mode;
		"ClipTransport: Quant mode set to %".format(mode).postln;
	}

	// Get current beat
	beat {
		^clock.beats;
	}

	// Get next bar boundary (aligned to beatsPerBar)
	nextBar {
		var currentBeat = clock.beats;
		var nextBarBeat = currentBeat.roundUp(beatsPerBar);
		^nextBarBeat;
	}

	// Get next quantization boundary
	nextQuant {
		if (quantization == 0, {
			^clock.beats;  // Immediate
		}, {
			var currentBeat = clock.beats;
			var quantBeats = quantization.quant;
			var nextQuantBeat = currentBeat.roundUp(quantBeats);
			^nextQuantBeat;
		});
	}

	// Get the beat at which a clip launch (play or record) should start,
	// according to quantMode
	nextLaunchQuant {
		^switch(quantMode,
			\longestClip, { this.nextLongestClipStart ? this.nextQuant },
			{ this.nextQuant }
		);
	}

	// Next beat at which the longest running clip starts its loop again on
	// the beat grid (ignoring its channel nudge), or nil if no clip is running. Queued-to-play clips count (at their
	// scheduled start) so a scene of clips launched together stays aligned;
	// queued-to-stop clips don't, since they're on their way out.
	nextLongestClipStart {
		var running, longest, cycles;

		if (clipSource.isNil, { ^nil });

		running = clipSource.value.select({ |slot|
			[\recording, \playing, \overdubbing, \queuedToPlay].includes(slot.state)
				and: { slot.loopStartBeat.notNil }
				and: { slot.loopLengthBeats.notNil }
		});
		if (running.isEmpty, { ^nil });

		longest = running.maxItem(_.loopLengthBeats);
		cycles = ((clock.beats - longest.gridStartBeat) / longest.loopLengthBeats).ceil;
		^longest.gridStartBeat + (cycles * longest.loopLengthBeats);
	}

	// Schedule a function at a specific beat (absolute time)
	scheduleAtBeat { |beat, func|
		var latency = server.latency;  // Use server latency for sample-accurate timing

		clock.schedAbs(beat, {
			// Wrap in server bundle with latency for sample-accurate execution
			server.makeBundle(latency, func);
			nil;  // Don't reschedule
		});

		// "ClipTransport: Scheduled at beat % (current: %)"
		// 	.format(beat.round(0.01), clock.beats.round(0.01))
		// 	.postln;
	}

	// Schedule a function after N beats from now (relative time)
	scheduleAfterBeats { |beats, func|
		var targetBeat = clock.beats + beats;
		this.scheduleAtBeat(targetBeat, func);
	}

	// Schedule a function quantized to next quantization boundary
	scheduleQuantized { |func|
		var nextBeat = this.nextQuant;
		this.scheduleAtBeat(nextBeat, func);
	}

	// Schedule immediate (no quantization, no latency)
	scheduleImmediate { |func|
		server.makeBundle(0, func);  // latency = 0 for instant response
	}

	// Enable Ableton Link sync (requires LinkClock)
	enableLink {
		if (LinkClock.isNil, {
			"ClipTransport: LinkClock not available (requires SC 3.9+)".error;
			^this;
		});

		clock.stop;  // Stop current clock

		// Create LinkClock with current tempo
		clock = LinkClock.new(tempo / 60);
		syncMode = \link;

		"ClipTransport: Switched to Ableton Link sync @ % BPM".format(tempo).postln;
	}

	// Enable MIDI clock output: sends realtime Clock messages (0xF8, 24
	// pulses per quarter note per the MIDI spec) out midiOut, following
	// this transport's tempo -- for syncing external gear (drum machines,
	// other sequencers) while SC-Clip runs standalone. midiOut is a
	// caller-supplied MIDIOut (e.g. MIDIOut.newByName("your device", ...)),
	// so this class doesn't need to know about any particular interface.
	// This is MIDI clock OUTPUT (this transport as master) -- separate
	// from syncMode/enableLink, which is about what drives this
	// transport's own clock.
	enableMIDIClock { |midiOut|
		if (midiClockEnabled, {
			"ClipTransport: MIDI clock already enabled".warn;
			^this;
		});

		midiClockOut = midiOut;
		midiClockEnabled = true;

		midiClockTask = Routine({
			loop {
				// Same latency as audio bundles and MIDI clip notes, so gear
				// following this clock plays in time with them (set per pulse
				// to follow later server.latency changes)
				midiClockOut.latency = server.latency;
				midiClockOut.midiClock;
				(1/24).wait;  // 24 clock pulses per quarter note
			};
		}).play(clock, quant: 1);

		"ClipTransport: MIDI clock output enabled (24 ppqn)".postln;
	}

	// Stop MIDI clock output
	disableMIDIClock {
		if (midiClockEnabled.not, {
			"ClipTransport: MIDI clock already disabled".warn;
			^this;
		});

		if (midiClockTask.notNil, {
			midiClockTask.stop;
			midiClockTask = nil;
		});

		midiClockEnabled = false;
		midiClockOut = nil;

		"ClipTransport: MIDI clock output disabled".postln;
	}

	// Switch back to internal clock
	useInternalClock {
		if (syncMode != \internal, {
			clock.stop;
			clock = TempoClock.new(tempo / 60);
			syncMode = \internal;

			"ClipTransport: Switched to internal clock".postln;
		});
	}

	// Metronome control

	enableMetronome { |amp = 0.3|
		if (metronomeEnabled, {
			"ClipTransport: Metronome already enabled".warn;
			^this;
		});

		metronomeAmp = amp;
		metronomeEnabled = true;

		// Create task that triggers on every beat
		metronomeTask = Routine({
			loop {
				var currentBeat = clock.beats;
				var beatInBar = currentBeat % beatsPerBar;
				var isDownbeat = (beatInBar < 0.01);  // First beat of bar

				// Play click synth, with the same latency as clip launches and
				// MIDI (clock and clips) -- sent straight away, it sounded
				// server.latency ahead of everything else
				server.makeBundle(server.latency, {
					Synth(\metronomeClick, [
						\out, 0,
						\isDownbeat, isDownbeat.asInteger,
						\amp, metronomeAmp
					], server);
				});

				1.wait;  // Every beat
			};
		}).play(clock, quant: 1);

		"ClipTransport: Metronome enabled (amp: %)".format(amp).postln;
	}

	disableMetronome {
		if (metronomeEnabled.not, {
			"ClipTransport: Metronome already disabled".warn;
			^this;
		});

		if (metronomeTask.notNil, {
			metronomeTask.stop;  // Stop just this routine (not clock.clear -- that
			// would also wipe MIDI clock output and any pending slot schedules)
			metronomeTask = nil;
		});

		metronomeEnabled = false;

		"ClipTransport: Metronome disabled".postln;
	}

	setMetronomeVolume { |amp|
		metronomeAmp = amp;
		"ClipTransport: Metronome volume set to %".format(amp).postln;
	}

	// Cleanup
	free {
		this.disableMetronome;
		this.disableMIDIClock;
		clock.stop;
		clock.clear;
	}

}
