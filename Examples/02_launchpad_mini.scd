/*
 * SC-Clip Example: Launchpad Mini Integration
 *
 * This example demonstrates how to use a Novation Launchpad Mini (original)
 * with SC-Clip for hands-on clip launching and recording.
 *
 * Requirements:
 *   - Novation Launchpad Mini (original) connected via USB
 *   - Audio input source (microphone, instrument, etc.)
 *
 * Grid Layout:
 *   - Columns (0-7) = Channels (up to 8)
 *   - Rows (0-3) = Clip indexes (4 slots per channel)
 *   - Rows (4-7) = Reserved for future features
 *
 * Button Behavior:
 *   - Short press on empty slot: Arm and start recording (8 beats)
 *   - Short press on playing slot: Stop
 *   - Short press on stopped slot: Resume playback
 *   - Long press (>1 second): Delete clip and restart recording
 *
 * LED Feedback:
 *   - Off: Empty slot
 *   - Solid color: Armed or Playing
 *   - Fast blink (8Hz): Recording
 *   - Slow blink (1Hz): Stopped (has audio)
 */

// ===================================
// 1. Boot and Initialize SC-Clip
// ===================================

(
s.waitForBoot {
	// Create SC-Clip instance with 8 channels and 4 slots per channel
	~clip = SCClip.new(numChannels: 8, numSlots: 4);

	~clip.boot({
		"SC-Clip initialized!".postln;

		// Set tempo
		~clip.setTempo(120);

		// Optional: Enable metronome for timing reference
		~clip.enableMetronome(0.3);

		"Ready to connect Launchpad Mini...".postln;
	});
};
)

// ===================================
// 2. Connect Launchpad Mini
// ===================================

(
// Initialize MIDI
MIDIClient.init;
MIDIIn.connectAll;

// List available MIDI devices
"Available MIDI devices:".postln;
MIDIClient.sources.do { |src, i|
	"  [%] %".format(i, src.name).postln;
};

// Connect Launchpad Mini
~controller = ~clip.connectController(\launchpadMini, "Launchpad Mini");

// Note: If you have a different device name, you can specify it:
// ~controller = ~clip.connectController(\launchpadMini, "Launchpad Mini MK2");

"Launchpad Mini connected! Ready to record.".postln;
)

// ===================================
// 3. Usage Instructions
// ===================================

/*
Now you can use your Launchpad Mini to control SC-Clip!

Basic Workflow:
1. Press any button on the top 4 rows (clips area)
2. The LED will light up, and recording will start after quantization
3. Play/sing into your microphone for 8 beats
4. Recording automatically stops, and the clip starts looping
5. Press the same button to stop the clip
6. Press again to restart playback
7. Hold the button for >0.5 beats to delete and re-record

Tips:
- Each column (0-7) is a separate channel with its own color
- Each row (0-3) is a different clip slot
- All clips are quantized to beat boundaries for tight timing
- Change tempo with: ~clip.setTempo(140);
*/

// ===================================
// 4. Advanced Configuration
// ===================================

// Change tempo
~clip.setTempo(140);

// Change quantization (default is 1 beat)
~clip.setQuantization(2);  // Snap to 2-beat boundaries

// Adjust metronome volume
~clip.setMetronomeVolume(0.5);

// Disable metronome
~clip.disableMetronome;

// Check system status
~clip.printStatus;

// ===================================
// 5. Manual Slot Control (Optional)
// ===================================

// You can also control slots programmatically:

// Arm slot [channel=0, slot=0] for 8-beat recording
~clip.armSlot(0, 0, 8);

// Launch slot (start recording or playback)
~clip.launchSlot(0, 0);

// Stop slot
~clip.stopSlot(0, 0);

// Clear slot (delete audio)
~clip.clearSlot(0, 0);

// Stop all slots in channel 0
~clip.stopChannel(0);

// Stop all slots in all channels
~clip.stopAll;

// ===================================
// 6. Channel Mixing
// ===================================

// Set channel level (in dB)
~clip.setChannelLevel(0, -6);  // Channel 0 at -6dB

// Set channel pan (-1 = left, 0 = center, 1 = right)
~clip.setChannelPan(0, 0.5);   // Channel 0 panned right

// Mute/unmute channel
~clip.muteChannel(0);
// ~clip.unMuteChannel(0);  // (not exposed in SCClip, use grid directly)

// Solo channel
~clip.soloChannel(0);

// ===================================
// 7. Master Controls
// ===================================

// Set master output level
~clip.setMasterLevel(-3);  // -3dB

// Adjust master EQ (lo, mid, hi)
~clip.setMasterEQ(
	loFreq: 100,   loGain: 0,
	midFreq: 1000, midGain: 2, midQ: 1,
	hiFreq: 8000,  hiGain: -2
);

// Adjust master compressor
~clip.setMasterCompressor(
	thresh: -20,    // Threshold in dB
	ratio: 4,       // Compression ratio
	attack: 0.01,   // Attack time
	release: 0.1,   // Release time
	makeupGain: 6   // Makeup gain in dB
);

// ===================================
// 8. Custom Controller Settings
// ===================================

// You can customize the controller after connection:

// Change channel colors (must match Launchpad's palette)
~controller.channelColors = [
	\red_high,     // Channel 0
	\amber_high,   // Channel 1
	\yellow,       // Channel 2
	\green,        // Channel 3
	\red_mid,      // Channel 4
	\amber_low,    // Channel 5
	\yellow,       // Channel 6
	\green         // Channel 7
];

// Refresh all LEDs to reflect current state
~controller.updateAllLEDs;

// Change long press threshold (in seconds)
~controller.longPressThreshold = 1.5;  // Require a 1.5 s hold for long press

// Change loop length (hard-coded to 8 beats by default)
~controller.loopLengthBeats = 16;  // Use 16-beat loops

// ===================================
// 9. Troubleshooting
// ===================================

/*
If LEDs aren't updating:
- Check that blink routine is running: ~controller.blinkRoutine.isPlaying
- Manually start blink routine: ~controller.startBlinkRoutine;

If buttons aren't responding:
- Check MIDI connections: MIDIClient.sources;
- Verify device name matches: MIDIIn.findPort("Launchpad Mini", "Launchpad Mini");
- Restart connection: ~controller.disconnect; ~controller.connect("Launchpad Mini");

If audio isn't recording:
- Check input levels: s.meter;
- Verify audio interface is selected in SC
- Test manual recording: ~clip.armSlot(0, 0, 8); ~clip.launchSlot(0, 0);

Performance issues:
- Check CPU: s.avgCPU; s.peakCPU;
- Increase audio buffer size: s.options.blockSize = 256;
- Reduce number of channels: ~clip = SCClip.new(numChannels: 4);
*/

// ===================================
// 10. Cleanup
// ===================================

// Disconnect Launchpad (clears LEDs, stops routines)
~controller.disconnect;

// Shutdown SC-Clip (stops all synths, frees buffers)
~clip.shutdown;

// Quit server
s.quit;
