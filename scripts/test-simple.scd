// Simple test to verify sc-clip classes are loaded
(
"========================================".postln;
"sc-clip: simple class loading test".postln;
"========================================".postln;

// Just check if classes exist
"Testing if SCClip class exists...".postln;
if (SCClip.notNil, {
    "OK SCClip class found!".postln;
}, {
    "ERROR: SCClip class NOT found!".postln;
    1.exit;
});

"========================================".postln;
"sc-clip: simple test PASSED".postln;
"========================================".postln;

// Exit successfully
0.exit;
)
