/*
 * ClipUMC404
 *
 * Concrete implementation for Behringer UMC404HD audio interface.
 *
 * Hardware specs:
 *   - 4 line/mic inputs (combo XLR/TRS jacks)
 *   - 4 line outputs (TRS, main L/R on channels 1-2)
 *   - 1 MIDI In port
 *   - 1 MIDI Out port
 *   - Class-compliant USB audio/MIDI on Linux (no driver needed)
 *   - Shows up as ALSA card "U192k" and MIDI ports "UMC404HD 192k MIDI 1"
 *
 * This class handles:
 *   - Automatic MIDI clock output routing (SuperCollider -> hardware)
 *   - Optional MIDI thru (hardware MIDI IN -> MIDI OUT)
 *   - Connection state tracking
 *
 * Note: Audio routing is handled by JACK (see start-audio.sh) and SC-Clip's
 * own input monitoring synths. This class focuses on MIDI functionality and
 * providing a unified interface object for UMC404-specific operations.
 */

ClipUMC404 : ClipAudioInterface {
	var <midiDeviceName;  // MIDI device name (may differ from audio device name)
	var <midiOut;         // MIDIOut instance for clock/thru
	var <midiThruEnabled; // MIDI thru state
	var <projectDir;      // Project directory for connect-midi.sh script

	*new { |projectDir|
		^super.new(
			numInputs: 4,
			numOutputs: 4,  // UMC404 has 4 outputs (main L/R on 1-2, additional on 3-4)
			deviceName: "UMC404HD 192k"
		).init(projectDir);
	}

	init { |projDir|
		midiDeviceName = "UMC404HD 192k";
		midiThruEnabled = false;
		projectDir = projDir;
	}

	// Connect to SC-Clip and set up MIDI routing
	connect { |clipInstance|
		if (connected, {
			"ClipUMC404: Already connected".warn;
			^this;
		});

		clip = clipInstance;

		// Initialize MIDI if needed
		if (MIDIClient.initialized.not, { MIDIClient.init });

		// Create MIDIOut for clock and thru
		midiOut = MIDIOut.newByName(midiDeviceName, midiDeviceName ++ " MIDI 1");

		if (midiOut.isNil, {
			"ClipUMC404: Failed to create MIDIOut - device may not be connected".error;
			"Available MIDI destinations:".postln;
			MIDIClient.destinations.do(_.postln);
			^this;
		});

		// Run connect-midi.sh to wire SuperCollider's virtual ALSA sequencer
		// port to the UMC404's hardware MIDI port
		if (projectDir.notNil, {
			var scriptPath = projectDir ++ "/scripts/connect-midi.sh";
			scriptPath.unixCmd({ |exitCode|
				if (exitCode == 0) {
					"ClipUMC404: MIDI routing established (SuperCollider -> hardware port)".postln;
				} {
					"ClipUMC404: connect-midi.sh failed (exit %) - MIDI output may not work".format(exitCode).warn;
				};
			});
		}, {
			"ClipUMC404: No project directory provided - skipping connect-midi.sh".warn;
			"ClipUMC404: MIDI output may not be routed to hardware. Run connect-midi.sh manually.".warn;
		});

		connected = true;
		"ClipUMC404: Connected (% inputs, % outputs)".format(numInputs, numOutputs).postln;

		^this;
	}

	// Disconnect
	disconnect {
		if (connected.not, {
			^this;
		});

		// Disable MIDI thru if enabled
		if (midiThruEnabled, {
			this.disableMIDIThru;
		});

		// MIDIOut doesn't need explicit cleanup, but we nil the reference
		midiOut = nil;
		clip = nil;
		connected = false;

		"ClipUMC404: Disconnected".postln;
	}

	// Enable MIDI thru: hardware MIDI IN -> MIDI OUT
	// This is done at the ALSA sequencer level by connecting the UMC404's
	// MIDI port to itself (the hardware port is duplex)
	enableMIDIThru {
		if (connected.not, {
			"ClipUMC404: Cannot enable MIDI thru - not connected".error;
			^this;
		});

		if (midiThruEnabled, {
			"ClipUMC404: MIDI thru already enabled".postln;
			^this;
		});

		// Use aconnect to connect the UMC404's hardware port to itself
		"aconnect -l | awk '/UMC404HD 192k/ && /MIDI 1/ { c=$1; sub(\":\",\"\",c); print c; exit }' | xargs -I {} aconnect {}:{} {}:{}".unixCmd({ |exitCode|
			if (exitCode == 0) {
				midiThruEnabled = true;
				"ClipUMC404: MIDI thru enabled (hardware IN -> OUT)".postln;
			} {
				"ClipUMC404: Failed to enable MIDI thru (aconnect exit %)".format(exitCode).error;
			};
		});
	}

	// Disable MIDI thru
	disableMIDIThru {
		if (midiThruEnabled.not, {
			"ClipUMC404: MIDI thru not enabled".postln;
			^this;
		});

		// Use aconnect -d to disconnect the self-connection
		"aconnect -l | awk '/UMC404HD 192k/ && /MIDI 1/ { c=$1; sub(\":\",\"\",c); print c; exit }' | xargs -I {} aconnect -d {}:{} {}:{}".unixCmd({ |exitCode|
			if (exitCode == 0) {
				midiThruEnabled = false;
				"ClipUMC404: MIDI thru disabled".postln;
			} {
				"ClipUMC404: Failed to disable MIDI thru (aconnect exit %)".format(exitCode).warn;
			};
		});
	}

	// Get MIDIOut for use with SC-Clip's MIDI clock
	getMIDIOut {
		if (connected.not, {
			"ClipUMC404: Not connected - cannot provide MIDIOut".error;
			^nil;
		});

		^midiOut;
	}
}
