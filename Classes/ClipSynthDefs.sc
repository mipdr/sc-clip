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
			this.addChannelEffects;
			this.addMetronome;
			this.addLevelMeter;
		};
	}

	*addInputMonitor {

		// Input monitor: routes hardware input to the mixer channel (for live
		// monitoring) AND to the channel's dedicated recordBus (the clean,
		// playback-free feed ClipSlot's recorder/overdub synths read from --
		// mixerIn also carries whatever's currently playing back, which
		// would double up in an overdub if the recorder read from there).
		SynthDef(\inputMonitor, { |hardwareIn=0, mixerIn, recordBus|
			var sig = SoundIn.ar(hardwareIn);
			Out.ar(mixerIn, sig);
			Out.ar(recordBus, sig);
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
		// attackTime parameter allows instant attack for drift resync (0.001) or
		// normal smooth start (0.01, default)
		SynthDef(\clipPlayer, { |outBus, bufnum, rate=1, loop=1, gate=1, amp=1, startPos=0, attackTime=0.01|
			var sig = PlayBuf.ar(
				numChannels: 1,
				bufnum: bufnum,
				rate: BufRateScale.kr(bufnum) * rate,
				startPos: startPos,  // frame to start at (nudge restarts mid-loop)
				loop: loop,
				doneAction: Done.freeSelf
			);

			// Envelope for smooth start/stop
			sig = sig * EnvGen.kr(
				Env.asr(attackTime: attackTime, sustainLevel: 1, releaseTime: 0.05),
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
			sig = BLowShelf.ar(sig, loFreq, 1, loGain);
			// Mid parametric
			sig = BPeakEQ.ar(sig, midFreq, midQ, midGain);
			// High shelf
			sig = BHiShelf.ar(sig, hiFreq, 1, hiGain);

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

		// Boum effect: bus compressor + distortion + hi-cut + gate
		// Inspired by OTO BOUM hardware unit
		SynthDef(\masterBoum, { |inBus, outBus,
			thresh= -12, ratio=3,
			attack=0.01, release=0.3,
			scHPF=0,  // Sidechain HPF: 0=20Hz, 1=75Hz, 2=250Hz
			drive=0, type=0,  // Distortion: drive in dB, type 0-3
			hicut=20000,  // Hi-cut filter frequency
			gateThresh= -60,  // Gate threshold (fully open at minimum)
			makeupGain=0,
			mix=1,  // Dry/wet mix
			bypass=0|

			var sig, dry, wet, sc, scFreq, env, gr, distorted, distGain;
			var lagTime = 0.05;

			sig = In.ar(inBus, 2);
			dry = sig;

			// Lag continuous parameters to avoid zipper noise
			thresh = Lag.kr(thresh, lagTime);
			ratio = Lag.kr(ratio, lagTime);
			attack = Lag.kr(attack, lagTime);
			release = Lag.kr(release, lagTime);
			drive = Lag.kr(drive, lagTime);
			hicut = Lag.kr(hicut, lagTime);
			gateThresh = Lag.kr(gateThresh, lagTime);
			makeupGain = Lag.kr(makeupGain, lagTime);
			mix = Lag.kr(mix, lagTime);

			// === GATE ===
			// Simple downward expander - fully open at minimum setting
			env = Amplitude.kr(sig.sum, attack, release);
			gr = (env.ampdb - gateThresh).max(0) / (0 - gateThresh).max(0.1);
			sig = sig * gr.lag(0.01);

			// === COMPRESSOR ===
			// Stereo-linked detector (mono sum for sidechain)
			sc = sig.sum * 0.5;  // Mono sum

			// Sidechain HPF: 20/75/250 Hz
			scFreq = Select.kr(scHPF, [20, 75, 250]);
			sc = HPF.ar(sc, scFreq);

			// Amplitude follower
			env = Amplitude.kr(sc, attack, release);

			// Gain reduction calculation (dB domain)
			// Soft knee (2 dB width)
			gr = (env.ampdb - thresh).max(0);
			gr = (gr * (1 - (1/ratio))).neg;
			gr = gr.dbamp;

			sig = sig * gr;

			// === DISTORTION ===
			// Type 0: boost (soft clip)
			// Type 1: tube (asymmetric tanh + DC blocking)
			// Type 2: fuzz (hard clip)
			// Type 3: square (extreme gain + clip)
			// NOTE: fuzz and square will alias at 48kHz; no oversampling for CPU budget

			distGain = drive.dbamp;

			distorted = Select.ar(type, [
				// Type 0: Boost (soft clip with tanh)
				(sig * distGain * 2).tanh * 0.5,

				// Type 1: Tube (asymmetric tanh + LeakDC for bias)
				LeakDC.ar(((sig * distGain * 3) + 0.1).tanh * 0.4),

				// Type 2: Fuzz (hard clip) - aliases at 48k
				(sig * distGain * 5).clip2(0.8) * 0.8,

				// Type 3: Square (extreme gain + clip) - aliases at 48k
				(sig * distGain * 20).clip2(0.7) * 0.7
			]);

			sig = distorted;

			// === HI-CUT FILTER ===
			sig = LPF.ar(sig, hicut);

			// === MAKEUP GAIN ===
			sig = sig * makeupGain.dbamp;

			// === DRY/WET MIX ===
			wet = sig;
			sig = (dry * (1 - mix)) + (wet * mix);

			// === BYPASS ===
			sig = Select.ar(bypass, [sig, dry]);

			ReplaceOut.ar(outBus, sig);
		}).add;

		// Brick-wall limiter for master bus
		SynthDef(\limiter, { |inBus, outBus, ceiling= -0.3, dur=0.01|
			var sig = In.ar(inBus, 2);

			sig = Limiter.ar(sig, ceiling.dbamp, dur);

			ReplaceOut.ar(outBus, sig);
		}).add;

	}

	*addChannelEffects {

		// Per-channel insert effects, played via ClipChannel.addEffect ->
		// MixerChannel.playfx into the channel's effectgroup, which sits
		// ahead of its fader synth on a mono (1-channel) inbus -- so these
		// read/write a single channel and ReplaceOut in place, same as the
		// master effects above but per-channel instead of on the master bus.
		// playfx auto-supplies i_out/out/outbus, all set to the channel's inbus
		// -- but i_out is NOT a separate control: SynthDef strips the "i_" rate
		// prefix, so a control literally named "i_out" registers as an ir-rate
		// control named "out", colliding with an "out" arg declared in the same
		// function (SynthDef.add throws "Function argument 'out' already
		// declared"). Since playfx sets i_out/out/outbus to the same bus index
		// anyway, these only need a single "out" control, used for both the
		// In.ar read and the ReplaceOut.ar write.

		// Simple built-in reverb (FreeVerb, ships with stock SC -- no
		// sc3-plugins needed). mix is the wet/dry blend, the parameter a
		// MIDI knob would typically control.
		SynthDef(\channelReverb, { |out, mix = 0.3, room = 0.5, damp = 0.5|
			var sig = In.ar(out, 1);
			ReplaceOut.ar(out, FreeVerb.ar(sig, mix, room, damp));
		}).add;

		// Delay/echo. CombN has no built-in dry/wet control, so it's blended
		// manually, same knob-friendly mix param as the other channel fx.
		SynthDef(\channelDelay, { |out, mix = 0.3, delayTime = 0.3, decayTime = 2|
			var sig = In.ar(out, 1);
			var wet = CombN.ar(sig, 1.0, delayTime, decayTime);
			ReplaceOut.ar(out, (sig * (1 - mix)) + (wet * mix));
		}).add;

		// Distortion via tanh waveshaping. drive 0-1 scales into saturation;
		// mix blends against the dry signal like the others.
		SynthDef(\channelDistortion, { |out, mix = 0.3, drive = 0.5|
			var sig = In.ar(out, 1);
			var driven = (sig * (1 + (drive * 20))).tanh;
			ReplaceOut.ar(out, (sig * (1 - mix)) + (driven * mix));
		}).add;

		// Boum effect (mono/channel version): compressor + distortion + hi-cut + gate
		SynthDef(\channelBoum, { |out,
			thresh= -12, ratio=3,
			attack=0.01, release=0.3,
			scHPF=0,  // Sidechain HPF: 0=20Hz, 1=75Hz, 2=250Hz
			drive=0, type=0,  // Distortion: drive in dB, type 0-3
			hicut=20000,  // Hi-cut filter frequency
			gateThresh= -60,  // Gate threshold
			makeupGain=0,
			mix=1|  // Dry/wet mix

			var sig, dry, wet, sc, scFreq, env, gr, distorted, distGain;
			var lagTime = 0.05;

			sig = In.ar(out, 1);
			dry = sig;

			// Lag continuous parameters
			thresh = Lag.kr(thresh, lagTime);
			ratio = Lag.kr(ratio, lagTime);
			attack = Lag.kr(attack, lagTime);
			release = Lag.kr(release, lagTime);
			drive = Lag.kr(drive, lagTime);
			hicut = Lag.kr(hicut, lagTime);
			gateThresh = Lag.kr(gateThresh, lagTime);
			makeupGain = Lag.kr(makeupGain, lagTime);
			mix = Lag.kr(mix, lagTime);

			// Gate
			env = Amplitude.kr(sig, attack, release);
			gr = (env.ampdb - gateThresh).max(0) / (0 - gateThresh).max(0.1);
			sig = sig * gr.lag(0.01);

			// Compressor with sidechain HPF
			scFreq = Select.kr(scHPF, [20, 75, 250]);
			sc = HPF.ar(sig, scFreq);
			env = Amplitude.kr(sc, attack, release);
			gr = (env.ampdb - thresh).max(0);
			gr = (gr * (1 - (1/ratio))).neg;
			gr = gr.dbamp;
			sig = sig * gr;

			// Distortion
			distGain = drive.dbamp;
			distorted = Select.ar(type, [
				(sig * distGain * 2).tanh * 0.5,
				LeakDC.ar(((sig * distGain * 3) + 0.1).tanh * 0.4),
				(sig * distGain * 5).clip2(0.8) * 0.8,
				(sig * distGain * 20).clip2(0.7) * 0.7
			]);
			sig = distorted;

			// Hi-cut filter
			sig = LPF.ar(sig, hicut);

			// Makeup gain
			sig = sig * makeupGain.dbamp;

			// Dry/wet mix
			wet = sig;
			sig = (dry * (1 - mix)) + (wet * mix);

			ReplaceOut.ar(out, sig);
		}).add;

	}

	// Level meter tap, played by ClipLevelMeter at the tail of a MixerChannel's
	// fader group: the fader synth ReplaceOut's its post-fader signal back onto
	// the mixer's inbus, so reading it there gives post-fader levels. Sends
	// '/clipLevelMeter' [nodeID, replyID, peakL, rmsL, peakR, rmsR].
	*addLevelMeter {
		SynthDef(\clipLevelMeter, { |bus, rate = 30|
			SendPeakRMS.kr(In.ar(bus, 2), rate, 3, '/clipLevelMeter');
		}).add;
	}

	*addMetronome {

		// Metronome click: downbeat (high pitch) vs regular beat (low pitch)
		SynthDef(\metronomeClick, { |out=0, isDownbeat=0, amp=0.3|
			var sig, env, freq;

			// Downbeat: 1200 Hz, Regular beat: 800 Hz
			freq = Select.kr(isDownbeat, [800, 1200]);

			// Short click envelope
			env = EnvGen.kr(Env.perc(0.001, 0.05), doneAction: Done.freeSelf);

			// Simple sine click
			sig = SinOsc.ar(freq) * env * amp;

			Out.ar(out, sig ! 2);  // Stereo output
		}).add;

	}

}
