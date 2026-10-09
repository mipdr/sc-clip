/*
 * ClipEffectRegistry
 *
 * Registry for available channel effects with metadata.
 * Each effect includes:
 * - displayName: Human-readable name for GUI
 * - category: Effect category (reverb, delay, distortion, etc.)
 * - mainControl: Parameter name for single MIDI knob control
 * - parameters: Dictionary of ControlSpecs for all parameters
 */

ClipEffectRegistry {
	classvar <effects;  // Dictionary: synthDefName -> metadata

	*initClass {
		effects = Dictionary.new;
	}

	*register { |synthDefName, metadata|
		// metadata: (
		//     displayName: "Reverb (FreeVerb)",
		//     category: \reverb,
		//     mainControl: \mix,
		//     parameters: Dictionary of ControlSpecs
		// )
		effects[synthDefName] = metadata;

		"ClipEffectRegistry: Registered %".format(metadata[\displayName]).postln;
	}

	*get { |synthDefName|
		^effects[synthDefName];
	}

	*all {
		^effects;
	}

	*allNames {
		^effects.keys.asArray.sort;
	}

	*getMainControl { |synthDefName|
		var meta = effects[synthDefName];
		^meta !? { meta[\mainControl] };
	}

	*getMainControlSpec { |synthDefName|
		var meta = effects[synthDefName];
		var mainParam = meta !? { meta[\mainControl] };
		^if (mainParam.notNil, {
			meta[\parameters][mainParam];
		}, {
			nil
		});
	}

	*categorized {
		// Return effects organized by category
		var categorized = Dictionary.new;
		effects.keysValuesDo { |name, meta|
			var cat = meta[\category] ? \other;
			if (categorized[cat].isNil, {
				categorized[cat] = List.new;
			});
			categorized[cat].add(name);
		};
		^categorized;
	}
}
