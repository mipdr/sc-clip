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
 *   - Bottom-right pad (row 7, col 7): metronome on/off (lit amber when on)
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
	// portName is optional: when nil, the first port on deviceName is used.
	// (Port names are platform-specific -- on macOS the port is usually named
	// after the device, on Linux/ALSA it is e.g. "Launchpad Mini MIDI 1" --
	// so requiring an exact device+port match here fails on Linux.)
	connect { |deviceName = "Launchpad Mini", portName|
		var midiInPort, midiOutPort;

		// Initialize MIDI and connect all input sources, since MIDIClient.init
		// alone does not route hardware input to MIDIFunc responders. Routed
		// through ClipMIDI so this only happens once per session even if
		// another controller (e.g. ClipTR8s4Channel) also needs it -- calling
		// MIDIIn.connectAll a second time in the same session has been
		// observed to hang sclang on real hardware.
		ClipMIDI.connectAllInputs;

		// Find MIDI device
		midiInPort = this.findEndPoint(MIDIClient.sources, deviceName, portName);
		midiOutPort = this.findEndPoint(MIDIClient.destinations, deviceName, portName);

		if (midiInPort.isNil or: { midiOutPort.isNil }, {
			"ClipLaunchpadMini: Could not find device '%'".format(deviceName).error;
			"Available MIDI sources:".postln;
			MIDIClient.sources.do(_.postln);
			^nil;
		});

		// Create MIDIOut
		midiOut = MIDIOut(MIDIClient.destinations.indexOf(midiOutPort), midiOutPort.uid);

		// On Linux, ALSA only opens a hardware port's output while something is
		// subscribed to it -- uid-addressed sends alone are silently dropped
		// (no LEDs ever light). Subscribe SC's out port to the device.
		if (thisProcess.platform.name == \linux, {
			midiOut.connect(midiOutPort);
		});

		// Initialize device (XY layout mode)
		this.initializeDevice;

		// Show initialization confirmation (blink entire grid green 3 times)
		this.showInitializationConfirmation;

		// Set up note on/off responders
		this.setupMIDIResponders(midiInPort.uid);

		// Install callbacks on ClipChannel instances
		this.installCallbacks;

		// Update all LEDs to reflect current state
		this.updateAllLEDs;

		// Start LED blinking routine
		this.startBlinkRoutine;

		"ClipLaunchpadMini: Connected to '%' port '%'".format(
			midiInPort.device, midiInPort.name).postln;

		^this;
	}

	// First endpoint on deviceName (and portName, if given), or nil
	findEndPoint { |endPoints, deviceName, portName|
		^endPoints.detect { |ep|
			ep.device == deviceName and: { portName.isNil or: { ep.name == portName } }
		}
	}

	// Initialize Launchpad Mini (XY layout mode)
	// The original Launchpad Mini is configured with CC 0 on channel 1
	// (B0 00 xx), not SysEx -- the F0 00 20 29 02 18 ... messages are the
	// Launchpad MK2 protocol and are ignored by this device.
	initializeDevice {
		// Reset to default state (all LEDs off)
		midiOut.control(0, 0, 0);

		// Set to XY layout mode
		midiOut.control(0, 0, 1);

		"ClipLaunchpadMini: Device initialized (XY mode)".postln;
	}

	// Set up MIDI note on/off responders
	setupMIDIResponders { |srcUID|
		// Note On - button pressed
		midiIn.add(
			MIDIFunc.noteOn({ |velocity, note, chan, src|
				var row, col;
				#row, col = this.noteToCoords(note);
				if (row.notNil and: { col.notNil }, {
					this.handleButtonPress(row, col, velocity, true);
				});
			}, srcID: srcUID)
		);

		// Note Off - button released
		midiIn.add(
			MIDIFunc.noteOff({ |velocity, note, chan, src|
				var row, col;
				#row, col = this.noteToCoords(note);
				if (row.notNil and: { col.notNil }, {
					this.handleButtonPress(row, col, velocity, false);
				});
			}, srcID: srcUID)
		);

		"ClipLaunchpadMini: MIDI responders installed".postln;
	}

	// The bottom-right pad (row 7, col 7) toggles the metronome. It is outside
	// the clip area on grids smaller than 8x8; on a full 8x8 grid it takes
	// precedence over clip slot [7,7].
	handleButtonPress { |row, col, velocity, isNoteOn|
		if (row == 7 and: { col == 7 }, {
			if (isNoteOn, { this.toggleMetronome });
			^this;
		});
		^super.handleButtonPress(row, col, velocity, isNoteOn);
	}

	toggleMetronome {
		if (transport.metronomeEnabled, {
			transport.disableMetronome;
		}, {
			transport.enableMetronome(transport.metronomeAmp ? 0.3);
		});
		this.updateMetronomeLED;
	}

	// Amber full is the closest this bi-color (red/green) device gets to white
	updateMetronomeLED {
		this.sendLEDMessage(7, 7, if (transport.metronomeEnabled, \amber_high, \off));
	}

	// Also track metronome state changes made elsewhere (sclang, session load)
	updateBlinkingLEDs {
		super.updateBlinkingLEDs;
		this.updateMetronomeLED;
	}

	updateAllLEDs {
		super.updateAllLEDs;
		// Resend unconditionally, like the rest of the grid here (the cache
		// can be stale, e.g. after the init animation's final "off" frame)
		ledCache.removeAt(707);
		this.updateMetronomeLED;
	}

	// Convert MIDI note number to grid coordinates (XY mode)
	noteToCoords { |noteNum|
		var row, col;

		// XY layout: note = (row * 16) + col
		row = noteNum.div(16);
		col = noteNum % 16;

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

		// Schedule at the current *real* time, not the calling thread's logical
		// time: connect usually runs inside a boot routine whose logical time
		// has fallen seconds behind (MIDIClient.init/MIDIIn.connectAll block
		// without advancing it), and a plain fork would then run every wait
		// back-to-back to catch up, sending all frames at once (ending on off).
		SystemClock.schedAbs(Main.elapsedTime, Routine {
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

			// The final "off" frame overwrote whatever updateAllLEDs drew
			// while the animation was running -- redraw the real grid state
			this.updateAllLEDs;

			"ClipLaunchpadMini: Initialization animation complete".postln;
		});
	}

	// Disconnect (override to clear LEDs before disconnect)
	disconnect {
		// Clear all LEDs
		this.clearAllLEDs;

		// Reset device
		if (midiOut.notNil, {
			midiOut.control(0, 0, 0);
		});

		// Call parent disconnect
		^super.disconnect;
	}
}
