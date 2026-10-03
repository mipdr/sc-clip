/*
 * ClipLaunchpadMini
 *
 * Concrete implementation for Novation Launchpad Mini (original)
 * Handles Launchpad-specific MIDI protocol, button mapping, and LED colors.
 *
 * Grid Layout (XY mode):
 *   - 8x8 grid
 *   - Columns 0-3: Audio channels (4 channels)
 *   - Columns 4-5: MIDI channels (2 channels)
 *   - Column 6: Reserved
 *   - Column 7: Controls (clip length selector + metronome)
 *   - Rows 0-5: Clip slots (6 slots per channel)
 *   - Row 6: Channel selector (for input mapping, cols 0-3)
 *   - Row 7: Input selector (for input mapping, cols 0-3) + metronome (col 7)
 *   - Column 7, Rows 0-6 = binary clip length selector (1, 2, 4, 8, 16, 32, 64 bars)
 *     Multiple buttons can be selected simultaneously to express any bar length (1-127).
 *     Selected bits lit orange, unselected green.
 *     Examples: [2] = 2 bars, [1,2,4] = 7 bars, [1,4,8,16] = 29 bars
 *   - Row 7, Col 7 = metronome on/off (lit amber when on)
 *   - MIDI note = (row * 16) + col
 *
 * Input-to-Channel Mapping (rows 6-7, cols 0-3):
 *   - Row 7 (cols 0-3): Input selector - press to select which input to configure
 *   - Row 6 (cols 0-3): Channel selector - press to map selected input to channel
 *   - Green = unselected, Orange = selected/mapped
 *
 * LED Colors (via velocity):
 *   - 12 = off
 *   - 13 = red_low, 14 = red_mid, 15 = red_high
 *   - 29 = amber_low, 63 = amber_high
 *   - 31 = orange
 *   - 62 = yellow
 *   - 60 = green
 */

