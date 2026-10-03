/*
 * SC-Clip Input-to-Channel Mapping Example
 *
 * Demonstrates how to use dynamic input-to-channel mapping to record
 * different musical parts from the same hardware synth into separate
 * channels with independent effects and mixing.
 *
 * Use case: Recording both bass and pads from a Prophet-600 connected
 * to input 0, with different effects for each part.
 */

// ===== SETUP =====

// Boot SC-Clip with 4 channels
(
~clip = SCClip.new(numChannels: 4, numSlots: 8);
~clip.boot({
	"SC-Clip ready! Default mapping: input 0→ch0, input 1→ch1, etc.".postln;
});
)

// ===== EXAMPLE 1: Record Bass and Pads from Same Synth =====

/*
 * Scenario: Prophet-600 is connected to hardware input 0.
 * We want to record bass on channel 0 and pads on channel 1,
 * with different effects for each.
 */

// Set up Channel 0 for BASS
(
// Map input 0 (Prophet-600) to channel 0
~clip.setChannelInput(0, 0);

// Add bass-appropriate effects
~clip.channels[0].addEffect(\channelBoum, [\drive, 6, \type, 2]);  // Fuzz distortion
~clip.channels[0].setLevel(-3);  // Slightly lower level
~clip.channels[0].setPan(-0.2);  // Slightly left

"Channel 0: Ready for BASS (input 0, fuzz + compressed)".postln;
)

// Record bass line on channel 0, slot 0
(
~clip.armSlot(0, 0, loopLengthBeats: 4);  // 4 beats
~clip.launchSlot(0, 0);  // Start recording - play your bass line!
)

// Now set up Channel 1 for PADS
(
// Map input 0 (same Prophet-600) to channel 1
~clip.setChannelInput(1, 0);

// Add pad-appropriate effects
~clip.channels[1].addEffect(\channelReverb, [\mix, 0.6, \room, 0.8]);  // Spacious reverb
~clip.channels[1].setLevel(-6);  // Lower level for pads
~clip.channels[1].setPan(0.2);  // Slightly right

"Channel 1: Ready for PADS (input 0, reverb)".postln;
)

// Record pad part on channel 1, slot 0
(
~clip.armSlot(1, 0, loopLengthBeats: 8);  // 8 beats
~clip.launchSlot(1, 0);  // Start recording - play your pad chords!
)

// Note: Channel 0 now has NO input (it was reassigned to channel 1)
// This is temporary - you can always reassign it back

// Both loops are now playing with independent effects!
// Bass has fuzz, pads have reverb, despite coming from the same synth

// ===== EXAMPLE 2: Switch Inputs Between Channels =====

/*
 * Scenario: You have multiple synths and want to quickly route them
 * to different channels for recording.
 */

// Input 0: Prophet-600 (bass synth)
// Input 1: Juno-106 (lead synth)

// Map Prophet to channel 0 for bass
~clip.setChannelInput(0, 0);

// Map Juno to channel 2 for lead
~clip.setChannelInput(2, 1);

// Now swap them
~clip.setChannelInput(0, 1);  // Juno → channel 0
~clip.setChannelInput(2, 0);  // Prophet → channel 2

// ===== EXAMPLE 3: Temporarily Disconnect a Channel =====

/*
 * If you want a channel to not receive any input (useful during
 * playback-only mode or when reorganizing your setup):
 */

// Disconnect channel 1 from all inputs
~clip.setChannelInput(1, nil);

// Verify
~clip.getChannelInput(1);  // Returns nil

// Reconnect later
~clip.setChannelInput(1, 0);

// ===== EXAMPLE 4: Query Current Mappings =====

// Check which input each channel is using
(
"Current input-to-channel mapping:".postln;
4.do { |chanIdx|
	var input = ~clip.getChannelInput(chanIdx);
	"  Channel % → Input %".format(chanIdx, input ?? "none").postln;
};
)

// ===== EXAMPLE 5: Session Save/Load Preserves Mappings =====

// Set up a specific routing configuration
(
~clip.setChannelInput(0, 1);  // Input 1 → Channel 0
~clip.setChannelInput(1, 0);  // Input 0 → Channel 1
~clip.setChannelInput(2, 2);  // Input 2 → Channel 2
~clip.setChannelInput(3, nil); // Channel 3 disconnected
)

// Save session
~clip.saveAs(Platform.userAppSupportDir +/+ "SC-Clip/MyRoutingSession");

// Later, load it back - all input mappings are restored
~clip = SCClip.load(Platform.userAppSupportDir +/+ "SC-Clip/MyRoutingSession");

// Verify mappings were restored
(
"Restored input mappings:".postln;
4.do { |i|
	var input = ~clip.getChannelInput(i);
	"  Channel % → Input %".format(i, input ?? "none").postln;
};
)

// ===== EXAMPLE 6: Launchpad Mini UI =====

/*
 * If you have a Launchpad Mini connected, the input mapping UI
 * is available on rows 6-7, columns 0-3:
 *
 * Row 7 (cols 0-3): Input selector
 *   - Press a pad to select which input to configure
 *   - Selected input lights ORANGE, others GREEN
 *
 * Row 6 (cols 0-3): Channel selector
 *   - After selecting an input, press a pad to map it to that channel
 *   - Mapped channel lights ORANGE, others GREEN
 *
 * Example flow:
 *   1. Press row 7, col 1 → Select input 1
 *   2. Row 6 now shows current mapping for input 1
 *   3. Press row 6, col 2 → Map input 1 to channel 2
 *   4. Input 1 is now routed to channel 2
 */

// Connect Launchpad (if available)
(
~launchpad = ClipLaunchpadMini.new(
	grid: ~clip.grid,
	transport: ~clip.transport,
	numRows: 6,         // 6 clip rows
	numCols: 6,         // 4 audio + 2 MIDI channels
	numAudioChannels: 4 // 4 audio channels for input mapping
);
~launchpad.connect("Launchpad Mini");
)

// ===== TIPS =====

/*
 * 1. Independent Effects: Even though multiple channels read from the
 *    same input, each channel can have completely different effects,
 *    levels, and panning.
 *
 * 2. Sequential Recording: You can't record to multiple channels from
 *    the same input simultaneously. Record one part, then reassign the
 *    input and record another part.
 *
 * 3. Live Performance: During playback, you can reassign inputs in
 *    real-time to route live synths to different effect chains.
 *
 * 4. Temporary Setup: Disconnecting a channel (set to nil) is useful
 *    when you want to free up an input for another channel temporarily.
 *
 * 5. Default Behavior: On startup, input i → channel i. This matches
 *    the traditional multi-track recorder behavior.
 */

// ===== CLEANUP =====

// Shutdown
~clip.shutdown;

// Disconnect Launchpad
~launchpad !? { ~launchpad.disconnect };
