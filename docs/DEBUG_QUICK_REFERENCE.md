# Debug Logging Quick Reference

Quick reference card for SC-Clip debug logging system.

## Enable/Disable

```supercollider
~clip.enableDebug(\normal);   // Turn on (normal verbosity)
~clip.enableDebug(\minimal);  // Minimal verbosity (warnings only)
~clip.enableDebug(\verbose);  // Verbose (all details)
~clip.disableDebug();          // Turn off
```

## Check for Problems

```supercollider
~clip.checkForOrphans;         // Find orphan synths
~clip.printActiveSynths;       // Show currently tracked synths
~clip.printDebugSummary;       // Statistics summary
```

## View Logs

```supercollider
~clip.printDebugHistory;       // Print full history
~clip.printDebugHistory(50);   // Print last 50 entries
```

## Save/Share

```supercollider
~clip.saveDebugLog;                        // Save to default path
~clip.saveDebugLog("~/my-debug.log");     // Save to specific path
```

## Manage Logs

```supercollider
~clip.clearDebugLog;          // Clear accumulated history
```

## Log Levels

| Level     | What You See                                           | When to Use                          |
|-----------|--------------------------------------------------------|--------------------------------------|
| `minimal` | Only warnings, errors, and orphan detection            | Minimal overhead, only see problems  |
| `normal`  | State transitions, recording/playback events           | General troubleshooting              |
| `verbose` | Everything + synth details, buffers, scheduled events  | Deep debugging, timing issues        |

## Common Workflows

### I'm seeing orphan loops

```supercollider
~clip.enableDebug(\verbose);
// ... reproduce the issue ...
~clip.checkForOrphans;
~clip.printActiveSynths;
~clip.saveDebugLog("~/orphan-issue.log");
```

### Recording won't stop

```supercollider
~clip.enableDebug(\normal);
// ... reproduce the issue ...
~clip.printDebugHistory(50);  // Look for missing state transitions
~clip.printDebugSummary;      // Check transition counts
```

### Something unexpected happened

```supercollider
~clip.enableDebug(\normal);   // Enable before reproducing
// ... reproduce the issue ...
~clip.printDebugHistory;      // See what happened
~clip.saveDebugLog;           // Save for later analysis
```

## Log Output Format

```
[TIME] [CATEGORY] MESSAGE | DETAILS
```

Example:
```
[  12.345s] [SLOT      ] Slot[0,0] state: armed -> recording
[  12.346s] [SYNTH     ] CREATED clipRecorder for slot[0,0] (nodeID: 1001)
[  12.347s] [RECORD    ] Recording START on slot[0,0] | loop: 4 beats
```

## Categories

- `system` - Logger state changes
- `slot` - Slot state transitions
- `synth` - Synth creation/freeing
- `record` - Recording events
- `play` - Playback events
- `schedule` - Scheduled events
- `buffer` - Buffer allocation
- `orphan` - Orphan detection

## Direct Access (Advanced)

```supercollider
ClipDebugLogger.enable;
ClipDebugLogger.setLevel(\verbose);
ClipDebugLogger.history;           // Raw history array
ClipDebugLogger.checkForOrphans;
```

## Performance Impact

- **Minimal**: Negligible overhead
- **Normal**: Low overhead, safe for performance
- **Verbose**: Higher overhead, may affect timing in extreme cases

## See Also

- Full documentation: [docs/DebugLogging.md](DebugLogging.md)
- Example usage: [Examples/debug_logging_example.scd](../Examples/debug_logging_example.scd)
- Test suite: [Tests/test_debug_logger.scd](../Tests/test_debug_logger.scd)