ClipLaunchpadMini : ClipGridController {
	var <clipLengthBars;      // Currently selected clip length in bars (sum of binary selections)
	var <clipLengthOptions;   // Array of binary place values (powers of 2)
	var <clipLengthSelections; // Set of selected binary places (e.g., Set[1, 2] for 3 bars)
	var <selectedInputIndex;  // Currently selected input for mapping (nil = none selected)
	var <numAudioChannels;    // Number of audio channels (for input mapping UI)

	*new { |grid, transport, numRows = 6, numCols = 6, numAudioChannels = 4|
		// numRows = 6 for clip slots (rows 0-5)
		// numCols = 6 total columns used for clips (cols 0-5: 4 audio + 2 MIDI)
		// numAudioChannels = 4 for input mapping UI (rows 6-7, cols 0-3)
		^super.new(grid, transport, numRows, numCols)
			.initClipLength
			.initInputMapping(numAudioChannels);
	}

	// Initialize clip length settings
	initClipLength {
		// Binary place values: 1, 2, 4, 8, 16, 32, 64 (7 bits = 0-127 bars)
		clipLengthOptions = [1, 2, 4, 8, 16, 32, 64];
		// Start with 2 bars selected (just the "2" bit)
		clipLengthSelections = Set[2];
		clipLengthBars = 2;
	}

	// Initialize input mapping
	initInputMapping { |numAudio|
		numAudioChannels = numAudio;
		selectedInputIndex = nil;
	}

	// Convert bars to beats based on current time signature
	barsToBeats { |bars|
		^bars * transport.beatsPerBar;
	}

	// Loop length for new clips, from the clip length selector
	// (overrides parent's fixed loopLengthBeats)
	loopLengthBeats {
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

	// Handle button press - routes to clip length selector or metronome or input mapping or parent
	handleButtonPress { |row, col, velocity, isNoteOn|
		// Row 7 (input selector), cols 0-3
		if (row == 7 and: { col < numAudioChannels }, {
			if (isNoteOn, { this.selectInput(col) });
			^this;
		});

		// Row 6 (channel selector), cols 0-3 - only active if input selected
		if (row == 6 and: { col < numAudioChannels } and: { selectedInputIndex.notNil }, {
			if (isNoteOn, { this.mapInputToChannel(selectedInputIndex, col) });
			^this;
		});

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

	// Toggle clip length bit (binary selector)
	selectClipLength { |row|
		var bitValue;

		if (row < clipLengthOptions.size, {
			bitValue = clipLengthOptions[row];

			// Toggle the bit: add if not present, remove if present
			if (clipLengthSelections.includes(bitValue), {
				clipLengthSelections.remove(bitValue);
			}, {
				clipLengthSelections.add(bitValue);
			});

			// Calculate total bar length (sum of all selected bits)
			clipLengthBars = clipLengthSelections.sum;

			// Ensure at least 1 bar (if all bits cleared, select 1)
			if (clipLengthBars == 0, {
				clipLengthSelections.add(1);
				clipLengthBars = 1;
			});

			"ClipLaunchpadMini: Binary selector % = % bars (% beats)".format(
				clipLengthSelections.asArray.sort,
				clipLengthBars,
				this.loopLengthBeats
			).postln;

			this.updateClipLengthLEDs;
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

	// Select which input to configure for mapping
	selectInput { |inputIndex|
		if (selectedInputIndex == inputIndex, {
			// Deselect if clicking same input again
			selectedInputIndex = nil;
			"ClipLaunchpadMini: Input deselected".postln;
		}, {
			selectedInputIndex = inputIndex;
			"ClipLaunchpadMini: Selected input % for mapping".format(inputIndex).postln;
		});

		this.updateInputMappingLEDs;
	}

	// Map selected input to a channel
	mapInputToChannel { |inputIdx, channelIdx|
		grid.setChannelInput(channelIdx, inputIdx);

		"ClipLaunchpadMini: Mapped input % to channel %".format(inputIdx, channelIdx).postln;

		this.updateInputMappingLEDs;
	}

	// Update clip length selector LEDs (column 7, rows 0-6)
	// Shows binary selection: selected bits are orange, unselected are green
	updateClipLengthLEDs {
		clipLengthOptions.do { |bitValue, index|
			// Selected bit: orange; unselected: green
			this.sendLEDMessage(index, 7,
				if (clipLengthSelections.includes(bitValue), \orange, \green));
		};
	}

	// Amber full is the closest this bi-color (red/green) device gets to white
	updateMetronomeLED {
		this.sendLEDMessage(7, 7, if (transport.metronomeEnabled, \amber_high, \off));
	}

	// Update input/channel mapping LEDs (rows 6-7, cols 0-3)
	updateInputMappingLEDs {
		// Row 7 (input selector): Show all inputs, highlight selected
		numAudioChannels.do { |inputIdx|
			this.sendLEDMessage(7, inputIdx,
				if (inputIdx == selectedInputIndex, \orange, \green));
		};

		// Row 6 (channel selector): Only show when input selected
		numAudioChannels.do { |channelIdx|
			var color;

			if (selectedInputIndex.isNil, {
				// No input selected: all green
				color = \green;
			}, {
				// Input selected: highlight if this channel is mapped to selected input
				var mappedInput = grid.getChannelInput(channelIdx);
				color = if (mappedInput == selectedInputIndex, \orange, \green);
			});

			this.sendLEDMessage(6, channelIdx, color);
		};
	}

	// Also track metronome state changes made elsewhere (sclang, session load)
	updateBlinkingLEDs {
		super.updateBlinkingLEDs;
		this.updateMetronomeLED;
		// Clip length LEDs don't need blinking, but update if cache is stale
	}

	updateAllLEDs {
		super.updateAllLEDs;
		// Resend the control column and mapping rows unconditionally, like the
		// rest of the grid here (the cache can be stale, e.g. after the init
		// animation's final "off" frame)
		8.do { |row| ledCache.removeAt((row * 100) + 7) };
		numAudioChannels.do { |col|
			ledCache.removeAt((6 * 100) + col);  // Row 6 (channel selector)
			ledCache.removeAt((7 * 100) + col);  // Row 7 (input selector)
		};
		this.updateClipLengthLEDs;
		this.updateMetronomeLED;
		this.updateInputMappingLEDs;
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
		{ color == \orange }     { 31 }   // Red full + green low
		{ color == \yellow }     { 62 }   // Yellow
		{ color == \green }      { 60 }   // Green
		{ 12 };  // Default: off
	}

	// Override channel colors for Launchpad's palette (cols 0-3 audio, 4-5 MIDI)
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
