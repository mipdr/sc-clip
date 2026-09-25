# SC-Clip Debug Logging

Debug logging system for troubleshooting unexpected behaviors in SC-Clip, including:
- Orphan loops that keep sounding
- Infinite recording/playing loops
- State transition issues
- Synth lifecycle problems

## Quick Start

```supercollider
// Create and boot SCClip
~clip = SCClip.new(numChannels: 4, numSlots: 8);
~clip.boot;

// Enable debug logging with normal verbosity
~clip.enableDebug(\normal);

// Use sc-clip normally...
~clip.armSlot(0, 0, 4);
~clip.launchSlot(0, 0);

// Check for orphan synths
~clip.checkForOrphans;

// Print summary of what happened
~clip.printDebugSummary;

// Save full log to file for sharing with others
~clip.saveDebugLog("~/my-debug-session.log");

// Disable debug logging when done
~clip.disableDebug;
```

## Log Levels

The debug logger supports three verbosity levels:

### `\minimal`
Only critical events and warnings:
- System enable/disable messages
- Errors and warnings
- Orphan synth detection

**Use case**: Minimal overhead, only see problems

### `\normal` (default)
Common debugging information:
- All minimal level events
- State transitions
- Recording start/stop
- Playback start/stop

**Use case**: General troubleshooting, understanding what's happening

### `\verbose`
Maximum detail:
- All normal level events
- Synth creation/freeing with full details
- Buffer allocation/deallocation
- Scheduled events (quantized actions)

**Use case**: Deep debugging, finding timing issues, tracking synth lifecycle

## API Reference

### Enabling/Disabling

```supercollider
// Enable with default (normal) level
~clip.enableDebug;

// Enable with specific level
~clip.enableDebug(\minimal);
~clip.enableDebug(\normal);
~clip.enableDebug(\verbose);

// Disable
~clip.disableDebug;
```

### Viewing Logs

```supercollider
// Print full history
~clip.printDebugHistory;

// Print last N entries
~clip.printDebugHistory(50);

// Print summary statistics
~clip.printDebugSummary;
```

### Orphan Detection

```supercollider
// Check for orphan synths (still running but not tracked)
~clip.checkForOrphans;

// View all currently tracked active synths
~clip.printActiveSynths;
```

### Saving Logs

```supercollider
// Save to default location (~/sc-clip-debug-TIMESTAMP.log)
~clip.saveDebugLog;

// Save to specific path
~clip.saveDebugLog("~/Desktop/my-session-debug.log");
```

### Managing Logs

```supercollider
// Clear accumulated history (useful for long sessions)
~clip.clearDebugLog;
```

## Log Output Format

Each log entry shows:
```
[TIME] [CATEGORY] MESSAGE | DETAILS
```

Example:
```
[  12.345s] [SLOT      ] Slot[0,0] state: armed -> recording | buffer: 42, playerSynth: nil, recorderSynth: 1001
[  12.346s] [SYNTH     ] CREATED clipRecorder for slot[0,0] (nodeID: 1001) | args: [inBus, 16, bufnum, 42, loop, 1, mode, 0]
[  12.347s] [RECORD    ] Recording START on slot[0,0] | loop: 4 beats, bufnum: 42
[  12.348s] [SCHEDULE  ] Scheduled finishRecording for beat 4.0 on slot[0,0] | after 4 beats
```

## Log Categories

- `system` - Logger state changes (enable/disable, level changes)
- `slot` - Slot state transitions
- `synth` - Synth creation and freeing
- `record` - Recording start/stop events
- `play` - Playback start/stop events
- `schedule` - Scheduled transport events
- `buffer` - Buffer allocation/deallocation
- `orphan` - Orphan synth detection results

## Troubleshooting Workflows

### Orphan Loops (Clips Keep Playing After Stop)

```supercollider
// 1. Enable verbose logging to track synth lifecycle
~clip.enableDebug(\verbose);

// 2. Reproduce the issue
~clip.armSlot(0, 0, 4);
~clip.launchSlot(0, 0);
// ... wait for recording to complete ...
~clip.stopSlot(0, 0);
// If orphan loop occurs, it will keep playing

// 3. Check for orphans
~clip.checkForOrphans;
// Look for synths that are still tracked but shouldn't be

// 4. Print active synths to see what's running
~clip.printActiveSynths;

// 5. Review log for missing free() calls
~clip.printDebugHistory;
// Look for CREATE without matching FREED

// 6. Save log for analysis
~clip.saveDebugLog("~/orphan-loop-issue.log");
```

