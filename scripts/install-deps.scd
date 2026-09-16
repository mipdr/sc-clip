/*
 * Installs sc-clip's Quark dependencies (currently just ddwMixerChannel,
 * for MixerChannel) if not already present. Idempotent -- safe to re-run.
 *
 * Run via `make install`, not directly: that target also symlinks this repo
 * into the SuperCollider Extensions dir first, and separately relaunches
 * sclang afterwards to actually verify SCClip resolves (Quarks.install only
 * registers the new class-file search path for the *next* class-library
 * compile -- it doesn't recompile the currently running process).
 */
(
if (Quarks.isInstalled("ddwMixerChannel").not, {
	"sc-clip: installing ddwMixerChannel dependency...".postln;
	Quarks.install("ddwMixerChannel");
}, {
	"sc-clip: ddwMixerChannel already installed.".postln;
});
0.exit;
)
