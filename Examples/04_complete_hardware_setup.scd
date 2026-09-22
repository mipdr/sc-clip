/*
 * SC-Clip Complete Hardware Setup Example
 *
 * This example demonstrates a complete production setup with:
 *   - 4 channels with explicit input mapping
 *   - 3 effects per channel (reverb, delay, distortion)
 *   - UMC404 audio interface (4 inputs -> 4 channels)
 *   - Launchpad Mini for clip triggering
 *   - TR-8S (4-channel) for level and effects control
 *
 * This is the recommended approach for building a complete SC-Clip rig.
 */

// ========================================================================
// Step 1: Initialize SC-Clip with Explicit Input Mapping
// ========================================================================

(
~projectDir = PathName(thisProcess.nowExecutingPath).pathOnly ++ "../";

// Create SC-Clip with 4 channels and explicit input mapping
// inputMapping: [0, 1, 2, 3] means:
//   - Channel 0 reads from hardware input 0
//   - Channel 1 reads from hardware input 1
//   - Channel 2 reads from hardware input 2
//   - Channel 3 reads from hardware input 3
~clip = SCClip.new(
	numChannels: 4,
	numSlots: 8,
	server: s,
	inputMapping: [0, 1, 2, 3]  // Explicit 1:1 mapping (this is also the default)
);

~clip.boot(
	options: (
		numBuffers: 1024 * 256,
		memSize: 8192 * 40,
		numWireBufs: 2048,
		maxNodes: 1024 * 32,
		numInputBusChannels: 4,   // Match number of hardware inputs
		numOutputBusChannels: 2,  // Stereo output
		sampleRate: 48000
	),
	action: {
		"=== SC-Clip Hardware Setup ===".postln;
		~clip.printStatus;

		// Proceed to Step 2: Add effects
		// (See below)
	}
);
)

// ========================================================================
// Step 2: Add Effects to All Channels
// ========================================================================

/*
 * Effects should be added BEFORE connecting MIDI controllers.
 * Each channel gets 3 effects in specific slots:
 *   - Slot 0: Reverb (controlled by TR-8S CTRL knob)
 *   - Slot 1: Delay (controlled by TR-8S DECAY knob)
 *   - Slot 2: Distortion (controlled by TR-8S TUNE knob)
 */

(
fork {
	// Wait for server to be ready
	s.sync;

	"Adding effects to all channels...".postln;

	4.do { |chanIdx|
		var channel = ~clip.grid.getChannel(chanIdx);

		// Effect slot 0: Reverb
		channel.addEffect(
			\channelReverb,
			[\mix, 0.3, \room, 0.5, \damp, 0.5],
			slot: 0
		);

		// Effect slot 1: Delay
		channel.addEffect(
			\channelDelay,
			[\mix, 0.0, \delayTime, 0.3, \decayTime, 2],
			slot: 1
		);

		// Effect slot 2: Distortion
		channel.addEffect(
			\channelDistortion,
			[\mix, 0.0, \drive, 0.5],
			slot: 2
		);
	};

	"Effects added to all 4 channels (3 per channel = 12 total)".postln;

	// Proceed to Step 3: Connect hardware
	// (See below)
};
)

// ========================================================================
// Step 3: Connect UMC404 Audio Interface
// ========================================================================

/*
 * The UMC404 provides:
 *   - 4 hardware inputs (already mapped to SC-Clip channels)
 *   - MIDI clock output
 *   - MIDI thru functionality
 */

(
~umc404 = ClipUMC404.new(~projectDir);
~umc404.connect(~clip);

// Enable MIDI thru (hardware MIDI IN -> MIDI OUT)
// This allows external MIDI controllers/synths to pass through
~umc404.enableMIDIThru;

// Enable MIDI clock output for syncing external gear
~midiOut = ~umc404.getMIDIOut;
if (~midiOut.notNil, {
	~clip.enableMIDIClock(~midiOut);
	"MIDI clock enabled via UMC404".postln;
});

"UMC404 audio interface connected".postln;
)

// ========================================================================
// Step 4: Connect Launchpad Mini Grid Controller
// ========================================================================

/*
 * The Launchpad Mini provides:
 *   - 4x8 grid for triggering clips
 *   - Visual feedback via LED colors
 *   - Short press: arm/launch/stop clips
 *   - Long press: clear and re-arm clips
 */

(
~launchpad = ~clip.connectController(
	controllerType: \launchpadMini,
	deviceName: "Launchpad Mini",  // Adjust if your device name differs
	numRows: 4,                     // 4 rows (matching 4 channels)
	numCols: 8                      // 8 columns (matching 8 slots)
);

if (~launchpad.notNil, {
	"Launchpad Mini connected (4x8 grid)".postln;
}, {
	"Failed to connect Launchpad Mini - check device name".warn;
});
)

// ========================================================================
// Step 5: Connect TR-8S MIDI Controller
// ========================================================================

/*
 * The TR-8S (4-channel configuration) provides:
 *   - 4 channel faders (LEVEL) -> SC-Clip channel levels
 *   - 12 knobs (3 per channel) -> Effect parameters
 *     * CTRL knob  -> Reverb mix (slot 0)
 *     * DECAY knob -> Delay mix (slot 1)
 *     * TUNE knob  -> Distortion mix (slot 2)
 *
 * TR-8S tracks used: CH, OH, CC, RC (rightmost 4 tracks)
 */

