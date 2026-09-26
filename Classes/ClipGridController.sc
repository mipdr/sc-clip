/*
 * ClipGridController
 *
 * Abstract base class for MIDI grid controllers (Launchpad, APC40, Push, etc.)
 * Handles button press detection, long press timing, LED state management, and
 * integration with ClipGrid via callbacks.
 *
 * Concrete subclasses must implement:
 *   - connect(deviceName)
 *   - updateLED(row, col, color, blinkMode)
 */

ClipGridController {
	var <grid;              // ClipGrid reference
	var <transport;         // ClipTransport reference
	var <numRows;           // Number of rows for clip control
	var <numCols;           // Number of columns (channels)
	var <midiIn;            // Array of MIDIFunc instances
	var <midiOut;           // MIDIOut instance
	var <pressTracker;      // IdentityDictionary: buttonID -> press time (in beats)
	var <blinkRoutine;      // Routine for LED blinking
	var <ledStates;         // IdentityDictionary: key -> (color, blinkMode)
	var <ledCache;          // IdentityDictionary: key -> last sent value (optimization)
	var <channelColors;     // Array of colors per channel
	var <loopLengthBeats;   // Loop length in beats (hard-coded to 8)
	var <longPressThreshold; // Threshold in beats for long press (default 0.5)
	var <deleteBufferTime;  // Time buffer in seconds after deletion before re-arming (default 2.0)
	var <deleteLockout;     // IdentityDictionary: slotKey -> lockout end time (SystemClock)

	*new { |grid, transport, numRows = 4, numCols = 8|
		^super.newCopyArgs(
			grid,                        // grid
			transport,                   // transport
			numRows,                     // numRows
			numCols,                     // numCols
			nil,                         // midiIn
			nil,                         // midiOut
			nil,                         // pressTracker
			nil,                         // blinkRoutine
			nil,                         // ledStates
			nil,                         // ledCache
			nil,                         // channelColors
			8,                           // loopLengthBeats (hard-coded)
			0.5,                         // longPressThreshold
			2.0,                         // deleteBufferTime (2 seconds)
			nil                          // deleteLockout
		).init;
	}

	init {
		pressTracker = IdentityDictionary.new;
		ledStates = IdentityDictionary.new;
		ledCache = IdentityDictionary.new;
		deleteLockout = IdentityDictionary.new;
		midiIn = List.new;

		// Initialize default channel colors
		this.initChannelColors;

		"ClipGridController: Initialized (% rows × % cols)".format(numRows, numCols).postln;
	}

	// Initialize default channel colors (can be overridden)
	initChannelColors {
		channelColors = [
			\red_mid,     // Channel 0
			\amber_low,   // Channel 1
			\yellow,      // Channel 2
			\green,       // Channel 3
			\red_high,    // Channel 4
			\red_low,     // Channel 5
			\amber_high,  // Channel 6
			\yellow       // Channel 7
		];
	}

	// Connect to MIDI device (abstract - must override in subclass)
	connect { |deviceName|
		this.subclassResponsibility(thisMethod);
	}

	// Disconnect MIDI device
	disconnect {
		// Stop blink routine
		if (blinkRoutine.notNil, {
			blinkRoutine.stop;
			blinkRoutine = nil;
		});

		// Free all MIDIFunc responders
		midiIn.do(_.free);
		midiIn.clear;

		// Clear all LEDs
		this.clearAllLEDs;

		"ClipGridController: Disconnected".postln;
	}

	// Handle button press/release from MIDI
	handleButtonPress { |row, col, velocity, isNoteOn|
		var buttonID = (row * 100) + col;

		// Validate coordinates
		if (row < 0 or: { row >= numRows } or: { col < 0 } or: { col >= numCols }, {
			^this;  // Ignore out-of-range buttons
		});

		if (isNoteOn, {
			// Track press time for long press detection
			pressTracker[buttonID] = transport.clock.beats;
		}, {
			// Button released - calculate duration
			var pressTime = pressTracker[buttonID];
			if (pressTime.notNil, {
				var pressDuration = transport.clock.beats - pressTime;
				pressTracker.removeAt(buttonID);

				if (pressDuration > longPressThreshold, {
					this.handleLongPress(row, col);
				}, {
					this.handleShortPress(row, col);
				});
			});
		});
	}

	// Handle short press (< threshold)
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

		// State machine for short press
		case
		{ slot.isEmpty } {
			// Empty slot: arm and launch
			grid.armSlot(col, row, loopLengthBeats);
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

	// Handle long press (>= threshold)
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
			// Empty: just arm (don't launch)
			grid.armSlot(col, row, loopLengthBeats);
		});
	}

	// Callback when a slot changes state (called from ClipChannel)
	onSlotStateChanged { |channelIndex, slotIndex, newState|
		var row = slotIndex;
		var col = channelIndex;
		var color, blinkMode;

		// Map state to LED color and blink mode
		#color, blinkMode = this.stateToLED(newState, channelColors[channelIndex]);

		// Update LED state (for blinking routine)
		this.setLEDState(row, col, color, blinkMode);

		// Immediate LED update
		this.updateLED(row, col, color, blinkMode);
	}

	// Map slot state to LED color and blink mode
	stateToLED { |state, channelColor|
		^case
		{ state == \empty }        { [\off, \solid] }
		{ state == \armed }        { [channelColor, \solid] }
		{ state == \recording }    { [channelColor, \fast] }   // Fast blink
		{ state == \playing }      { [channelColor, \solid] }
		{ state == \stopped }      { [channelColor, \slow] }   // Slow blink
		{ state == \overdubbing }  { [channelColor, \solid] }
		{ state == \queuedToPlay } { [channelColor, \fast] }
		{ state == \queuedToStop } { [channelColor, \fast] }
		{ [\off, \solid] };  // Default
	}

	// Set LED state (for blinking)
	setLEDState { |row, col, color, blinkMode|
		var key = (row * 100) + col;
		ledStates[key] = (color: color, blinkMode: blinkMode);
	}

	// Update a single LED (abstract - must override in subclass)
	updateLED { |row, col, color, blinkMode|
		this.subclassResponsibility(thisMethod);
	}

	// Update all LEDs to reflect current slot states
	updateAllLEDs {
		numRows.do { |row|
			numCols.do { |col|
				var slot = grid.getSlot(col, row);
				if (slot.notNil, {
					var color, blinkMode;
					#color, blinkMode = this.stateToLED(slot.state, channelColors[col]);
					this.setLEDState(row, col, color, blinkMode);
					this.updateLED(row, col, color, blinkMode);
				});
			};
		};

		"ClipGridController: Updated all LEDs".postln;
	}

	// Clear all LEDs
	clearAllLEDs {
		numRows.do { |row|
			numCols.do { |col|
				this.updateLED(row, col, \off, \solid);
			};
		};
	}

	// Start LED blinking routine
	startBlinkRoutine {
		if (blinkRoutine.notNil, {
			blinkRoutine.stop;
		});

		blinkRoutine = Routine({
			loop {
				this.updateBlinkingLEDs;
				(1/8).wait;  // Update at 1/8 beat resolution
			};
		}).play(transport.clock);

		"ClipGridController: Started blink routine".postln;
	}

	// Update LEDs that need blinking
	updateBlinkingLEDs {
		var beat = transport.clock.beats;
		var fastPhase = (beat * 8).floor % 2;  // Toggle every 1/8 beat (8Hz)
		var slowPhase = beat.floor % 2;        // Toggle every beat (1Hz)

		ledStates.keysValuesDo { |key, state|
			var row = key.div(100);
			var col = key % 100;
			var color = state[\color];
			var blinkMode = state[\blinkMode];
			var shouldBeOn;

			case
			{ blinkMode == \solid } { shouldBeOn = true }
			{ blinkMode == \fast }  { shouldBeOn = (fastPhase == 0) }
			{ blinkMode == \slow }  { shouldBeOn = (slowPhase == 0) }
			{ shouldBeOn = true };  // Default

			if (shouldBeOn, {
				this.sendLEDMessage(row, col, color);
			}, {
				this.sendLEDMessage(row, col, \off);
			});
		};
	}

	// Send LED message with caching (optimization)
	sendLEDMessage { |row, col, color|
		var key = (row * 100) + col;
		var cachedValue = ledCache[key];

		// Only send if changed
		if (cachedValue != color, {
			this.updateLED(row, col, color, \solid);
			ledCache[key] = color;
		});
	}

	// Install callbacks on all channels
	installCallbacks {
		grid.channels.do { |channel, chanIdx|
			channel.slotStateAction = { |slotIdx, newState|
				this.onSlotStateChanged(chanIdx, slotIdx, newState);
			};
		};

		"ClipGridController: Installed state change callbacks".postln;
	}

	// Set a lockout period for a slot (prevents arming for specified duration)
	setSlotLockout { |slotKey, duration|
		var lockoutEndTime = Main.elapsedTime + duration;
		deleteLockout[slotKey] = lockoutEndTime;

		// Schedule cleanup of lockout entry after duration
		SystemClock.sched(duration, {
			deleteLockout.removeAt(slotKey);
		});
	}

	// Check if a slot is currently locked out from being armed
	isSlotLockedOut { |slotKey|
		var lockoutEndTime = deleteLockout[slotKey];
		if (lockoutEndTime.isNil, {
			^false;  // No lockout
		});

		// Check if lockout has expired
		if (Main.elapsedTime >= lockoutEndTime, {
			deleteLockout.removeAt(slotKey);
			^false;  // Lockout expired
		});

		^true;  // Still locked out
	}

	// Cleanup
	free {
		this.disconnect;
	}
}
