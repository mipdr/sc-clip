/*
 * ClipMIDIController
 *
 * Abstract base class for MIDI controllers with SC-Clip integration.
 * Provides generic MIDI CC -> SCClip action dispatching and convenience methods
 * for common controller features (faders, knobs, buttons).
 *
 * This class is hardware-agnostic and can be used directly for simple controllers
 * or extended for device-specific implementations (e.g., TR-8S, APC40, etc.).
 *
 * Device-specific CC numbers and channel assignments belong in concrete subclasses
 * or in the caller's setup code, not here.
 *
 * Common MIDI controller features supported:
 *   - Faders: Continuous CC control, typically for channel levels
 *   - Knobs: Continuous CC control, typically for effect parameters
 *   - Buttons: Note or CC control, for triggering actions
 *
 * Not all controllers implement all features - concrete subclasses should
 * override only the methods relevant to their hardware.
 */

ClipMIDIController {
	var <clip;         // SCClip instance
	var <midiChannel;  // 0-indexed MIDI channel to listen on; nil = any channel
	var <ccMap;        // IdentityDictionary: ccNum -> handler function
	var <responder;    // MIDIFunc for CC messages
	var <deviceName;   // Human-readable controller name
	var <connected;    // Connection state

	*new { |clip, midiChannel, deviceName = "MIDIController"|
		^super.newCopyArgs(
			clip,
			midiChannel,
			IdentityDictionary.new,
			nil,                        // responder
			deviceName,
			false                       // connected
		).init;
	}

	init {
		responder = MIDIFunc.cc({ |val, num, chan, src|
			if (midiChannel.isNil or: { chan == midiChannel }, {
				var handler = ccMap[num];
				if (handler.notNil, { handler.value(val, chan, src) });
			});
		});
		connected = true;
	}

	// Map a raw CC number to an arbitrary handler: func.(value0to127, chan, src)
	mapCC { |ccNum, func|
		ccMap[ccNum] = func;
	}

	unmapCC { |ccNum|
		ccMap.removeAt(ccNum);
	}

	// Convenience: a fader CC -> a channel's mixer level, scaled to a dB range
	mapChannelLevel { |ccNum, channelIndex, minDb = -60, maxDb = 6|
		this.mapCC(ccNum, { |val|
			clip.setChannelLevel(channelIndex, val.linlin(0, 127, minDb, maxDb));
		});
	}

	// Convenience: a knob CC -> a live parameter on a channel effect already
	// added via SCClip.addChannelEffect / ClipChannel.addEffect at that slot
	mapEffectParam { |ccNum, channelIndex, slot, param, lo = 0.0, hi = 1.0|
		this.mapCC(ccNum, { |val|
			var channel = clip.grid.getChannel(channelIndex);
			if (channel.notNil, {
				channel.setEffectParam(slot, param, val.linlin(0, 127, lo, hi));
			});
		});
	}

	// === Extended convenience methods for common controller features ===

	// Map a fader to channel level (alias for mapChannelLevel for clarity)
	mapFader { |ccNum, channelIndex, minDb = -60, maxDb = 6|
		this.mapChannelLevel(ccNum, channelIndex, minDb, maxDb);
	}

	// Map a knob to effect parameter (alias for mapEffectParam for clarity)
	mapKnob { |ccNum, channelIndex, slot, param, lo = 0.0, hi = 1.0|
		this.mapEffectParam(ccNum, channelIndex, slot, param, lo, hi);
	}

	// Map a CC to channel pan
	mapChannelPan { |ccNum, channelIndex|
		this.mapCC(ccNum, { |val|
			// MIDI CC 0-127 -> pan -1.0 (left) to 1.0 (right)
			clip.setChannelPan(channelIndex, val.linlin(0, 127, -1.0, 1.0));
		});
	}

	// Map a CC to master level
	mapMasterLevel { |ccNum, minDb = -60, maxDb = 6|
		this.mapCC(ccNum, { |val|
			clip.setMasterLevel(val.linlin(0, 127, minDb, maxDb));
		});
	}

	// Map a CC to tempo control
	mapTempo { |ccNum, minBPM = 60, maxBPM = 180|
		this.mapCC(ccNum, { |val|
			clip.setTempo(val.linlin(0, 127, minBPM, maxBPM));
		});
	}

	// === Disconnect and cleanup ===

	disconnect {
		if (connected.not, {
			^this;
		});

		this.free;
		connected = false;
		"ClipMIDIController[%]: Disconnected".format(deviceName).postln;
	}

	free {
		if (responder.notNil, {
			responder.free;
			responder = nil;
		});
		ccMap.clear;
	}
}
