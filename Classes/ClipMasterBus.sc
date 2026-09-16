/*
 * ClipMasterBus
 *
 * Master bus with mastering chain (EQ, compression, limiting).
 * Receives audio from all ClipChannel instances.
 */

ClipMasterBus {
	var <mixerChannel;  // MixerChannel instance for master
	var <masterGroup;   // Group for master effects
	var <server;
	var <eqSynth;
	var <compSynth;
	var <limiterSynth;

	*new { |server|
		^super.newCopyArgs(
			nil,                      // mixerChannel
			nil,                      // masterGroup
			server ? Server.default,  // server
			nil,                      // eqSynth
			nil,                      // compSynth
			nil                       // limiterSynth
		).init;
	}

	init {
		// Create master group
		server.bind {
			masterGroup = Group.new(server);
		};

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
	addMasteringChain {
		var bus = mixerChannel.inbus;

		server.bind {
			// EQ (neutral by default)
			eqSynth = Synth(\masterEQ, [
				\inBus, bus,
				\outBus, bus,
				\loFreq, 80,
				\loGain, 0,
				\midFreq, 1000,
				\midGain, 0,
				\midQ, 1,
				\hiFreq, 8000,
				\hiGain, 0
			], masterGroup, \addToTail);

			// Glue compressor (gentle by default)
			compSynth = Synth(\glueComp, [
				\inBus, bus,
				\outBus, bus,
				\thresh, -12,
				\ratio, 3,
				\attack, 0.01,
				\release, 0.3,
				\makeupGain, 0
			], masterGroup, \addAfter, eqSynth);

			// Limiter (safety ceiling at -0.3 dB)
			limiterSynth = Synth(\limiter, [
				\inBus, bus,
				\outBus, bus,
				\ceiling, -0.3,
				\dur, 0.01
			], masterGroup, \addAfter, compSynth);
		};

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

		if (ceiling.notNil, { limiterSynth.set(\ceiling, ceiling) });
		if (dur.notNil, { limiterSynth.set(\dur, dur) });

		"ClipMasterBus: Limiter updated (ceiling: % dB)".format(ceiling).postln;
	}

	// Bypass mastering (mute/unmute master)
	bypass { |bool = true|
		if (bool, {
			mixerChannel.mute;
			"ClipMasterBus: Bypassed (muted)".postln;
		}, {
			mixerChannel.unMute;
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
		masterGroup.free;
	}

}
