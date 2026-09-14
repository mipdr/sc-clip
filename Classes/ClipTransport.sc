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
	var <syncMode;  // \internal, \link, or \midiclock
	var <server;

	*new { |tempo = 120, beatsPerBar = 4, timeSignature, server|
		^super.newCopyArgs(
			clock: nil,
			tempo: tempo,
			beatsPerBar: beatsPerBar,
			timeSignature: timeSignature ? [4, 4],  // Default 4/4
			quantization: nil,
			syncMode: \internal,
			server: server ? Server.default
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

	// Enable MIDI clock output
	enableMIDIClock { |port|
		// TODO: Implement MIDI clock output
		// This would send MIDI timing clock messages (24 ppq)
		// and start/stop/continue messages

		"ClipTransport: MIDI clock output not yet implemented".warn;
		syncMode = \midiclock;
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

	// Cleanup
	free {
		clock.stop;
		clock.clear;
	}

}
