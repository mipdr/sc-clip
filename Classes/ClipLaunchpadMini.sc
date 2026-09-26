/*
 * ClipLaunchpadMini
 *
 * Concrete implementation for Novation Launchpad Mini (original)
 * Handles Launchpad-specific MIDI protocol, button mapping, and LED colors.
 *
 * Grid Layout (XY mode):
 *   - 8x8 grid: Columns 0-6 = channels (7 channels), Column 7 = controls
 *   - Rows 0-3 = clip slots (for columns 0-6)
 *   - Column 7, Rows 0-6 = clip length selector (1, 2, 4, 8, 16, 32, 64 bars)
 *   - Row 7, Col 7 = metronome on/off (lit amber when on)
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
	var <clipLengthBars;      // Currently selected clip length in bars (1, 2, 4, 8, 16, 32, 64)
	var <clipLengthOptions;   // Array of available clip lengths in bars

	*new { |grid, transport, numRows = 4, numCols = 7|
		// Note: numCols = 7 for clip channels, column 7 is used for controls
		^super.new(grid, transport, numRows, numCols).initClipLength;
	}

	// Initialize clip length settings
	initClipLength {
		// Available clip lengths in bars: 1, 2, 4, 8, 16, 32, 64
		clipLengthOptions = [1, 2, 4, 8, 16, 32, 64];
		// Default to 2 bars (index 1)
		clipLengthBars = 2;
	}

	// Convert bars to beats based on current time signature
	barsToBeats { |bars|
		^bars * transport.beatsPerBar;
	}

	// Get current loop length in beats (overrides parent's loopLengthBeats)
	getLoopLengthBeats {
		^this.barsToBeats(clipLengthBars);
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

	// Handle button press - routes to clip length selector or metronome or parent
	handleButtonPress { |row, col, velocity, isNoteOn|
		// Column 7, rows 0-6: Clip length selector
		if (col == 7 and: { row >= 0 } and: { row < clipLengthOptions.size }, {
			if (isNoteOn, { this.selectClipLength(row) });
			^this;
		});

		// Bottom-right pad (row 7, col 7): metronome toggle
		if (row == 7 and: { col == 7 }, {
			if (isNoteOn, { this.toggleMetronome });
			^this;
		});

		// All other buttons: pass to parent for clip control
		^super.handleButtonPress(row, col, velocity, isNoteOn);
	}

	// Select clip length based on row index in column 7
	selectClipLength { |row|
		if (row < clipLengthOptions.size, {
			clipLengthBars = clipLengthOptions[row];
			"ClipLaunchpadMini: Selected clip length: % bars (% beats)".format(
				clipLengthBars, this.getLoopLengthBeats
			).postln;
			this.updateClipLengthLEDs;
		});
	}

	// Override parent's handleShortPress to use configurable clip length
	handleShortPress { |row, col|
		var slot = grid.getSlot(col, row);  // col=channel, row=slot index
		var slotKey = (col * 100) + row;

		if (slot.isNil, {
			"ClipGridController: Invalid slot [%,%]".format(col, row).warn;
			^this;
		});

		// Check if slot is in delete lockout period
		if (this.isSlotLockedOut(slotKey), {
			// Ignore press during lockout period
			^this;
		});

		// State machine for short press (using configurable clip length)
		case
		{ slot.isEmpty } {
			// Empty slot: arm and launch with current clip length
			grid.armSlot(col, row, this.getLoopLengthBeats);
			grid.launchSlot(col, row);
		}
		{ slot.isArmed } {
			// Already armed: just launch
			grid.launchSlot(col, row);
		}
		{ slot.isRecording } {
			// Recording: ignore (wait for loop to complete)
		}
		{ slot.isPlaying or: { slot.isOverdubbing } } {
			// Playing: stop
			grid.stopSlot(col, row);
		}
		{ slot.isStopped } {
			// Stopped: restart playback
			grid.launchSlot(col, row);
		}
		{
			// Queued to stop or other state: ignore
		};
	}

	// Override parent's handleLongPress to use configurable clip length
	handleLongPress { |row, col|
		var slot = grid.getSlot(col, row);
		var slotKey = (col * 100) + row;

		if (slot.isNil, {
			"ClipGridController: Invalid slot [%,%]".format(col, row).warn;
			^this;
		});

		if (slot.hasAudio, {
			// Has audio: clear the slot
			grid.clearSlot(col, row);

			// Set lockout period: prevent arming for deleteBufferTime seconds
			// This prevents immediately starting recording when button is released
			this.setSlotLockout(slotKey, deleteBufferTime);
		}, {
			// Empty: just arm (don't launch) with current clip length
			grid.armSlot(col, row, this.getLoopLengthBeats);
		});
	}

	toggleMetronome {
		if (transport.metronomeEnabled, {
			transport.disableMetronome;
		}, {
			transport.enableMetronome(transport.metronomeAmp ? 0.3);
		});
		this.updateMetronomeLED;
	}

	// Update clip length selector LEDs (column 7, rows 0-6)
	updateClipLengthLEDs {
		clipLengthOptions.do { |bars, index|
			var color;
			// Selected length: amber/orange, unselected: green (closest to white on this device)
			color = if (bars == clipLengthBars, \amber_high, \green);
			this.sendLEDMessage(index, 7, color);
		};
	}

	// Amber full is the closest this bi-color (red/green) device gets to white
	updateMetronomeLED {
		this.sendLEDMessage(7, 7, if (transport.metronomeEnabled, \amber_high, \off));
	}

	// Also track metronome state changes made elsewhere (sclang, session load)
	updateBlinkingLEDs {
		super.updateBlinkingLEDs;
		this.updateMetronomeLED;
		// Clip length LEDs don't need blinking, but update if cache is stale
	}

	updateAllLEDs {
		super.updateAllLEDs;
		// Update clip length selector LEDs
		this.updateClipLengthLEDs;
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

	// Override channel colors for Launchpad's palette (7 channels)
	initChannelColors {
		channelColors = [
			\red_mid,      // Channel 0
			\amber_low,    // Channel 1
			\yellow,       // Channel 2
			\green,        // Channel 3
			\red_high,     // Channel 4
			\red_low,      // Channel 5
			\amber_high    // Channel 6
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
