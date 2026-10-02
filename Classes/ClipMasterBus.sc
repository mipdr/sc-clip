/*
 * ClipMasterBus
 *
 * Master bus with mastering chain (EQ, compression, limiting).
 * Receives audio from all ClipChannel instances.
 */

ClipMasterBus {
	var <mixerChannel;  // MixerChannel instance for master
	var <server;
	var <eqSynth;
	var <compSynth;
	var <compressorType;  // Current compressor type: \glueComp or \boum
	var <limiterSynth;
	var <limiterCeiling = -0.3;  // dB
	var <limiterDur = 0.01;      // lookahead, seconds

	*new { |server|
		^super.newCopyArgs(
			nil,                      // mixerChannel
			server ? Server.default,  // server
			nil,                      // eqSynth
			nil,                      // compSynth
			\glueComp,                // compressorType (default)
			nil                       // limiterSynth
		).init;
	}

	init {
		// Create master MixerChannel (stereo)
		mixerChannel = MixerChannel.new(
			\master,
			server,
			2,  // 2 input channels (stereo from all channels)
			2,  // 2 output channels (stereo out to hardware)
			outbus: 0,  // Hardware output bus 0-1
			level: 0.dbamp  // Unity gain
		);

		"ClipMasterBus: Initialized".postln;
	}

	// Add mastering chain (EQ → Compressor → Limiter)
	//
	// The synths go in the master MixerChannel's effect group (via playfx,
	// same as ClipChannel.addEffect), which runs after every channel has
	// written into the master inbus and before the master fader. A separate
	// Group.new(server) would land at the head of the default group -- i.e.
	// BEFORE the channels -- so the chain would process silence and the dry
	// channel audio would be summed in after it, making EQ/comp/limiter inaudible.
	// playfx adds each synth at the effect group's tail, so they run in the
	// order they're created here.
	addMasteringChain {
		var bus = mixerChannel.inbus;

		if (eqSynth.notNil, {
			"ClipMasterBus: Mastering chain already active".postln;
			^this;
		});

		// EQ (neutral by default)
		eqSynth = mixerChannel.playfx(\masterEQ, [
			\inBus, bus,
			\outBus, bus,
			\loFreq, 80,
			\loGain, 0,
			\midFreq, 1000,
			\midGain, 0,
			\midQ, 1,
			\hiFreq, 8000,
			\hiGain, 0
		]);

		// Compressor (type determined by compressorType)
		this.prCreateCompressor(bus);

		// Limiter (safety ceiling at -0.3 dB)
		limiterSynth = mixerChannel.playfx(\limiter, [
			\inBus, bus,
			\outBus, bus,
			\ceiling, limiterCeiling,
			\dur, limiterDur
		]);

		"ClipMasterBus: Mastering chain added (EQ → Compressor → Limiter)".postln;
	}

	// Remove mastering chain
	removeMasteringChain {
		eqSynth.free; eqSynth = nil;
		compSynth.free; compSynth = nil;
		limiterSynth.free; limiterSynth = nil;

		"ClipMasterBus: Mastering chain removed".postln;
	}

	// Master level control
	setMasterLevel { |db|
		mixerChannel.level_(db.dbamp);
		"ClipMasterBus: Master level set to % dB".format(db).postln;
	}

	// EQ controls
	setEQ { |loFreq, loGain, midFreq, midGain, midQ, hiFreq, hiGain|
		if (eqSynth.isNil, {
			"ClipMasterBus: EQ not active - call addMasteringChain first".warn;
			^this;
		});

		if (loFreq.notNil, { eqSynth.set(\loFreq, loFreq) });
		if (loGain.notNil, { eqSynth.set(\loGain, loGain) });
		if (midFreq.notNil, { eqSynth.set(\midFreq, midFreq) });
		if (midGain.notNil, { eqSynth.set(\midGain, midGain) });
		if (midQ.notNil, { eqSynth.set(\midQ, midQ) });
		if (hiFreq.notNil, { eqSynth.set(\hiFreq, hiFreq) });
		if (hiGain.notNil, { eqSynth.set(\hiGain, hiGain) });

		"ClipMasterBus: EQ updated".postln;
	}

	// Compressor controls
	setCompressor { |thresh, ratio, attack, release, makeupGain|
		if (compSynth.isNil, {
			"ClipMasterBus: Compressor not active - call addMasteringChain first".warn;
			^this;
		});

		if (thresh.notNil, { compSynth.set(\thresh, thresh) });
		if (ratio.notNil, { compSynth.set(\ratio, ratio) });
		if (attack.notNil, { compSynth.set(\attack, attack) });
		if (release.notNil, { compSynth.set(\release, release) });
		if (makeupGain.notNil, { compSynth.set(\makeupGain, makeupGain) });

		"ClipMasterBus: Compressor updated".postln;
	}

	// Limiter controls
	setLimiter { |ceiling, dur|
		if (limiterSynth.isNil, {
			"ClipMasterBus: Limiter not active - call addMasteringChain first".warn;
			^this;
		});

		if (ceiling.notNil, {
			limiterCeiling = ceiling;
			limiterSynth.set(\ceiling, ceiling);
		});

		// Limiter.ar allocates its lookahead buffer when the synth starts, so
		// dur can't be .set on a running synth -- swap in a new limiter at the
		// same spot in the chain instead. Only when it actually changes, since
		// the swap causes a brief glitch (the limiter's 2*dur delay changes).
		if (dur.notNil and: { dur != limiterDur }, {
			limiterDur = dur;
			limiterSynth = Synth.replace(limiterSynth, \limiter, [
				\inBus, mixerChannel.inbus,
				\outBus, mixerChannel.inbus,
				\ceiling, limiterCeiling,
				\dur, limiterDur
			]);
		});

		"ClipMasterBus: Limiter updated (ceiling: % dB, lookahead: % s)"
			.format(limiterCeiling, limiterDur).postln;
	}

	// Boum controls
	setBoum { |thresh, ratio, attack, release, scHPF, drive, type, hicut, gateThresh, makeupGain, mix, bypass|
		if (compSynth.isNil, {
			"ClipMasterBus: Compressor not active - call addMasteringChain first".warn;
			^this;
		});

		if (compressorType != \boum, {
			"ClipMasterBus: Current compressor is %, not \\boum - call setCompressorType(\\boum) first".format(compressorType).warn;
			^this;
		});

		if (thresh.notNil, { compSynth.set(\thresh, thresh) });
		if (ratio.notNil, { compSynth.set(\ratio, ratio) });
		if (attack.notNil, { compSynth.set(\attack, attack) });
		if (release.notNil, { compSynth.set(\release, release) });
		if (scHPF.notNil, { compSynth.set(\scHPF, scHPF) });
		if (drive.notNil, { compSynth.set(\drive, drive) });
		if (type.notNil, { compSynth.set(\type, type) });
		if (hicut.notNil, { compSynth.set(\hicut, hicut) });
		if (gateThresh.notNil, { compSynth.set(\gateThresh, gateThresh) });
		if (makeupGain.notNil, { compSynth.set(\makeupGain, makeupGain) });
		if (mix.notNil, { compSynth.set(\mix, mix) });
		if (bypass.notNil, { compSynth.set(\bypass, bypass) });

		"ClipMasterBus: Boum updated".postln;
	}

	// Set compressor type (\glueComp or \boum)
	// Swaps the compressor synth while maintaining EQ -> compressor -> limiter order
	setCompressorType { |type|
		var bus = mixerChannel.inbus;

		if (#[\glueComp, \boum].includes(type).not, {
			"ClipMasterBus: Invalid compressor type % - must be \\glueComp or \\boum".format(type).error;
			^this;
		});

		if (type == compressorType, {
			"ClipMasterBus: Compressor type already %".format(type).postln;
			^this;
		});

		compressorType = type;

		// If mastering chain is active, swap the compressor
		if (compSynth.notNil, {
			// Free old compressor
			compSynth.free;

			// Create new compressor
			this.prCreateCompressor(bus);

			// Ensure limiter is still at the end by recreating it
			// (playfx adds at tail, so we need to explicitly reorder)
			limiterSynth = Synth.replace(limiterSynth, \limiter, [
				\inBus, bus,
				\outBus, bus,
				\ceiling, limiterCeiling,
				\dur, limiterDur
			]);

			"ClipMasterBus: Compressor type changed to % (chain: EQ → % → Limiter)".format(type, type).postln;
		}, {
			"ClipMasterBus: Compressor type set to % (will be used when mastering chain is added)".format(type).postln;
		});
	}

	// Private: Create compressor synth based on compressorType
	prCreateCompressor { |bus|
		var synthDef, args;

		case
		{ compressorType == \glueComp } {
			synthDef = \glueComp;
			args = [
				\inBus, bus,
				\outBus, bus,
				\thresh, -12,
				\ratio, 3,
				\attack, 0.01,
				\release, 0.3,
				\makeupGain, 0
			];
		}
		{ compressorType == \boum } {
			synthDef = \masterBoum;
			args = [
				\inBus, bus,
				\outBus, bus,
				\thresh, -12,
				\ratio, 3,
				\attack, 0.01,
				\release, 0.3,
				\scHPF, 0,
				\drive, 0,
				\type, 0,
				\hicut, 20000,
				\gateThresh, -60,
				\makeupGain, 0,
				\mix, 1,
				\bypass, 0
			];
		};

		compSynth = mixerChannel.playfx(synthDef, args);
	}

	// Bypass mastering (mute/unmute master)
	bypass { |bool = true|
		mixerChannel.mute(bool);
		if (bool, {
			"ClipMasterBus: Bypassed (muted)".postln;
		}, {
			"ClipMasterBus: Un-bypassed (unmuted)".postln;
		});
	}

	// Convenience: get input bus for routing channels to master
	inbus {
		^mixerChannel.inbus;
	}

	// Cleanup
	free {
		this.removeMasteringChain;
		mixerChannel.free;
	}

}