### Infinite Recording Loop

```supercollider
// 1. Enable normal logging
~clip.enableDebug(\normal);

// 2. Reproduce the issue
~clip.armSlot(0, 0, 4);
~clip.launchSlot(0, 0);
// Recording should finish after 4 beats but doesn't

// 3. Check state transitions
~clip.printDebugHistory;
// Look for:
//   - armed -> recording transition
//   - Missing recording -> playing transition
//   - Check scheduled finishRecording event

// 4. Print summary to see state transition counts
~clip.printDebugSummary;
// Verify expected number of state changes occurred
```

### Unexpected State Transitions

```supercollider
// 1. Enable normal logging
~clip.enableDebug(\normal);

// 2. Monitor in real-time
// The logger prints to console as events occur
// Watch the console while using sc-clip

// 3. After observing issue, review history
~clip.printDebugHistory(100);  // Last 100 events

// 4. Look for warnings
// Warning messages indicate something unexpected:
//   - "Cannot play - wrong state"
//   - "Orphan player detected"
//   - "Cannot stop - wrong state"
```

## Advanced: Direct Logger Access

For more control, you can access the logger directly:

```supercollider
// Access the logger class directly
ClipDebugLogger.enable;
ClipDebugLogger.setLevel(\verbose);

// Custom log entries (from your own code)
ClipDebugLogger.log(\custom, "My custom message", "details here", level: \normal);

// Access raw history data
ClipDebugLogger.history;  // Returns List of log entries

// Each entry is a dictionary with:
// - timestamp (seconds since logger enabled)
// - category (symbol)
// - message (string)
// - details (string or nil)
// - thread (Thread object)
```

## Performance Impact

- **Minimal level**: Negligible overhead, safe for performance-critical use
- **Normal level**: Low overhead, suitable for most debugging
- **Verbose level**: Higher overhead due to detailed tracking, may affect timing in extreme cases

**Recommendation**: Use `\normal` for general troubleshooting, `\verbose` only when needed for deep debugging.

## Sharing Logs for Support

When reporting issues:

1. Enable verbose logging before reproducing the issue:
   ```supercollider
   ~clip.enableDebug(\verbose);
   ```

2. Reproduce the problem

3. Save the log:
   ```supercollider
   ~clip.saveDebugLog("~/sc-clip-issue-YYYY-MM-DD.log");
   ```

4. Attach the log file when reporting the issue

The log file is plain text and contains:
- Timestamp of each event
- Full state transition history
- Synth lifecycle (creation/destruction)
- Buffer operations
- Scheduled events

This makes it much easier for others to understand exactly what happened.

## Example Session

```supercollider
(
// Boot with debug logging enabled
~clip = SCClip.new(numChannels: 2, numSlots: 4);
~clip.boot({
    // Enable debug logging at boot
    ~clip.enableDebug(\normal);

    "SC-Clip ready with debug logging enabled".postln;
});
)

// Create a test tone
~testTone = { SinOsc.ar([440, 550]) * 0.1 }.play;

// Record a 4-beat loop on channel 0, slot 0
~clip.armSlot(0, 0, 4);
~clip.launchSlot(0, 0);

// After recording completes, check what happened
(
fork {
    5.wait;  // Wait for recording to complete
    "--- Debug Summary ---".postln;
    ~clip.printDebugSummary;

    "--- Active Synths ---".postln;
    ~clip.printActiveSynths;
}
)

// Stop the clip
~clip.stopSlot(0, 0);

// Check for any orphans
~clip.checkForOrphans;

// Save the session log
~clip.saveDebugLog("~/Desktop/my-debug-session.log");

// Clean up
~testTone.free;
~clip.shutdown;
```

## Integration with Existing Code

The debug logger is designed to be non-invasive:
- Works with existing SC-Clip code without modification
- Zero overhead when disabled
- Can be enabled/disabled at any time
- Does not affect audio processing or timing (except at verbose level in extreme cases)

All existing SC-Clip functionality continues to work exactly as before, with logging as an optional debugging aid.
