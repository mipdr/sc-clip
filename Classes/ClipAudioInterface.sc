/*
 * ClipAudioInterface
 *
 * Abstract base class for audio interface integration with SC-Clip.
 * Manages hardware input/output routing and interface-specific functionality.
 *
 * Unlike MIDI controllers, audio interfaces handle audio routing between
 * hardware I/O and SC-Clip channels. Some interfaces (like the UMC404)
 * also provide MIDI ports for ancillary functions like MIDI thru.
 *
 * Concrete subclasses must implement:
 *   - connect(clip)
 *   - disconnect()
 *
 * Optional methods that subclasses may override:
 *   - enableMIDIThru()  (if the interface has MIDI ports)
 *   - disableMIDIThru() (if the interface has MIDI ports)
 */

ClipAudioInterface {
	var <clip;              // SCClip instance
	var <numInputs;         // Number of hardware inputs
	var <numOutputs;        // Number of hardware outputs
	var <deviceName;        // Human-readable device name
	var <connected;         // Connection state

	*new { |numInputs = 4, numOutputs = 2, deviceName = "AudioInterface"|
		^super.newCopyArgs(
			nil,                    // clip
			numInputs,              // numInputs
			numOutputs,             // numOutputs
			deviceName,             // deviceName
			false                   // connected
		);
	}

	// Connect the interface to SC-Clip (abstract - must override in subclass)
	connect { |clipInstance|
		this.subclassResponsibility(thisMethod);
	}

	// Disconnect the interface (abstract - must override in subclass)
	disconnect {
		this.subclassResponsibility(thisMethod);
	}

	// Map a hardware input channel to an SC-Clip channel
	// This is typically handled automatically during connection, but can be
	// overridden for advanced routing scenarios
	mapInput { |hardwareInputIndex, clipChannelIndex|
		if (clip.isNil, {
			"ClipAudioInterface: Cannot map input - not connected to SC-Clip".error;
			^this;
		});

		if (hardwareInputIndex >= numInputs, {
			"ClipAudioInterface: Hardware input % out of range (0-%)".format(
				hardwareInputIndex, numInputs - 1
			).error;
			^this;
		});

		if (clipChannelIndex >= clip.numChannels, {
			"ClipAudioInterface: SC-Clip channel % out of range (0-%)".format(
				clipChannelIndex, clip.numChannels - 1
			).error;
			^this;
		});

		// Default implementation: SC-Clip channels already read from hardware
		// inputs via SoundIn.ar(channelIndex) in the input monitor synth.
		// This method exists primarily for documentation and potential override.
		"ClipAudioInterface: Mapped hardware input % -> SC-Clip channel %".format(
			hardwareInputIndex, clipChannelIndex
		).postln;
	}

	// Map an SC-Clip channel to a hardware output channel
	// By default, SC-Clip's master bus outputs to the first stereo pair (0-1)
	// This method exists for potential future routing flexibility
	mapOutput { |clipChannelIndex, hardwareOutputIndex|
		if (clip.isNil, {
			"ClipAudioInterface: Cannot map output - not connected to SC-Clip".error;
			^this;
		});

		"ClipAudioInterface: Output routing controlled by SC-Clip master bus".postln;
	}

	// Cleanup
	free {
		this.disconnect;
	}
}
