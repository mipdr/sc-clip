/*
 * SC-Clip Command-Line Testing Guide
 *
 * This file demonstrates how to test SC-Clip using only the command line
 * (no MIDI controller needed). Perfect for initial testing and debugging.
 */

// ============================================================================
// PART 1: BOOT SERVER AND INITIALIZE
// ============================================================================

// Step 1: Configure and boot server
(
s.options.memSize = 8192 * 16;        // 128 MB for loop buffers
s.options.numBuffers = 2048;
s.options.blockSize = 128;            // ~3ms latency @ 48kHz
s.options.sampleRate = 48000;
s.options.numInputBusChannels = 4;    // 4 hardware inputs
s.options.numOutputBusChannels = 2;   // Stereo out

s.waitForBoot {
	"Server booted! Ready to initialize SC-Clip.".postln;
	s.meter;  // Show audio levels
};
)

// Step 2: Create and initialize SC-Clip
(
~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);
~clip.boot(action: {
	"SC-Clip initialized and ready!".postln;
	~clip.printStatus;
});
)

// ============================================================================
// PART 2: METRONOME TESTING
// ============================================================================

// Enable metronome (default volume)
~clip.enableMetronome;

// You should hear: tick-tick-tick-TICK (high pitch on downbeat)

// Adjust metronome volume (0.0 to 1.0)
~clip.setMetronomeVolume(0.5);  // Louder
~clip.setMetronomeVolume(0.1);  // Quieter

// Disable metronome
~clip.disableMetronome;

// Enable again for testing
~clip.enableMetronome(0.3);

// ============================================================================
// PART 3: TEMPO AND TIME SIGNATURE
// ============================================================================

// Change tempo
~clip.setTempo(90);   // Slower
~clip.setTempo(140);  // Faster
~clip.setTempo(120);  // Back to 120 BPM

// Change time signature
~clip.setTimeSignature(3, 4);  // 3/4 time (waltz)
// Listen: TICK-tick-tick-TICK-tick-tick

~clip.setTimeSignature(6, 8);  // 6/8 time
// Listen: TICK-tick-tick-tick-tick-tick-TICK...

~clip.setTimeSignature(4, 4);  // Back to 4/4
// Listen: TICK-tick-tick-tick-TICK...

// ============================================================================
// PART 4: TEST WITH INTERNAL AUDIO (NO HARDWARE SYNTH)
// ============================================================================

// Create a test tone generator to simulate hardware input
// This routes a simple sine wave to input channel 0
(
~testTone = {
	var freq = LFNoise1.kr(0.5).range(200, 800);  // Wandering pitch
	var sig = SinOsc.ar(freq) * 0.3;
	// Route to hardware input bus 0 (will be picked up by ClipChannel)
	ReplaceOut.ar(0, sig);
}.play(target: RootNode.new, addAction: \addToHead);
)

// Now you have audio going to "hardware input 0"

// ============================================================================
// PART 4B: RECORDING A REAL HARDWARE SYNTH (INSTEAD OF A TEST TONE)
// ============================================================================

// If you have a real synth (or any audio source) plugged into your audio
// interface, you can record it directly -- no test tone needed. ClipChannel
// already listens on its own hardware input (channelIndex N reads
// SoundIn.ar(N)), so a channel is "live" as soon as SC-Clip boots, as long
// as numInputBusChannels above covers it.

// Example: a synth plugged into a Behringer UMC404HD's line input #1.
// Interface inputs are usually labeled starting at 1, but SC's SoundIn.ar
// bus indices start at 0 -- so physical input #1 is SC input index 0,
// i.e. ClipChannel 0 (channelIndex 0 reads SoundIn.ar(0)). On Linux with
// JACK, this is the order a typical jackd setup auto-connects in
// (system:capture_1 -> scsynth's input bus 0); other OSes/drivers/routings
// may number inputs differently, so if in doubt, check with the meter below
// rather than assuming.

// Confirm signal is actually arriving on the input you expect before
// recording -- play/sing into the synth and watch for movement on input 0:
s.meter;

// No ~testTone needed this time -- channel 0 is already listening to
// physical input #1 live. Arm and record it exactly like PART 5 below:
~clip.armSlot(channelIndex: 0, slotIndex: 0, loopLengthBeats: 4);
~clip.launchSlot(0, 0);  // starts recording on the next bar

