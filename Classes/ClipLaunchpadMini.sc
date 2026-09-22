/*
 * ClipLaunchpadMini
 *
 * Concrete implementation for Novation Launchpad Mini (original)
 * Handles Launchpad-specific MIDI protocol, button mapping, and LED colors.
 *
 * Grid Layout (XY mode):
 *   - 8x8 grid: Columns 0-7 = channels, Rows 0-3 = clip indexes
 *   - Rows 4-7 reserved for future use
 *   - MIDI note = (row * 16) + col
 *
 * LED Colors (via velocity):
 *   - 12 = off
 *   - 13 = red_low, 14 = red_mid, 15 = red_high
 *   - 29 = amber_low, 63 = amber_high
 *   - 62 = yellow
 *   - 60 = green
 */

ClipLaunchpadMini : ClipGridController {

	*new { |grid, transport, numRows = 4, numCols = 8|
		^super.new(grid, transport, numRows, numCols);
	}

	// Connect to Launchpad Mini
	connect { |deviceName = "Launchpad Mini"|
		var midiInPort, midiOutPort;

		// Initialize MIDI
		MIDIClient.init;

		// Find MIDI device
		midiInPort = MIDIIn.findPort(deviceName, deviceName);
		midiOutPort = MIDIOut.findPort(deviceName, deviceName);

		if (midiInPort.isNil or: { midiOutPort.isNil }, {
			"ClipLaunchpadMini: Could not find device '%'".format(deviceName).error;
			"Available MIDI sources:".postln;
			MIDIClient.sources.do(_.postln);
			^this;
		});

		// Create MIDIOut
		midiOut = MIDIOut.newByName(deviceName, deviceName);

		// Initialize device (XY layout mode)
		this.initializeDevice;

		// Show initialization confirmation (blink entire grid green 3 times)
		this.showInitializationConfirmation;

		// Set up note on/off responders
		this.setupMIDIResponders(deviceName);

		// Install callbacks on ClipChannel instances
		this.installCallbacks;

		// Update all LEDs to reflect current state
		this.updateAllLEDs;

		// Start LED blinking routine
		this.startBlinkRoutine;

		"ClipLaunchpadMini: Connected to '%'".format(deviceName).postln;
	}

	// Initialize Launchpad Mini (XY layout mode)
	initializeDevice {
		// Reset to default state
		midiOut.sysex(Int8Array[240, 0, 32, 41, 2, 24, 14, 0, 247]);

		// Set to XY layout mode
		// SysEx: F0 00 20 29 02 18 22 01 F7
		midiOut.sysex(Int8Array[240, 0, 32, 41, 2, 24, 34, 1, 247]);

		"ClipLaunchpadMini: Device initialized (XY mode)".postln;
	}

	// Set up MIDI note on/off responders
	setupMIDIResponders { |deviceName|
		// Note On - button pressed
		midiIn.add(
			MIDIFunc.noteOn({ |velocity, note, chan, src|
				var row, col;
				#row, col = this.noteToCoords(note);
				if (row.notNil and: { col.notNil }, {
					this.handleButtonPress(row, col, velocity, true);
				});
			}, srcID: MIDIIn.findPort(deviceName, deviceName).uid)
		);

		// Note Off - button released
		midiIn.add(
			MIDIFunc.noteOff({ |velocity, note, chan, src|
				var row, col;
				#row, col = this.noteToCoords(note);
				if (row.notNil and: { col.notNil }, {
					this.handleButtonPress(row, col, velocity, false);
				});
			}, srcID: MIDIIn.findPort(deviceName, deviceName).uid)
		);

		"ClipLaunchpadMini: MIDI responders installed".postln;
	}

	// Convert MIDI note number to grid coordinates (XY mode)
	noteToCoords { |noteNum|
		var row, col;

		// XY layout: note = (row * 16) + col
		row = noteNum div 16;
		col = noteNum mod 16;

		// Validate coordinates
		if (row < 0 or: { row >= 8 } or: { col < 0 } or: { col >= 8 }, {
			^[nil, nil];  // Invalid
		});

		^[row, col];
	}

	// Convert grid coordinates to MIDI note number (XY mode)
	coordsToNote { |row, col|
		// XY layout: note = (row * 16) + col
		^(row * 16) + col;
	}

	// Update a single LED
	updateLED { |row, col, color, blinkMode|
		var note, velocity;

		note = this.coordsToNote(row, col);
		velocity = this.colorToVelocity(color);

		// Send note on with velocity = color
		midiOut.noteOn(0, note, velocity);
	}

	// Convert color symbol to MIDI velocity
	colorToVelocity { |color|
		^case
		{ color == \off }        { 12 }   // Off (min brightness)
		{ color == \red_low }    { 13 }   // Red low
		{ color == \red_mid }    { 14 }   // Red mid
		{ color == \red_high }   { 15 }   // Red full
		{ color == \amber_low }  { 29 }   // Amber low
		{ color == \amber_high } { 63 }   // Amber full
		{ color == \yellow }     { 62 }   // Yellow
		{ color == \green }      { 60 }   // Green
		{ 12 };  // Default: off
	}

	// Override channel colors for Launchpad's palette
	initChannelColors {
		channelColors = [
			\red_mid,      // Channel 0
			\amber_low,    // Channel 1
			\yellow,       // Channel 2
			\green,        // Channel 3
			\red_high,     // Channel 4
			\red_low,      // Channel 5
			\amber_high,   // Channel 6
			\yellow        // Channel 7
		];
	}

	// Show initialization confirmation: blink entire grid green 3 times
	showInitializationConfirmation {
		var greenVelocity = this.colorToVelocity(\green);
		var offVelocity = this.colorToVelocity(\off);

		// Fork a routine to blink asynchronously
		fork {
			3.do {
				// Turn all LEDs green (entire 8x8 grid)
				8.do { |row|
					8.do { |col|
						var note = this.coordsToNote(row, col);
						midiOut.noteOn(0, note, greenVelocity);
					};
				};

				// Wait 0.15 seconds
				0.15.wait;

				// Turn all LEDs off
				8.do { |row|
					8.do { |col|
						var note = this.coordsToNote(row, col);
						midiOut.noteOn(0, note, offVelocity);
					};
				};

				// Wait 0.15 seconds before next blink
				0.15.wait;
			};

			"ClipLaunchpadMini: Initialization animation complete".postln;
		};
	}

	// Disconnect (override to clear LEDs before disconnect)
	disconnect {
		// Clear all LEDs
		this.clearAllLEDs;

		// Reset device
		if (midiOut.notNil, {
			midiOut.sysex(Int8Array[240, 0, 32, 41, 2, 24, 14, 0, 247]);
		});

		// Call parent disconnect
		^super.disconnect;
	}
}
