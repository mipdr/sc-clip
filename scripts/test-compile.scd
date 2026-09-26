/*
 * Compilation test for sc-clip quark
 *
 * This script verifies that all sc-clip classes compile successfully
 * by attempting to reference them. If compilation fails, sclang will
 * print an error and exit with a non-zero status.
 *
 * Expected to run in a headless environment (CI) with:
 *   QT_QPA_PLATFORM=offscreen sclang -D scripts/test-compile.scd
 */
(
"========================================".postln;
"sc-clip: compilation test".postln;
"========================================".postln;

// Test core classes
"Testing core classes...".postln;
"  SCClip -> %".format(SCClip).postln;
"  ClipGrid -> %".format(ClipGrid).postln;
"  ClipChannel -> %".format(ClipChannel).postln;
"  ClipSlot -> %".format(ClipSlot).postln;
"  ClipTransport -> %".format(ClipTransport).postln;

// Test bus and audio classes
"Testing audio classes...".postln;
"  ClipMasterBus -> %".format(ClipMasterBus).postln;
"  ClipSynthDefs -> %".format(ClipSynthDefs).postln;

// Test controller classes
"Testing controller classes...".postln;
"  ClipGridController -> %".format(ClipGridController).postln;
"  ClipMIDIController -> %".format(ClipMIDIController).postln;
"  ClipMIDIClock -> %".format(ClipMIDIClock).postln;
"  ClipLaunchpadMini -> %".format(ClipLaunchpadMini).postln;
"  ClipTR8s4Channel -> %".format(ClipTR8s4Channel).postln;
"  ClipTR8s3Channel -> %".format(ClipTR8s3Channel).postln;

// Test dependency
"Testing dependencies...".postln;
"  MixerChannel -> %".format(MixerChannel).postln;

"========================================".postln;
"sc-clip: compilation test PASSED".postln;
"========================================".postln;
0.exit;
)
