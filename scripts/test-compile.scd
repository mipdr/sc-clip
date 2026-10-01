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

// Test dependencies
"Testing dependencies...".postln;
"  MixerChannel -> %".format(MixerChannel).postln;

// Test new classes (skip GUI classes in headless mode)
"Testing meter classes...".postln;
"  ClipLevelMeter -> %".format(ClipLevelMeter).postln;

// Test MIDI classes
"Testing MIDI classes...".postln;
"  ClipMIDIChannel -> %".format(ClipMIDIChannel).postln;
"  ClipMIDISlot -> %".format(ClipMIDISlot).postln;
"  ClipMIDINotationParser -> %".format(ClipMIDINotationParser).postln;

// Note: ClipMasteringGUI is skipped in headless mode as it requires a display

"========================================".postln;
"sc-clip: compilation test PASSED".postln;
"========================================".postln;

// Explicitly quit the server before exiting sclang to avoid CI timeout
// The -D flag boots the server, which can prevent sclang from exiting cleanly
Server.killAll;
// Small delay to allow server processes to terminate
{ 0.exit }.defer(0.2);
)
