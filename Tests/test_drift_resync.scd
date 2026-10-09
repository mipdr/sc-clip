/*
 * Test: Drift Resync
 *
 * This test demonstrates the automatic drift correction mechanism that keeps
 * audio clips synchronized with the metronome over long playback sessions.
 *
 * Audio clips drift over time because:
 * - Loop length is calculated in samples once and cached
 * - Fractional samples are truncated (e.g., 90708.66 → 90708)
 * - This small error accumulates over many loop iterations
 *
 * The resync mechanism:
 * - Automatically restarts the PlayBuf every N loops (default: 8)
 * - Realigns the clip with the TempoClock (metronome source of truth)
 * - Uses 1ms attack crossfade to minimize audible artifacts
 *
 * Test procedure:
 * 1. Record a 4-beat loop at an odd tempo (127 BPM creates rounding error)
 * 2. Enable metronome to hear the reference beat
 * 3. Let it play for 100+ loops (several minutes)
 * 4. Listen for drift - with resync enabled, it stays locked
 * 5. Disable resync and hear drift accumulate
 */

(
// Boot server if needed
s.waitForBoot({
	var grid, transport, channel, slot;

	// Create transport at 127 BPM (creates sample rounding error)
	transport = ClipTransport.new(tempo: 127, beatsPerBar: 4, server: s);

	// Create grid with one channel
	grid = ClipGrid.new(
		numChannels: 1,
		numSlots: 1,
		transport: transport,
		server: s
	);

	// Enable metronome to hear the reference beat
	transport.enableMetronome(0.2);

	channel = grid.channels[0];
	slot = channel.slots[0];

	// Configure resync interval (default is 8 loops)
	slot.resyncInterval = 8;  // Resync every 8 loops

	"".postln;
	"=== DRIFT RESYNC TEST ===".postln;
	"Tempo: 127 BPM (creates sample rounding error)".postln;
	"Resync interval: % loops".format(slot.resyncInterval).postln;
	"".postln;

	// Calculate expected drift
	{
		var loopLengthBeats = 4;
		var tempo = 127;
		var sampleRate = s.sampleRate;
		var exactSamples = loopLengthBeats * 60 / tempo * sampleRate;
		var truncatedSamples = exactSamples.asInteger;
		var errorPerLoop = exactSamples - truncatedSamples;
		var errorMs = errorPerLoop / sampleRate * 1000;

		"Expected drift per loop: % samples = % ms".format(
			errorPerLoop.round(0.001),
			errorMs.round(0.001)
		).postln;

		[10, 50, 100, 200].do { |numLoops|
			var accumulatedMs = errorMs * numLoops;
			"After % loops: % ms".format(numLoops, accumulatedMs.round(0.001)).postln;
		};

		"".postln;
		"With resync every % loops, max drift is ~% ms".format(
			slot.resyncInterval,
			(errorMs * slot.resyncInterval).abs.round(0.001)
		).postln;
	}.value;

	"".postln;
	"".postln;
	"INSTRUCTIONS:".postln;
	"1. Play some audio to record a 4-beat loop".postln;
	"2. Arm the slot: channel.slots[0].arm(4)".postln;
	"3. Trigger recording: channel.recordSlot(0)".postln;
	"4. Listen for several minutes - it should stay locked to metronome".postln;
	"5. To test without resync: slot.resyncInterval = 0".postln;
	"6. Stop and restart playback to hear drift accumulate".postln;
	"".postln;

	// Store references for later use
	~grid = grid;
	~transport = transport;
	~channel = channel;
	~slot = slot;

	"Ready! Grid stored in ~grid, transport in ~transport".postln;
});
)


// === MANUAL TESTING COMMANDS ===

// Arm slot for 4-beat recording
~channel.slots[0].arm(4);

// Trigger recording (will start on next bar)
~channel.recordSlot(0);

// After recording completes, it will start playing and resyncing automatically

// Check current resync interval
~slot.resyncInterval;

// Disable resync to hear drift
~slot.resyncInterval = 0;

// Re-enable with different interval
~slot.resyncInterval = 4;  // Resync every 4 loops (more frequent)

// Stop playback
~channel.stopSlot(0);

// Restart playback
~channel.playSlot(0);

// Disable metronome
~transport.disableMetronome;

// Re-enable metronome
~transport.enableMetronome(0.2);

// Cleanup
~grid.free;
~transport.free;
