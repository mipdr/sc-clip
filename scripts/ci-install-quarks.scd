/*
 * CI Quark Installation Script
 *
 * Installs ddwMixerChannel quark and recompiles class library.
 * Used by GitHub Actions CI.
 */
(
var done = false;

fork {
  try {
    "Installing ddwMixerChannel...".postln;

    // Install quark
    Quarks.install("ddwMixerChannel");

    // Wait for installation
    2.wait;

    "Quark installed, recompiling class library...".postln;

    // Recompile is needed for quarks to be available
    thisProcess.recompile;

    2.wait;

    "Installation complete".postln;
    done = true;
    0.exit;
  } {|err|
    ("ERROR: " ++ err).postln;
    err.errorString.postln;
    done = true;
    1.exit;
  };
};

// Timeout after 60 seconds
SystemClock.sched(60, {
  if (done.not) {
    "ERROR: Installation timed out".postln;
    1.exit;
  };
});
)
