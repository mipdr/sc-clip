/*
 * ClipMIDIController
 *
 * Generic MIDI CC -> SCClip action dispatcher. Hardware-agnostic: it knows
 * nothing about any specific controller, just how to bind a (CC number,
 * MIDI channel) pair to a handler function, plus a couple of convenience
 * mappings for the two most common cases (a fader controlling channel level,
 * a knob controlling a running channel effect's parameter). Device-specific
 * CC numbers and channel assignments belong in the caller's setup, not here.
 */

ClipMIDIController {
	var <clip;         // SCClip instance
	var <midiChannel;  // 0-indexed MIDI channel to listen on; nil = any channel
	var <ccMap;        // IdentityDictionary: ccNum -> handler function
	var <responder;    // MIDIFunc

	*new { |clip, midiChannel|
		^super.newCopyArgs(clip, midiChannel, IdentityDictionary.new, nil).init;
	}

	init {
		responder = MIDIFunc.cc({ |val, num, chan, src|
			if (midiChannel.isNil or: { chan == midiChannel }, {
				var handler = ccMap[num];
				if (handler.notNil, { handler.value(val, chan, src) });
			});
		});
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

	free {
		responder.free;
		ccMap.clear;
	}
}