// Play your hardware synth into input #1 for 4 beats -- recording will
// automatically switch to playback once the loop length is reached.

~clip.printStatus;
~clip.stopSlot(0, 0);

// ============================================================================
// PART 5: RECORDING YOUR FIRST LOOP
// ============================================================================

// Arm slot 0 on channel 0 for a 4-beat loop
~clip.armSlot(channelIndex: 0, slotIndex: 0, loopLengthBeats: 4);

// Launch recording (will start on next bar)
// The metronome helps you hear when recording starts!
~clip.launchSlot(0, 0);

// Wait 4 beats... recording will automatically switch to playback
// You should hear the test tone looping

// Check status
~clip.printStatus;

// Stop the loop
~clip.stopSlot(0, 0);

// Play it again
~clip.launchSlot(0, 0);

// ============================================================================
// PART 6: MULTIPLE LOOPS
// ============================================================================

// Create a second test tone (different pitch)
(
~testTone2 = {
	var freq = LFNoise1.kr(0.3).range(400, 1200);
	var sig = Pulse.ar(freq, 0.5) * 0.2;
	ReplaceOut.ar(1, sig);  // Route to input 1
}.play(target: RootNode.new, addAction: \addToHead);
)

// Record on channel 1, slot 0 (8-beat loop this time)
~clip.armSlot(1, 0, 8);
~clip.launchSlot(1, 0);
// Wait 8 beats...

// Now you have two loops playing!
~clip.printStatus;

// Record another clip on channel 0, slot 1 (2-beat loop)
~clip.armSlot(0, 1, 2);
~clip.launchSlot(0, 1);

// Stop individual clips
~clip.stopSlot(0, 0);
~clip.stopSlot(1, 0);

// Stop all clips at once
~clip.stopAll;

// ============================================================================
// PART 7: OVERDUBBING
// ============================================================================

// Record a base loop
~clip.armSlot(0, 0, 4);
~clip.launchSlot(0, 0);
// Wait for recording to finish...

// Now overdub on top of it
~clip.overdubSlot(0, 0);
// The test tone will layer on the existing loop

// Stop overdubbing (keeps playing)
~clip.grid.channels[0].getSlot(0).stopOverdub;

// ============================================================================
// PART 8: MIXING AND EFFECTS
// ============================================================================

// Adjust channel levels
~clip.setChannelLevel(0, -6);  // Channel 0: -6 dB
~clip.setChannelLevel(1, -3);  // Channel 1: -3 dB

// Pan channels
~clip.setChannelPan(0, -0.7);  // Pan left
~clip.setChannelPan(1, 0.7);   // Pan right

// Adjust master level
~clip.setMasterLevel(-3);  // Master: -3 dB

// Adjust master EQ
~clip.setMasterEQ(
	loFreq: 100, loGain: 3,      // Boost bass
	midFreq: 1000, midGain: 0,
	hiFreq: 8000, hiGain: -2     // Cut highs slightly
);

// Adjust master compressor
~clip.setMasterCompressor(
	thresh: -12,
	ratio: 3,
	attack: 0.01,
	release: 0.3,
	makeupGain: 2
);

// ============================================================================
// PART 9: QUANTIZATION TESTING
// ============================================================================

// Try different quantization settings

// 1 beat quantization (tight, immediate feel)
~clip.setQuantization(1);
~clip.armSlot(0, 2, 4);
~clip.launchSlot(0, 2);  // Starts on next beat

// 1 bar quantization (looser, more musical)
~clip.setQuantization(4);
~clip.armSlot(0, 3, 4);
~clip.launchSlot(0, 3);  // Starts on next bar

// No quantization (immediate)
~clip.setQuantization(0);
~clip.armSlot(0, 4, 4);
~clip.launchSlot(0, 4);  // Starts immediately!

// Reset to 1 bar
~clip.setQuantization(4);

// ============================================================================
// PART 10: SCENE LAUNCHING
// ============================================================================

// Arm multiple slots at the same index across channels
~clip.armSlot(0, 0, 4);
~clip.armSlot(1, 0, 4);
~clip.armSlot(2, 0, 4);

// Launch the scene (all slot 0's at once)
~clip.launchScene(0);
// All three channels start recording simultaneously!

// Stop the scene
~clip.grid.stopScene(0);

// ============================================================================
// PART 11: ADVANCED TIMING
// ============================================================================

// Get current beat
~clip.transport.beat;

