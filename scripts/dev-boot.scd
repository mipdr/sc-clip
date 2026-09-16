/*
 * SC-Clip generic dev boot.
 *
 * Hardware-agnostic interactive sclang session for testing SCClip on its
 * own: default server options, no JACK/UMC404 or other rig-specific setup.
 * Deliberately generic -- this is NOT where real rig integration goes. For
 * the actual performance rig, see ~/music/config/sc-clip-startup.scd and
 * `make start-clip` in that project instead.
 *
 * Run via `make start`. Stays open afterwards for interactive testing, same
 * as Examples/00_command_line_test.scd.
 */
(
s.waitForBoot {
	~clip = SCClip.new(numChannels: 4, numSlots: 8, server: s);
	~clip.boot(action: {
		"SC-Clip dev boot ready (4 channels x 8 slots, default server options)".postln;
		~clip.printStatus;
	});
};
)
