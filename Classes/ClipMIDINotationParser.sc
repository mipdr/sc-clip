/*
 * ClipMIDINotationParser
 *
 * Parses human-readable MIDI notation strings into arrays of MIDI events.
 *
 * Format: "note:duration:velocity:legato note:duration:velocity:legato ..."
 *
 * Examples:
 *   "c4:1:100:0.8 d4:1:80:0.8"  // Simple melody
 *   "[c4 e4 g4]:2:100:0.9"       // Chord (polyphony)
 *   "c4 d4 e4"                    // Shorthand (defaults: dur=1, vel=100, legato=0.8)
 *   "c4:2 d4:1"                   // Specify only duration
 *   "~:1:0:0"                     // Rest
 *
 * Note names: c4, cs4/df4 (sharps/flats), c#4/db4 (alternative)
 * MIDI note 60 = c4 (middle C)
 */

ClipMIDINotationParser {
	var <noteNameMap;

	*new {
		^super.new.init;
	}

	init {
		// Map note names to semitones within octave (c=0, c#=1, etc.)
		noteNameMap = IdentityDictionary[
			'c' -> 0, 'b#' -> 0,
			'cs' -> 1, 'c#' -> 1, 'df' -> 1, 'db' -> 1,
			'd' -> 2,
			'ds' -> 3, 'd#' -> 3, 'ef' -> 3, 'eb' -> 3,
			'e' -> 4, 'fb' -> 4,
			'f' -> 5, 'e#' -> 5,
			'fs' -> 6, 'f#' -> 6, 'gf' -> 6, 'gb' -> 6,
			'g' -> 7,
			'gs' -> 8, 'g#' -> 8, 'af' -> 8, 'ab' -> 8,
			'a' -> 9,
			'as' -> 10, 'a#' -> 10, 'bf' -> 10, 'bb' -> 10,
			'b' -> 11, 'cf' -> 11
		];
	}

	// Main parse method: converts notation string to array of MIDI events
	parse { |notationString|
		var tokens = notationString.split($ );  // Split by space
		var events = List.new;
		var currentTime = 0;

		tokens.do { |token|
			var noteEvents;

			if (token.size > 0, {
				// Parse token (may be single note or chord)
				noteEvents = this.parseToken(token, currentTime);

				if (noteEvents.notNil, {
					// Add all events from this token
					noteEvents.do { |ev| events.add(ev) };

					// Advance time by duration of this token
					var duration = this.parseDuration(token);
					currentTime = currentTime + duration;
				});
			});
		};

		^events.asArray;
	}

	// Parse a single token (note/chord with params)
	parseToken { |token, currentTime|
		var isChord = token[0] == $[;
		var events = List.new;
		var parts, notesPart, duration, velocity, legato;
		var notes;

		// Split token by colon to extract parameters
		parts = token.split($:);

		// Extract notes part (first element)
		notesPart = parts[0];

		// Parse parameters (duration, velocity, legato)
		duration = if (parts.size > 1, { parts[1].asFloat }, { 1.0 });
		velocity = if (parts.size > 2, { parts[2].asInteger }, { 100 });
		legato = if (parts.size > 3, { parts[3].asFloat }, { 0.8 });

		// Validate parameters
		velocity = velocity.clip(0, 127);
		legato = legato.clip(0, 2);

		// Parse notes (single note or chord)
		if (isChord, {
			notes = this.parseChord(notesPart);
		}, {
			notes = [this.parseNote(notesPart)];
		});

		// Skip rests
		notes = notes.select(_.notNil);

		if (notes.size == 0, {
			// Rest - still need to advance time but no events
			^nil;
		});

		// Create note-on and note-off events for each note
		notes.do { |midiNote|
			var noteOnTime = currentTime;
			var noteOffTime = currentTime + (duration * legato);

			// Note On
			events.add((
				time: noteOnTime,
				type: \noteOn,
				note: midiNote,
				vel: velocity
			));

			// Note Off
			events.add((
				time: noteOffTime,
				type: \noteOff,
				note: midiNote,
				vel: 0
			));
		};

		^events;
	}

	// Parse duration from token string
	parseDuration { |token|
		var parts = token.split($:);
		^if (parts.size > 1, { parts[1].asFloat }, { 1.0 });
	}

	// Parse a chord: "[c4 e4 g4]" -> [60, 64, 67]
	parseChord { |chordString|
		var inner = chordString.copyRange(1, chordString.size - 2);  // Remove [ ]
		var noteStrings = inner.split($ );
		^noteStrings.collect({ |noteStr| this.parseNote(noteStr) });
	}

	// Parse a single note: "c4" -> 60, "cs5" -> 73
	parseNote { |noteString|
		var noteName, octave, semitone, midiNote;
		var match;

		// Check for rest
		if (noteString == "~", { ^nil });

		// Extract note name and octave using regex-like parsing
		// Format: <notename><octave> e.g. "c4", "cs5", "df3"

		// Find where the octave number starts (first digit)
		var digitIndex = noteString.detectIndex({ |char| char.isDecDigit });

		if (digitIndex.isNil, {
			"ClipMIDINotationParser: Invalid note format (no octave): %".format(noteString).error;
			^nil;
		});

		noteName = noteString.copyRange(0, digitIndex - 1).asSymbol;
		octave = noteString.copyRange(digitIndex, noteString.size - 1).asInteger;

		// Look up semitone from note name
		semitone = noteNameMap[noteName];

		if (semitone.isNil, {
			"ClipMIDINotationParser: Unknown note name: %".format(noteName).error;
			^nil;
		});

		// Calculate MIDI note number (c4 = 60)
		midiNote = (octave + 1) * 12 + semitone;

		^midiNote.clip(0, 127);
	}

	// Utility: convert MIDI note number back to note name (for display)
	midiNoteToName { |midiNote|
		var noteNames = ["c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"];
		var octave = (midiNote / 12).asInteger - 1;
		var semitone = midiNote % 12;
		^(noteNames[semitone] ++ octave);
	}
}