// Get next bar
~clip.transport.nextBar;

// Schedule a custom action
~clip.transport.scheduleAtBeat(~clip.transport.nextBar + 4, {
	"This message appears 1 bar from now!".postln;
});

// Schedule immediate action (no latency)
~clip.transport.scheduleImmediate({
	"This happens right now!".postln;
});

// ============================================================================
// PART 12: STATUS AND DEBUGGING
// ============================================================================

// Print full status
~clip.printStatus;

// Get all playing slots
~clip.grid.getAllPlayingSlots;

// Get all recording slots
~clip.grid.getAllRecordingSlots;

// Check a specific slot state
~clip.grid.getSlot(0, 0).state;

// Check loop length
~clip.grid.getSlot(0, 0).loopLengthBeats;

// View node tree (see all synths)
s.plotTree;

// Check CPU usage
s.avgCPU;
s.peakCPU;

// ============================================================================
// PART 13: CLEANUP
// ============================================================================

// Stop test tones
~testTone.free;
~testTone2.free;

// Disable metronome
~clip.disableMetronome;

// Stop all clips
~clip.stopAll;

// Shutdown SC-Clip
~clip.shutdown;

// Free all server resources
s.freeAll;

// ============================================================================
// QUICK REFERENCE - COMMON COMMANDS
// ============================================================================

/*

// METRONOME
~clip.enableMetronome(0.3);         // Enable with volume
~clip.disableMetronome;              // Disable
~clip.setMetronomeVolume(0.5);       // Adjust volume

// TEMPO
~clip.setTempo(120);                 // Set BPM
~clip.setTimeSignature(4, 4);        // Set time signature

// RECORDING
~clip.armSlot(channel, slot, beats); // Arm for recording
~clip.launchSlot(channel, slot);     // Start recording/playing
~clip.stopSlot(channel, slot);       // Stop
~clip.clearSlot(channel, slot);      // Delete loop

// OVERDUB
~clip.overdubSlot(channel, slot);    // Start overdubbing
~clip.grid.channels[channel].getSlot(slot).stopOverdub;  // Stop overdub

// MIXING
~clip.setChannelLevel(channel, db);  // Set channel level
~clip.setChannelPan(channel, pos);   // Pan (-1 to 1)
~clip.setMasterLevel(db);            // Set master level

// QUANTIZATION
~clip.setQuantization(4);            // 1 bar
~clip.setQuantization(1);            // 1 beat
~clip.setQuantization(0);            // Immediate

// SCENES
~clip.launchScene(slotIndex);        // Launch all slots at index
~clip.grid.stopScene(slotIndex);     // Stop all slots at index
~clip.stopAll;                       // Stop everything

// STATUS
~clip.printStatus;                   // Print full status
~clip.transport.beat;                // Current beat
s.plotTree;                          // View synth tree
s.avgCPU;                            // CPU usage

*/

// ============================================================================
// TIPS FOR COMMAND-LINE TESTING
// ============================================================================

/*

1. USE THE METRONOME!
   - Always enable it when testing timing
   - The downbeat (high pitch) helps you know where the bar starts
   - Critical for understanding quantization behavior

2. TEST TONES
   - Use the test tone generators above to simulate hardware synths
   - Change frequencies/waveforms to distinguish different "synths"
   - Route different tones to different input channels

3. PRINT STATUS OFTEN
   - ~clip.printStatus; shows everything at a glance
   - Helps debug what's armed, recording, or playing

4. START SIMPLE
   - Test one clip at a time first
   - Then try multiple clips on one channel
   - Then try multiple channels
   - Then try scenes

5. WATCH THE POST WINDOW
   - SC-Clip logs all state changes
   - Look for "ClipSlot[channel,slot]: state -> newState"
   - Helps understand what's happening when

6. USE DIFFERENT LOOP LENGTHS
   - 2 beats: fast phrases
   - 4 beats: standard loops
   - 8 beats: longer phrases
   - Try polyrhythms: 3 vs 4 vs 5 beats!

7. TIMING TESTS
   - Record with metronome
   - Let loops play for a minute
   - Check if they stay in sync (they should!)
   - This validates the sample-based loop length calculation

8. EXPERIMENT WITH QUANTIZATION
   - 1 bar = musical, loose timing
   - 1 beat = tight, responsive
   - 0 (immediate) = no waiting
   - Feel the difference!

*/