(
~tr8s = ClipTR8s4Channel.new(~clip);
~tr8s.connect;

"TR-8S 4-channel controller connected".postln;
)

// ========================================================================
// Complete Setup Verification
// ========================================================================

(
"".postln;
"=== Hardware Setup Complete ===".postln;
"Audio Interface:  UMC404HD (4 inputs mapped 1:1 to channels)".postln;
"Grid Controller:  Launchpad Mini (4x8 clip grid)".postln;
"MIDI Controller:  TR-8S 4Ch (levels + 3 effects per channel)".postln;
"MIDI Clock:       Enabled via UMC404".postln;
"MIDI Thru:        Enabled".postln;
"".postln;
"Total Channels:   4".postln;
"Slots per Channel: 8".postln;
"Effects per Channel: 3 (reverb, delay, distortion)".postln;
"".postln;
"Ready to record and perform!".postln;
"================================".postln;
)

// ========================================================================
// Example: Custom Input Mapping
// ========================================================================

/*
 * You can customize which hardware inputs map to which channels.
 * This is useful if your hardware setup doesn't match the default 1:1.
 *
 * Example: Swap inputs 0 and 1
 */

(
// ~clip = SCClip.new(
//     numChannels: 4,
//     numSlots: 8,
//     server: s,
//     inputMapping: [1, 0, 2, 3]  // Channel 0 <- Input 1, Channel 1 <- Input 0
// );
)

/*
 * Example: Use only 2 hardware inputs for 4 channels
 * (useful for stereo input pairs or duplicating inputs)
 */

(
// ~clip = SCClip.new(
//     numChannels: 4,
//     numSlots: 8,
//     server: s,
//     inputMapping: [0, 1, 0, 1]  // Channels 0,2 <- Input 0; Channels 1,3 <- Input 1
// );
)

// ========================================================================
// Example: Custom TR-8S Knob Mapping
// ========================================================================

/*
 * You can customize which TR-8S knobs control which effect parameters.
 * This must be done BEFORE calling ~tr8s.connect.
 */

(
// ~tr8s = ClipTR8s4Channel.new(~clip);
//
// // Custom knob mapping: control different parameters
// ~tr8s.setKnobMapping([
//     // CTRL knob -> reverb \room parameter instead of \mix
//     (knob: \ctrl, slot: 0, param: \room, range: [0.0, 1.0]),
//     // DECAY knob -> delay \decayTime parameter instead of \mix
//     (knob: \decay, slot: 1, param: \decayTime, range: [1.0, 5.0]),
//     // TUNE knob -> distortion \drive parameter instead of \mix
//     (knob: \tune, slot: 2, param: \drive, range: [0.0, 1.0])
// ]);
//
// ~tr8s.connect;
)

// ========================================================================
// Example: Custom TR-8S Channel Assignment
// ========================================================================

/*
 * You can customize which TR-8S tracks control which SC-Clip channels.
 * This must be done BEFORE calling ~tr8s.connect.
 */

(
// ~tr8s = ClipTR8s4Channel.new(~clip);
//
// // Swap channel assignments
// ~tr8s.setChannelAssignment(
//     chChannel: 3,  // CH track controls SC-Clip channel 3
//     ohChannel: 2,  // OH track controls SC-Clip channel 2
//     ccChannel: 1,  // CC track controls SC-Clip channel 1
//     rcChannel: 0   // RC track controls SC-Clip channel 0
// );
//
// ~tr8s.connect;
)

// ========================================================================
// Cleanup
// ========================================================================

(
~tr8s.disconnect;
~launchpad.disconnect;
~umc404.disconnect;
~clip.shutdown;
)

// ========================================================================
// Quick Reference: Hardware Control Layout
// ========================================================================

/*
LAUNCHPAD MINI (4x8 Grid):
┌────────────────────────────────────────┐
│ [0,0] [0,1] [0,2] [0,3] [0,4] [0,5] [0,6] [0,7] │  Channel 0
│ [1,0] [1,1] [1,2] [1,3] [1,4] [1,5] [1,6] [1,7] │  Channel 1
│ [2,0] [2,1] [2,2] [2,3] [2,4] [2,5] [2,6] [2,7] │  Channel 2
│ [3,0] [3,1] [3,2] [3,3] [3,4] [3,5] [3,6] [3,7] │  Channel 3
└────────────────────────────────────────┘
  Slot 0  1    2    3    4    5    6    7

TR-8S CONTROLLER (4 Channels):
┌──────────────────────────────────────┐
│ Track │ TUNE │ DECAY │ LEVEL │ CTRL │
├───────┼──────┼───────┼───────┼──────┤
│ CH    │ Dist │ Delay │ Level │ Verb │ -> SC-Clip Channel 0
│ OH    │ Dist │ Delay │ Level │ Verb │ -> SC-Clip Channel 1
│ CC    │ Dist │ Delay │ Level │ Verb │ -> SC-Clip Channel 2
│ RC    │ Dist │ Delay │ Level │ Verb │ -> SC-Clip Channel 3
└───────┴──────┴───────┴───────┴──────┘
        Slot 2  Slot 1    dB    Slot 0

UMC404 AUDIO INTERFACE:
┌────────────────────┐
│ Input 0 -> Chan 0  │
│ Input 1 -> Chan 1  │
│ Input 2 -> Chan 2  │
│ Input 3 -> Chan 3  │
└────────────────────┘
│ Output L/R (Main)  │
│ MIDI Clock Out     │
│ MIDI Thru (In->Out)│
└────────────────────┘
*/
