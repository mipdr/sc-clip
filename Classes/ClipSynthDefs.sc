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
			this.registerDefaultEffects;
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
		// Main control: drive (amount of distortion)
		SynthDef(\channelDistortion, { |out, mix = 1.0, drive = 0.5|
			var sig = In.ar(out, 1);
			var dry = sig;
			var driven = (sig * (1 + (drive * 10))).tanh * 0.5;
			ReplaceOut.ar(out, (dry * (1 - mix)) + (driven * mix));
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

		// NEW EFFECTS (added for flexible effect selection)

		// Tape Delay: Variable-speed delay with modulation
		// Main control: mix (dry/wet balance)
		SynthDef(\channelTapeDelay, { |out, mix=0.3, delayTime=0.375, feedback=0.6, wobble=0.002|
			var sig = In.ar(out, 1);
			var dry = sig;
			var modFreq = LFNoise1.kr(0.2).range(0.001, wobble);
			var delayTimeMod = delayTime * (1 + SinOsc.kr(1.3, 0, modFreq));
			var delayed = LocalIn.ar(1);
			delayed = DelayC.ar(sig + (delayed * feedback), 2, delayTimeMod.clip(0.001, 1.999));
			delayed = LPF.ar(delayed, 8000);  // Tape-like filtering
			LocalOut.ar(delayed);
			ReplaceOut.ar(out, (dry * (1 - mix)) + (delayed * mix));
		}).add;

		// Hard Clip: Aggressive hard clipping distortion
		// Main control: drive (clipping threshold)
		SynthDef(\channelHardClip, { |out, mix=1.0, drive=0.5, bias=0|
			var sig = In.ar(out, 1);
			var dry = sig;
			var driven = (sig * (1 + (drive * 10)) + bias).clip2(0.8) * 0.8;
			ReplaceOut.ar(out, (dry * (1 - mix)) + (driven * mix));
		}).add;

		// Chorus: Modulated delay for thickening
		// Main control: depth (modulation intensity)
		SynthDef(\channelChorus, { |out, mix=0.5, rate=0.5, depth=0.01, voices=4|
			var sig = In.ar(out, 1);
			var dry = sig;
			var wet = Mix.fill(voices, { |i|
				var phase = (i / voices) * 2pi;
				var mod = SinOsc.kr(rate, phase).range(0.02, 0.02 + depth);
				DelayC.ar(sig, 0.1, mod);
			}) / voices;
			ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
		}).add;

		// Phaser: Allpass filter modulation
		// Main control: depth (sweep intensity)
		SynthDef(\channelPhaser, { |out, mix=0.5, rate=0.3, depth=0.5, stages=4|
			var sig = In.ar(out, 1);
			var dry = sig;
			var mod = SinOsc.kr(rate).range(0.0001, 0.01 * depth);
			var wet = sig;
			stages.do {
				wet = AllpassN.ar(wet, 0.02, mod);
			};
			ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
		}).add;

		// Flanger: Short modulated delay
		// Main control: depth (sweep range)
		SynthDef(\channelFlanger, { |out, mix=0.5, rate=0.2, depth=0.005, feedback=0.5|
			var sig = In.ar(out, 1);
			var dry = sig;
			var mod = SinOsc.kr(rate).range(0.001, 0.001 + depth);
			var delayed = LocalIn.ar(1);
			delayed = DelayC.ar(sig + (delayed * feedback), 0.02, mod);
			LocalOut.ar(delayed);
			ReplaceOut.ar(out, (dry * (1 - mix)) + (delayed * mix));
		}).add;

		// Low-Pass Filter: Resonant low-pass filter
		// Main control: freq (cutoff frequency)
		SynthDef(\channelLowPass, { |out, freq=1000, resonance=0.5, mix=1.0|
			var sig = In.ar(out, 1);
			var dry = sig;
			var wet = RLPF.ar(sig, freq.clip(20, 20000), 1 - (resonance * 0.9));
			ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
		}).add;

		// High-Pass Filter: Resonant high-pass filter
		// Main control: freq (cutoff frequency)
		SynthDef(\channelHighPass, { |out, freq=200, resonance=0.5, mix=1.0|
			var sig = In.ar(out, 1);
			var dry = sig;
			var wet = RHPF.ar(sig, freq.clip(20, 20000), 1 - (resonance * 0.9));
			ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
		}).add;

		// Band-Pass Filter: Resonant band-pass filter
		// Main control: freq (center frequency)
		SynthDef(\channelBandPass, { |out, freq=1000, resonance=0.5, mix=1.0|
			var sig = In.ar(out, 1);
			var dry = sig;
			var wet = BPF.ar(sig, freq.clip(20, 20000), 1 - (resonance * 0.9));
			ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
		}).add;

		// Simple Compressor: Basic Compander
		// Main control: thresh (compression threshold)
		SynthDef(\channelCompressor, { |out, thresh= -12, ratio=3, attack=0.01, release=0.3, mix=1.0|
			var sig = In.ar(out, 1);
			var dry = sig;
			var wet = Compander.ar(sig, sig,
				thresh.dbamp, 1, 1/ratio, attack, release);
			ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
		}).add;

		// Optionally check for sc3-plugins and add GVerb if available
		if (GVerb.respondsTo(\ar), {
			SynthDef(\channelGVerb, { |out, mix=0.3, roomsize=10, revtime=3,
				damping=0.5, spread=15|
				var sig = In.ar(out, 1);
				var dry = sig;
				var wet = GVerb.ar(sig, roomsize, revtime, damping,
					spread: spread, drylevel: 0, mul: 0.3);
				wet = wet[0] + wet[1] * 0.5;  // Mix stereo to mono
				ReplaceOut.ar(out, (dry * (1 - mix)) + (wet * mix));
			}).add;
		});

	}

	*registerDefaultEffects {
		// Register all default channel effects with metadata
		// Called during StartUp after SynthDefs are added

		// Bypass (null effect)
		ClipEffectRegistry.register(\bypass, (
			displayName: "Bypass",
			category: \utility,
			mainControl: nil,
			parameters: Dictionary.new
		));

		// Reverb: FreeVerb
		ClipEffectRegistry.register(\channelReverb, (
			displayName: "Reverb (FreeVerb)",
			category: \reverb,
			mainControl: \mix,
			parameters: (
				mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
				room: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				damp: ControlSpec(0, 1, \lin, 0.01, 0.5, "")
			)
		));

		// Reverb: GVerb (if available)
		if (GVerb.respondsTo(\ar), {
			ClipEffectRegistry.register(\channelGVerb, (
				displayName: "Reverb (GVerb)",
				category: \reverb,
				mainControl: \mix,
				parameters: (
					mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
					roomsize: ControlSpec(1, 300, \exp, 1, 10, "m"),
					revtime: ControlSpec(0.1, 20, \exp, 0.1, 3, "s"),
					damping: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
					spread: ControlSpec(0, 50, \lin, 1, 15, "")
				)
			));
		});

		// Delay: Simple Delay
		ClipEffectRegistry.register(\channelDelay, (
			displayName: "Delay (Comb)",
			category: \delay,
			mainControl: \mix,
			parameters: (
				mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
				delayTime: ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"),
				decayTime: ControlSpec(0.1, 10, \exp, 0.1, 2, "s")
			)
		));

		// Delay: Tape Delay
		ClipEffectRegistry.register(\channelTapeDelay, (
			displayName: "Delay (Tape)",
			category: \delay,
			mainControl: \mix,
			parameters: (
				mix: ControlSpec(0, 1, \lin, 0.01, 0.3, ""),
				delayTime: ControlSpec(0.01, 2, \exp, 0.01, 0.375, "s"),
				feedback: ControlSpec(0, 0.95, \lin, 0.01, 0.6, ""),
				wobble: ControlSpec(0, 0.01, \lin, 0.0001, 0.002, "")
			)
		));

		// Distortion: Soft
		ClipEffectRegistry.register(\channelDistortion, (
			displayName: "Distortion (Soft)",
			category: \distortion,
			mainControl: \drive,
			parameters: (
				mix: ControlSpec(0, 1, \lin, 0.01, 1.0, ""),
				drive: ControlSpec(0, 1, \lin, 0.01, 0.5, "")
			)
		));

		// Distortion: Hard Clip
		ClipEffectRegistry.register(\channelHardClip, (
			displayName: "Distortion (Hard)",
			category: \distortion,
			mainControl: \drive,
			parameters: (
				mix: ControlSpec(0, 1, \lin, 0.01, 1.0, ""),
				drive: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				bias: ControlSpec(-0.5, 0.5, \lin, 0.01, 0, "")
			)
		));

		// Modulation: Chorus
		ClipEffectRegistry.register(\channelChorus, (
			displayName: "Chorus",
			category: \modulation,
			mainControl: \depth,
			parameters: (
				mix: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				rate: ControlSpec(0.1, 5, \exp, 0.01, 0.5, "Hz"),
				depth: ControlSpec(0.001, 0.05, \exp, 0.001, 0.01, ""),
				voices: ControlSpec(2, 8, \lin, 1, 4, "")
			)
		));

		// Modulation: Phaser
		ClipEffectRegistry.register(\channelPhaser, (
			displayName: "Phaser",
			category: \modulation,
			mainControl: \depth,
			parameters: (
				mix: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				rate: ControlSpec(0.1, 5, \exp, 0.01, 0.3, "Hz"),
				depth: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				stages: ControlSpec(2, 12, \lin, 1, 4, "")
			)
		));

		// Modulation: Flanger
		ClipEffectRegistry.register(\channelFlanger, (
			displayName: "Flanger",
			category: \modulation,
			mainControl: \depth,
			parameters: (
				mix: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				rate: ControlSpec(0.1, 5, \exp, 0.01, 0.2, "Hz"),
				depth: ControlSpec(0.001, 0.01, \exp, 0.0001, 0.005, ""),
				feedback: ControlSpec(0, 0.95, \lin, 0.01, 0.5, "")
			)
		));

		// Filter: Low-Pass
		ClipEffectRegistry.register(\channelLowPass, (
			displayName: "Filter (Low-Pass)",
			category: \filter,
			mainControl: \freq,
			parameters: (
				freq: ControlSpec(20, 20000, \exp, 1, 1000, "Hz"),
				resonance: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				mix: ControlSpec(0, 1, \lin, 0.01, 1.0, "")
			)
		));

		// Filter: High-Pass
		ClipEffectRegistry.register(\channelHighPass, (
			displayName: "Filter (High-Pass)",
			category: \filter,
			mainControl: \freq,
			parameters: (
				freq: ControlSpec(20, 20000, \exp, 1, 200, "Hz"),
				resonance: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				mix: ControlSpec(0, 1, \lin, 0.01, 1.0, "")
			)
		));

		// Filter: Band-Pass
		ClipEffectRegistry.register(\channelBandPass, (
			displayName: "Filter (Band-Pass)",
			category: \filter,
			mainControl: \freq,
			parameters: (
				freq: ControlSpec(20, 20000, \exp, 1, 1000, "Hz"),
				resonance: ControlSpec(0, 1, \lin, 0.01, 0.5, ""),
				mix: ControlSpec(0, 1, \lin, 0.01, 1.0, "")
			)
		));

		// Dynamics: Boum
		ClipEffectRegistry.register(\channelBoum, (
			displayName: "Compressor (Boum)",
			category: \dynamics,
			mainControl: \thresh,
			parameters: (
				thresh: ControlSpec(-60, 0, \lin, 0.1, -12, "dB"),
				ratio: ControlSpec(1, 20, \lin, 0.1, 3, ":1"),
				attack: ControlSpec(0.001, 0.1, \exp, 0.001, 0.01, "s"),
				release: ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"),
				drive: ControlSpec(0, 24, \lin, 0.1, 0, "dB"),
				type: ControlSpec(0, 3, \lin, 1, 0, ""),
				hicut: ControlSpec(200, 20000, \exp, 1, 20000, "Hz"),
				gateThresh: ControlSpec(-80, -20, \lin, 0.1, -60, "dB"),
				makeupGain: ControlSpec(0, 24, \lin, 0.1, 0, "dB"),
				mix: ControlSpec(0, 1, \lin, 0.01, 1, "")
			)
		));

		// Dynamics: Simple Compressor
		ClipEffectRegistry.register(\channelCompressor, (
			displayName: "Compressor (Simple)",
			category: \dynamics,
			mainControl: \thresh,
			parameters: (
				thresh: ControlSpec(-60, 0, \lin, 0.1, -12, "dB"),
				ratio: ControlSpec(1, 20, \lin, 0.1, 3, ":1"),
				attack: ControlSpec(0.001, 0.1, \exp, 0.001, 0.01, "s"),
				release: ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"),
				mix: ControlSpec(0, 1, \lin, 0.01, 1.0, "")
			)
		));

		"ClipEffectRegistry: % effects registered".format(ClipEffectRegistry.all.size).postln;
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
