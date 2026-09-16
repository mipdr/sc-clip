/*
 * Verifies SCClip and its ddwMixerChannel dependency resolve after a class
 * library recompile. Run via `make install`, as a fresh sclang launch after
 * scripts/install-deps.scd -- referencing these classes here is itself the
 * verification: if either failed to compile, sclang would already have
 * printed "Library has not been compiled successfully." above and this
 * script would never reach 0.exit (the Makefile wraps it in `timeout` so
 * that failure still ends the `make install` run instead of hanging).
 */
(
"sc-clip: verifying class library...".postln;
"  MixerChannel -> %".format(MixerChannel).postln;
"  SCClip -> %".format(SCClip).postln;
"sc-clip: install OK.".postln;
0.exit;
)
