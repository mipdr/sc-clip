/*
 * Simple syntax validation test for sc-clip quark
 *
 * This script just tries to start sclang and print a message.
 * If the class library has syntax errors, sclang will fail to start.
 *
 * Run with: sclang scripts/test-syntax.scd
 */
(
"========================================".postln;
"sc-clip: syntax validation test".postln;
"========================================".postln;

// If we get here, at least sclang started
"sclang started successfully".postln;

// Try to check if any extensions are loaded
"Installed quarks and extensions:".postln;
Quarks.installed.do({ |q| ("  - " ++ q.name).postln });

"========================================".postln;
"sc-clip: syntax validation PASSED".postln;
"========================================".postln;
0.exit;
)
