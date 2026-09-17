/*
 * SC-Clip Basic Setup Example
 *
 * Demonstrates:
 * - Server configuration
 * - System initialization
 * - Manual clip recording and playback (no MIDI controller)
 * - Basic transport and mixing controls
 */

// ===== Step 1: Configure and boot server =====

(
s.options.memSize = 8192 * 16;        // 128 MB for loop buffers
s.options.numBuffers = 2048;          // Plenty of buffers
s.options.blockSize = 128;            // ~3ms latency @ 48kHz
s.options.sampleRate = 48000;
s.options.numInputBusChannels = 4;    // 4 hardware inputs
s.options.numOutputBusChannels = 2;   // Stereo out

s.waitForBoot {
	"Server booted!".postln;
	s.meter;  // Optional: show audio levels
};
)

// ===== Step 2: Create SC-Clip instance =====

(
// Create with 4 channels, 8 slots per channel
~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);

// Initialize (creates transport, master bus, grid)
~clip.boot(action: {
	"SC-Clip ready!".postln;
	~clip.printStatus;
});
)

// ===== Step 3: Set tempo and quantization =====

(
~clip.setTempo(120);              // 120 BPM
~clip.setTimeSignature(4, 4);     // 4/4 time
~clip.setQuantization(4);         // Quantize to 1 bar (4 beats)
)

// ===== Step 4: Record a loop =====

// Arm slot 0 on channel 0 for a 4-beat loop
~clip.armSlot(channelIndex: 0, slotIndex: 0, loopLengthBeats: 4);

// Launch recording (will start on next bar boundary)
// Play your hardware synth for 4 beats after this!
~clip.launchSlot(0, 0);

// Check status while recording
~clip.printStatus;

// Recording will automatically transition to playback after 4 beats

// ===== Step 5: Control playback =====

// Stop the loop (quantized to next bar)
~clip.stopSlot(0, 0);

// Play it again
~clip.launchSlot(0, 0);

// Clear the slot (deletes the loop)
~clip.clearSlot(0, 0);

// ===== Step 6: Multiple clips =====

(
// Arm multiple slots
~clip.armSlot(0, 0, 4);  // Channel 0, slot 0, 4 beats
~clip.armSlot(0, 1, 8);  // Channel 0, slot 1, 8 beats
~clip.armSlot(1, 0, 4);  // Channel 1, slot 0, 4 beats

// Launch them one by one (or all at once)
~clip.launchSlot(0, 0);
~clip.launchSlot(0, 1);
~clip.launchSlot(1, 0);
)

// Stop all
~clip.stopAll;

// ===== Step 7: Overdubbing =====

// Start overdubbing on an existing loop
~clip.overdubSlot(0, 0);

// Play more notes on your synth, they'll layer on the loop

// Stop overdubbing (keeps playing)
~clip.grid.channels[0].getSlot(0).stopOverdub;

// ===== Step 8: Mixing controls =====

// Set channel levels
~clip.setChannelLevel(0, -6);     // Channel 0 to -6 dB
~clip.setChannelLevel(1, -3);     // Channel 1 to -3 dB

// Pan channels
~clip.setChannelPan(0, -0.5);     // Pan channel 0 left
~clip.setChannelPan(1, 0.5);      // Pan channel 1 right

// Solo/mute
~clip.soloChannel(0);               // Solo channel 0
~clip.unSoloChannel(0);             // Un-solo

~clip.muteChannel(1);              // Mute channel 1
~clip.grid.channels[1].unMute;     // Un-mute

// Master level
~clip.setMasterLevel(-3);          // Master to -3 dB

// ===== Step 9: Master effects =====

// Adjust master EQ (frequencies in Hz, gains in dB)
~clip.setMasterEQ(
	loFreq: 100, loGain: 2,        // Boost lows
	midFreq: 1000, midGain: 0, midQ: 1,
	hiFreq: 8000, hiGain: -1       // Cut highs
);

// Adjust master compressor
~clip.setMasterCompressor(
	thresh: -12,                    // Threshold in dB
	ratio: 3,                       // 3:1 ratio
	attack: 0.01,                   // 10ms attack
	release: 0.3,                   // 300ms release
	makeupGain: 2                   // +2 dB makeup gain
);

// Adjust limiter ceiling
~clip.setMasterLimiter(ceiling: -0.5);  // -0.5 dB ceiling

// ===== Step 10: Scene launch =====

// Arm a scene (same slot across multiple channels)
(
~clip.armSlot(0, 0, 4);
~clip.armSlot(1, 0, 4);
~clip.armSlot(2, 0, 4);
)

// Launch the scene (slot 0 across all armed channels)
~clip.launchScene(0);

// Stop the scene
~clip.grid.stopScene(0);

// ===== Step 11: Advanced timing =====

// Get current beat
~clip.transport.beat;

// Get next bar boundary
~clip.transport.nextBar;

// Schedule something at a specific beat
~clip.transport.scheduleAtBeat(~clip.transport.nextBar + 8, {
	"This will happen 2 bars from now!".postln;
});

// ===== Step 12: Sync options =====

// Use Ableton Link (if available)
// ~clip.enableLink;

// Use MIDI clock (not yet implemented)
// ~clip.enableMIDIClock(port);

// Switch back to internal clock
~clip.useInternalClock;

// ===== Step 13: Status and debugging =====

// Print full grid status
~clip.printStatus;

// Check what's playing
~clip.grid.getAllPlayingSlots;

// Check what's recording
~clip.grid.getAllRecordingSlots;

// Plot node tree (see signal flow)
s.plotTree;

// ===== Step 14: Cleanup =====

// Shutdown the system
~clip.shutdown;

// Free server resources
s.freeAll;

/*
 * TIPS:
 *
 * 1. Quantization: Change ~clip.setQuantization(beats) to 1 for tighter timing,
 *    or 0 for immediate (no quantization).
 *
 * 2. Loop lengths: Can be any number of beats (doesn't have to match quantization).
 *    Try 3, 5, 7 beats for polyrhythmic patterns.
 *
 * 3. Multiple takes: Record to different slots to keep multiple versions.
 *    E.g., slots 0-3 could be 4 different bass lines.
 *
 * 4. Latency: If you hear timing issues, adjust s.latency (default 0.2).
 *    Lower = more responsive but less sample-accurate.
 *    Higher = more accurate but feels delayed.
 *
 * 5. Effects: Per-channel effects use ddwMixerChannel's playFx method.
 *    Example: ~clip.grid.channels[0].addEffect(\reverb, [\room: 0.5], 0);
 *
 * 6. Buffer memory: If you run out of memory, increase s.options.memSize.
 *    Each 4-bar loop @ 120 BPM uses ~1.5 MB.
 */
