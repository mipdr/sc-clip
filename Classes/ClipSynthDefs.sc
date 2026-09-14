/*
 * ClipSynthDefs
 *
 * All SynthDef definitions for the sc-clip system.
 * These are registered globally when the class library compiles.
 */

ClipSynthDefs {

	*initClass {
		StartUp.add {
			this.addInputMonitor;
			this.addRecorderSynths;
			this.addPlayerSynths;
			this.addMasterEffects;
		};
	}

	*addInputMonitor {

		// Input monitor: routes hardware input to mixer channel input
		SynthDef(\inputMonitor, { |hardwareIn=0, mixerIn|
			var sig = SoundIn.ar(hardwareIn);
			Out.ar(mixerIn, sig);
		}).add;

	}

	*addRecorderSynths {

		// Clip recorder: captures audio to buffer with overdub support
		// mode: 0 = record (fresh), 1 = replace, 2 = overdub
		SynthDef(\clipRecorder, { |inBus, bufnum, loop=1, mode=0|
			var sig = In.ar(inBus, 1);
			var recLevel = Select.kr(mode, [1, 1, 0.7]); // record, replace, overdub
			var preLevel = Select.kr(mode, [0, 0, 0.3]); // overdub preserves previous

			RecordBuf.ar(
				inputArray: sig,
				bufnum: bufnum,
				recLevel: recLevel,
				preLevel: preLevel,
				loop: loop,
				trigger: 1
			);
		}).add;

	}

	*addPlayerSynths {

		// Clip player: loops audio from buffer
		SynthDef(\clipPlayer, { |outBus, bufnum, rate=1, loop=1, gate=1, amp=1|
			var sig = PlayBuf.ar(
				numChannels: 1,
				bufnum: bufnum,
				rate: BufRateScale.kr(bufnum) * rate,
				loop: loop,
				doneAction: Done.freeSelf
			);

			// Envelope for smooth start/stop
			sig = sig * EnvGen.kr(
				Env.asr(attackTime: 0.01, sustainLevel: 1, releaseTime: 0.05),
				gate: gate,
				doneAction: Done.freeSelf
			);

			sig = sig * amp;
			Out.ar(outBus, sig);
		}).add;

	}

	*addMasterEffects {

		// Parametric EQ for master bus
		SynthDef(\masterEQ, { |inBus, outBus,
			loFreq=80, loGain=0,
			midFreq=1000, midGain=0, midQ=1,
			hiFreq=8000, hiGain=0|

			var sig = In.ar(inBus, 2);

			// Low shelf
			sig = BPeakEQ.ar(sig, loFreq, 1, loGain);
			// Mid parametric
			sig = BPeakEQ.ar(sig, midFreq, midQ, midGain);
			// High shelf
			sig = BPeakEQ.ar(sig, hiFreq, 1, hiGain);

			ReplaceOut.ar(outBus, sig);
		}).add;

		// Glue compressor for master bus
		SynthDef(\glueComp, { |inBus, outBus,
			thresh= -12, ratio=3,
			attack=0.01, release=0.3,
			makeupGain=0|

			var sig = In.ar(inBus, 2);

			sig = Compander.ar(
				in: sig,
				control: sig,
				thresh: thresh.dbamp,
				slopeBelow: 1,
				slopeAbove: 1/ratio,
				clampTime: attack,
				relaxTime: release
			);

			sig = sig * makeupGain.dbamp;
			ReplaceOut.ar(outBus, sig);
		}).add;

		// Brick-wall limiter for master bus
		SynthDef(\limiter, { |inBus, outBus, ceiling= -0.3, dur=0.01|
			var sig = In.ar(inBus, 2);

			sig = Limiter.ar(sig, ceiling.dbamp, dur);

			ReplaceOut.ar(outBus, sig);
		}).add;

	}

}
