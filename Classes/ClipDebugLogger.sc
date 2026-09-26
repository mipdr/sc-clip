/*
 * ClipDebugLogger
 *
 * Centralized debug logging system for SC-Clip.
 * Helps troubleshoot unexpected behaviors like:
 * - Orphan loops that keep sounding
 * - Infinite recording/playing loops
 * - Missing state transitions
 * - Synth lifecycle issues
 *
 * Usage:
 *   ClipDebugLogger.enable;           // Turn on debug logging
 *   ClipDebugLogger.disable;          // Turn off debug logging
 *   ClipDebugLogger.setLevel(\verbose); // Set log verbosity (minimal, normal, verbose)
 *   ClipDebugLogger.clear;            // Clear accumulated logs
 *   ClipDebugLogger.printHistory;     // Print all logged events
 *   ClipDebugLogger.saveToFile(path); // Save log history to file
 */

ClipDebugLogger {
	classvar <enabled = false;
	classvar <level = \normal;  // \minimal, \normal, \verbose
	classvar <history;  // Array of log entries
	classvar <maxHistorySize = 1000;
	classvar <trackSynths = true;  // Track synth creation/freeing
	classvar <activeSynths;  // IdentityDictionary: synth -> creation info
	classvar <startTime;  // Time when logger was first enabled

	*initClass {
		history = List.new;
		activeSynths = IdentityDictionary.new;
		startTime = Main.elapsedTime;
	}

	*enable {
		enabled = true;
		startTime = Main.elapsedTime;
		this.log(\system, "Debug logging ENABLED", level: \minimal);
	}

	*disable {
		this.log(\system, "Debug logging DISABLED", level: \minimal);
		enabled = false;
	}

	*setLevel { |newLevel|
		if ([\minimal, \normal, \verbose].includes(newLevel).not, {
			"ClipDebugLogger: Invalid level %. Must be \\minimal, \\normal, or \\verbose".format(newLevel).error;
			^this;
		});

		level = newLevel;
		this.log(\system, "Log level set to %".format(newLevel), level: \minimal);
	}

	*clear {
		history.clear;
		activeSynths.clear;
		this.log(\system, "Log history cleared", level: \minimal);
	}

	// Main logging method
	*log { |category, message, details, level = \normal|
		var timestamp, entry, shouldLog;

		if (enabled.not, { ^this });

		// Check if this message should be logged based on current level setting
		shouldLog = case
			{ this.level == \minimal } { level == \minimal }
			{ this.level == \normal } { level != \verbose }
			{ this.level == \verbose } { true };

		if (shouldLog.not, { ^this });

		timestamp = (Main.elapsedTime - startTime).round(0.001);

		entry = (
			timestamp: timestamp,
			category: category,
			message: message,
			details: details,
			thread: thisThread
		);

		// Add to history
		history.add(entry);

		// Limit history size
		if (history.size > maxHistorySize, {
			history.removeAt(0);
		});

		// Print to console
		this.printEntry(entry);
	}

	*printEntry { |entry|
		var detailsStr = "";
		var categoryStr = "[%]".format(entry.category.asString.toUpper.padRight(10));

		if (entry.details.notNil, {
			detailsStr = " | " ++ entry.details.asString;
		});

		"[%s] % %"
			.format(
				entry.timestamp.asString.padLeft(8),
				categoryStr,
				entry.message ++ detailsStr
			).postln;
	}

	// Category-specific logging methods

	*logSlotState { |channelIndex, slotIndex, oldState, newState, details|
		this.log(
			\slot,
			"Slot[%,%] state: % -> %".format(channelIndex, slotIndex, oldState, newState),
			details,
			level: \normal
		);
	}

	*logSynthCreate { |synthName, channelIndex, slotIndex, synth, args|
		var info = (
			synthName: synthName,
			channelIndex: channelIndex,
			slotIndex: slotIndex,
			nodeID: if (synth.notNil, { synth.nodeID }, { "unknown" }),
			args: args,
			createdAt: Main.elapsedTime,
			stack: thisThread.exceptionHandler
		);

		if (trackSynths && synth.notNil, {
			activeSynths[synth] = info;
		});

		this.log(
			\synth,
			"CREATED % for slot[%,%] (nodeID: %)".format(
				synthName,
				channelIndex,
				slotIndex,
				if (synth.notNil, { synth.nodeID }, { "nil" })
			),
			"args: %".format(args),
			level: \verbose
		);
	}

	*logSynthFree { |synthName, channelIndex, slotIndex, synth, reason|
		var info = activeSynths[synth];
		var lifespan = if (info.notNil, {
			(Main.elapsedTime - info.createdAt).round(0.001)
		}, {
			"unknown"
		});

		if (trackSynths && synth.notNil, {
			activeSynths.removeAt(synth);
		});

		this.log(
			\synth,
			"FREED % for slot[%,%] (nodeID: %, lifespan: %s)".format(
				synthName,
				channelIndex,
				slotIndex,
				if (synth.notNil, { synth.nodeID }, { "nil" }),
				lifespan
			),
			"reason: %".format(reason ? "normal"),
			level: \verbose
		);
	}

	*logRecordStart { |channelIndex, slotIndex, loopLengthBeats, bufnum|
		this.log(
			\record,
			"Recording START on slot[%,%]".format(channelIndex, slotIndex),
			"loop: % beats, bufnum: %".format(loopLengthBeats, bufnum),
			level: \normal
		);
	}

	*logRecordEnd { |channelIndex, slotIndex, duration|
		this.log(
			\record,
			"Recording END on slot[%,%]".format(channelIndex, slotIndex),
			"duration: %s".format(duration.round(0.001)),
			level: \normal
		);
	}

	*logPlayStart { |channelIndex, slotIndex, bufnum|
		this.log(
			\play,
			"Playback START on slot[%,%]".format(channelIndex, slotIndex),
			"bufnum: %".format(bufnum),
			level: \normal
		);
	}

	*logPlayStop { |channelIndex, slotIndex, duration|
		this.log(
			\play,
			"Playback STOP on slot[%,%]".format(channelIndex, slotIndex),
			"duration: %s".format(duration.round(0.001)),
			level: \normal
		);
	}

	*logSchedule { |eventType, atBeat, channelIndex, slotIndex, details|
		this.log(
			\schedule,
			"Scheduled % for beat % on slot[%,%]".format(eventType, atBeat.round(0.1), channelIndex, slotIndex),
			details,
			level: \verbose
		);
	}

	*logBufferAlloc { |channelIndex, slotIndex, numFrames, bufnum|
		this.log(
			\buffer,
			"Buffer ALLOCATED for slot[%,%]".format(channelIndex, slotIndex),
			"frames: %, bufnum: %".format(numFrames, bufnum),
			level: \verbose
		);
	}

	*logBufferFree { |channelIndex, slotIndex, bufnum|
		this.log(
			\buffer,
			"Buffer FREED for slot[%,%]".format(channelIndex, slotIndex),
			"bufnum: %".format(bufnum),
			level: \verbose
		);
	}

	*logWarning { |category, message, details|
		this.log(
			category,
			"WARNING: %".format(message),
			details,
			level: \minimal
		);
	}

	*logError { |category, message, details|
		this.log(
			category,
			"ERROR: %".format(message),
			details,
			level: \minimal
		);
	}

	// Orphan detection
	*checkForOrphans {
		var orphans = List.new;
		var now = Main.elapsedTime;

		if (trackSynths.not, {
			"ClipDebugLogger: Synth tracking is disabled".warn;
			^this;
		});

		activeSynths.keysValuesDo { |synth, info|
			var age = now - info.createdAt;

			// Check if synth is still running
			if (synth.isPlaying.not, {
				// Synth stopped but wasn't removed from tracking - likely orphaned
				orphans.add((
					synth: synth,
					info: info,
					age: age
				));
			});
		};

		if (orphans.size > 0, {
			this.log(
				\orphan,
				"Found % potential orphan synth(s)".format(orphans.size),
				level: \minimal
			);

			orphans.do { |orphan|
				this.log(
					\orphan,
					"  - % (nodeID: %, age: %s, slot[%,%])".format(
						orphan.info.synthName,
						orphan.info.nodeID,
						orphan.age.round(0.1),
						orphan.info.channelIndex,
						orphan.info.slotIndex
					),
					level: \minimal
				);
			};
		}, {
			this.log(\orphan, "No orphan synths detected", level: \normal);
		});

		^orphans;
	}

	*printActiveSynths {
		var now = Main.elapsedTime;

		"========================================".postln;
		"Active Synths (%)".format(activeSynths.size).postln;
		"========================================".postln;

		if (activeSynths.size == 0, {
			"  (none)".postln;
		}, {
			activeSynths.keysValuesDo { |synth, info|
				var age = now - info.createdAt;
				"  % (nodeID: %, age: %s, slot[%,%])".format(
					info.synthName,
					info.nodeID,
					age.round(0.1),
					info.channelIndex,
					info.slotIndex
				).postln;
			};
		});

		"========================================".postln;
	}

	// Print full history
	*printHistory { |numEntries|
		var entries = if (numEntries.notNil, {
			history.copyRange(history.size - numEntries, history.size - 1);
		}, {
			history;
		});

		"========================================".postln;
		"ClipDebugLogger History (% entries)".format(entries.size).postln;
		"========================================".postln;

		entries.do { |entry|
			this.printEntry(entry);
		};

		"========================================".postln;
	}

	// Save history to file
	*saveToFile { |path|
		var file;

		if (path.isNil, {
			path = "~/sc-clip-debug-%.log".format(Date.getDate.stamp).standardizePath;
		});

		file = File(path, "w");

		if (file.isOpen.not, {
			"ClipDebugLogger: Failed to open file for writing: %".format(path).error;
			^this;
		});

		file.write("SC-Clip Debug Log\n");
		file.write("Generated: %\n".format(Date.getDate.asString));
		file.write("Total entries: %\n".format(history.size));
		file.write("========================================\n\n");

		history.do { |entry|
			var detailsStr = if (entry.details.notNil, { " | " ++ entry.details.asString }, { "" });

			file.write("[%s] [%] %%\n".format(
				entry.timestamp.asString.padLeft(8),
				entry.category.asString.toUpper.padRight(10),
				entry.message,
				detailsStr
			));
		};

		file.close;

		"ClipDebugLogger: Saved % entries to %".format(history.size, path).postln;
	}

	// Get summary statistics
	*printSummary {
		var categories = IdentityDictionary.new;
		var stateTransitions = IdentityDictionary.new;

		history.do { |entry|
			// Count by category
			var count = categories[entry.category] ? 0;
			categories[entry.category] = count + 1;

			// Track state transitions
			if (entry.category == \slot, {
				var msg = entry.message;
				stateTransitions[msg] = (stateTransitions[msg] ? 0) + 1;
			});
		};

		"========================================".postln;
		"ClipDebugLogger Summary".postln;
		"========================================".postln;
		"Total entries: %".format(history.size).postln;
		"Time span: %s".format((Main.elapsedTime - startTime).round(0.1)).postln;
		"Current level: %".format(level).postln;
		"".postln;

		"Events by category:".postln;
		categories.keysValuesDo { |cat, count|
			"  %: %".format(cat.asString.padRight(12), count).postln;
		};

		if (stateTransitions.size > 0, {
			"".postln;
			"State transitions:".postln;
			stateTransitions.keysValuesDo { |trans, count|
				"  % (% times)".format(trans, count).postln;
			};
		});

		"========================================".postln;
	}
}
