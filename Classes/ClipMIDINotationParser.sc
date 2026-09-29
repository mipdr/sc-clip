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
 *   "~:1"                         // Rest (only the duration matters)
 *
 * Note names: c4, cs4/df4 (sharps/flats), c#4/db4 (alternative), case-insensitive
 * MIDI note 60 = c4 (middle C)
 *
 * After parse, totalBeats holds the summed duration of every token (rests
 * included) -- the natural loop length of the clip.
 */

ClipMIDINotationParser {
	var <noteNameMap;
	var <totalBeats;  // Summed duration of the last parsed string

	*new {
		^super.new.init;
	}

	init {
		// Map note names to semitones relative to the octave's C (b# and cb
		// cross the octave boundary, hence 12 and -1)
		noteNameMap = IdentityDictionary[
			'c' -> 0, 'b#' -> 12, 'bs' -> 12,
			'cs' -> 1, 'c#' -> 1, 'df' -> 1, 'db' -> 1,
			'd' -> 2,
			'ds' -> 3, 'd#' -> 3, 'ef' -> 3, 'eb' -> 3,
			'e' -> 4, 'fb' -> 4, 'ff' -> 4,
			'f' -> 5, 'e#' -> 5, 'es' -> 5,
			'fs' -> 6, 'f#' -> 6, 'gf' -> 6, 'gb' -> 6,
			'g' -> 7,
			'gs' -> 8, 'g#' -> 8, 'af' -> 8, 'ab' -> 8,
			'a' -> 9,
			'as' -> 10, 'a#' -> 10, 'bf' -> 10, 'bb' -> 10,
			'b' -> 11, 'cf' -> -1, 'cb' -> -1
		];
		totalBeats = 0;
	}

	// Main parse method: converts notation string to an array of MIDI events
	// sorted by time (note-offs before note-ons at the same time, so a
	// repeated note isn't cut short by its predecessor's note-off).
	// Returns nil if any token is invalid.
	parse { |notationString|
		var events = List.new;
		var currentTime = 0;
		var failed = false;

		totalBeats = 0;

		this.tokenize(notationString).do { |token|
			var parsed;

			if (failed.not, {
				parsed = this.parseToken(token, currentTime);
				if (parsed.isNil, {
					failed = true;
				}, {
					events.addAll(parsed);
					currentTime = currentTime + this.parseDuration(token);
				});
			});
		};

		if (failed, { ^nil });

		totalBeats = currentTime;
		^events.asArray.sort({ |a, b|
			(a.time < b.time) or: { a.time == b.time and: { a.type == \noteOff } }
		});
	}

	// Split on whitespace, but not inside [ ] (chords contain spaces)
	tokenize { |notationString|
		var tokens = List.new;
		var current = "";
		var depth = 0;

		notationString.do { |char|
			case
			{ char == $[ } { depth = depth + 1; current = current ++ char }
			{ char == $] } { depth = depth - 1; current = current ++ char }
			{ char.isSpace and: { depth <= 0 } } {
				if (current.size > 0, { tokens.add(current) });
				current = "";
			}
			{ current = current ++ char };
		};
		if (current.size > 0, { tokens.add(current) });

		^tokens.asArray;
	}

	// Split a token into [notesPart, paramStrings]. For a chord, the notes
	// part runs to the closing bracket, so its spaces never reach the params.
	splitToken { |token|
		var closeIndex, rest;

		if (token[0] == $[, {
			closeIndex = token.indexOf($]);
			if (closeIndex.isNil, { ^nil });
			rest = token.copyRange(closeIndex + 1, token.size - 1);
			^[token.copyRange(0, closeIndex), rest.split($:).reject(_.isEmpty)];
		});

		rest = token.split($:);
		^[rest[0], rest.copyRange(1, rest.size - 1)];
	}

	// Parse a single token (note/chord/rest with params) into note on/off
	// events. Returns an empty list for a rest, nil if the token is invalid.
	parseToken { |token, currentTime|
		var split, notesPart, params, duration, velocity, legato, notes;
		var events = List.new;

		split = this.splitToken(token);
		if (split.isNil, {
			"ClipMIDINotationParser: Unclosed chord: %".format(token).error;
			^nil;
		});
		#notesPart, params = split;

		duration = if (params.size > 0, { params[0].asFloat }, { 1.0 });
		velocity = if (params.size > 1, { params[1].asInteger }, { 100 }).clip(1, 127);
		legato = if (params.size > 2, { params[2].asFloat }, { 0.8 }).clip(0.01, 2);

		if (duration <= 0, {
			"ClipMIDINotationParser: Duration must be > 0: %".format(token).error;
			^nil;
		});

		if (notesPart == "~", { ^events });  // Rest

		notes = if (notesPart[0] == $[, {
			this.parseChord(notesPart)
		}, {
			[this.parseNote(notesPart)]
		});

		if (notes.isNil or: { notes.includes(nil) }, { ^nil });

		notes.do { |midiNote|
			events.add((time: currentTime, type: \noteOn, note: midiNote, vel: velocity));
			events.add((time: currentTime + (duration * legato), type: \noteOff, note: midiNote, vel: 0));
		};

		^events;
	}

	// Parse duration from token string
	parseDuration { |token|
		var split = this.splitToken(token);
		^if (split.notNil and: { split[1].size > 0 }, { split[1][0].asFloat }, { 1.0 });
	}

	// Parse a chord: "[c4 e4 g4]" -> [60, 64, 67]
	parseChord { |chordString|
		var inner = chordString.copyRange(1, chordString.size - 2);  // Remove [ ]
		var noteStrings = inner.split($ ).reject(_.isEmpty);
		if (noteStrings.isEmpty, {
			"ClipMIDINotationParser: Empty chord".error;
			^nil;
		});
		^noteStrings.collect({ |noteStr| this.parseNote(noteStr) });
	}

	// Parse a single note: "c4" -> 60, "cs5" -> 73, "a-1" -> 9
	parseNote { |noteString|
		var noteName, octave, semitone, octaveIndex;

		// The octave starts at the first digit or minus sign after the name
		if (noteString.size >= 2, {
			octaveIndex = (1..noteString.size - 1).detect({ |i|
				noteString[i].isDecDigit or: { noteString[i] == $- }
			});
		});

		if (octaveIndex.isNil, {
			"ClipMIDINotationParser: Invalid note format (no octave): %".format(noteString).error;
			^nil;
		});

		noteName = noteString.copyRange(0, octaveIndex - 1).toLower.asSymbol;
		octave = noteString.copyRange(octaveIndex, noteString.size - 1).asInteger;
		semitone = noteNameMap[noteName];

		if (semitone.isNil, {
			"ClipMIDINotationParser: Unknown note name: %".format(noteName).error;
			^nil;
		});

		// c4 = 60
		^((octave + 1) * 12 + semitone).clip(0, 127);
	}

	// Utility: convert MIDI note number back to note name (for display)
	midiNoteToName { |midiNote|
		var noteNames = ["c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"];
		var octave = (midiNote / 12).asInteger - 1;
		var semitone = midiNote % 12;
		^(noteNames[semitone] ++ octave);
	}
}
