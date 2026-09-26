/*
 * ClipMasteringGUI
 *
 * Native SuperCollider GUI for the mastering chain.
 * Features Max-style signal flow diagram and interactive controls.
 *
 * Usage:
 *   ~gui = ClipMasteringGUI(~clip);
 *   ~gui.show;
 *   ~gui.close;
 */

ClipMasteringGUI {
	var <clip;
	var <window;
	var <signalFlowView;
	var <controlViews;
	var <isActive = false;

	*new { |clipInstance|
		^super.newCopyArgs(clipInstance).init;
	}

	init {
		controlViews = Dictionary.new;
	}

	show {
		if (window.notNil and: { window.isClosed.not }, {
			window.front;
			^this;
		});

		this.createWindow;
		this.createSignalFlowView;
		this.createControls;

		window.front;
	}

	createWindow {
		window = Window("SC-Clip Mastering Chain", Rect(100, 100, 900, 600))
			.background_(Color.gray(0.15))
			.onClose_({ "Mastering GUI closed".postln; });
	}

	createSignalFlowView {
		signalFlowView = UserView(window, Rect(20, 20, 860, 200))
			.background_(Color.gray(0.1))
			.animate_(true)
			.frameRate_(30);

		signalFlowView.drawFunc_({
			this.drawSignalFlow;
		});
	}

	drawSignalFlow {
		var x = 40, y = 100;
		var boxWidth = 120, boxHeight = 60;
		var spacing = 160;

		Pen.use {
			Pen.smoothing_(true);

			// Title
			Pen.font_(Font("Helvetica", 14));
			Pen.color_(Color.gray(0.7));
			Pen.stringAtPoint("Signal Flow: Channels → Master → EQ → Compressor → Limiter → Out",
				Point(40, 20));

			// Draw boxes for each stage
			[\channels, \master, \eq, \comp, \limiter, \out].do { |stage, i|
				var boxX = x + (i * spacing);
				var boxColor, textColor;

				// Color based on active state
				if (isActive or: { i < 2 }, {
					boxColor = Color.new(0.2, 0.4, 0.6, 0.8);
					textColor = Color.white;
				}, {
					boxColor = Color.gray(0.25);
					textColor = Color.gray(0.5);
				});

				// Box background
				Pen.fillColor_(boxColor);
				Pen.addRect(Rect(boxX, y, boxWidth, boxHeight));
				Pen.fill;

				// Box border
				Pen.strokeColor_(Color.gray(0.6));
				Pen.width_(2);
				Pen.addRect(Rect(boxX, y, boxWidth, boxHeight));
				Pen.stroke;

				// Label
				Pen.font_(Font("Helvetica-Bold", 12));
				Pen.color_(textColor);
				Pen.stringCenteredIn(stage.asString.toUpper,
					Rect(boxX, y + 22, boxWidth, 20));

				// Connection arrow to next stage
				if (i < 5, {
					this.drawArrow(
						Point(boxX + boxWidth, y + (boxHeight / 2)),
						Point(boxX + spacing, y + (boxHeight / 2)),
						if (isActive or: { i < 2 }, { Color.new(0.4, 0.6, 0.8) }, { Color.gray(0.3) })
					);
				});
			};

			// Status indicator
			Pen.font_(Font("Helvetica-Bold", 12));
			Pen.color_(if (isActive, { Color.green }, { Color.gray(0.5) }));
			Pen.stringAtPoint(
				if (isActive, { "● MASTERING ACTIVE" }, { "○ Mastering Bypassed" }),
				Point(40, 175)
			);
		};
	}

	drawArrow { |from, to, color|
		// Line
		Pen.strokeColor_(color);
		Pen.width_(3);
		Pen.line(from, to);
		Pen.stroke;

		// Arrow head
		Pen.fillColor_(color);
		Pen.moveTo(to);
		Pen.lineTo(Point(to.x - 10, to.y - 5));
		Pen.lineTo(Point(to.x - 10, to.y + 5));
		Pen.lineTo(to);
		Pen.fill;
	}

	createControls {
		var scrollView, contentView;
		var yPos = 20;

		// Scrollable control area
		scrollView = ScrollView(window, Rect(20, 240, 860, 340))
			.background_(Color.gray(0.12))
			.hasHorizontalScroller_(false);

		contentView = View(scrollView, Rect(0, 0, 840, 900));

		// Master controls
		yPos = this.addMasterControls(contentView, yPos);

		// EQ section
		yPos = this.addEQControls(contentView, yPos);

		// Compressor section
		yPos = this.addCompressorControls(contentView, yPos);

		// Limiter section
		yPos = this.addLimiterControls(contentView, yPos);

		// Presets
		yPos = this.addPresets(contentView, yPos);

		// Apply button
		this.addApplyButton(contentView, yPos);
	}

	addMasterControls { |parent, yPos|
		StaticText(parent, Rect(20, yPos, 800, 30))
			.string_("MASTER CONTROLS")
			.font_(Font("Helvetica-Bold", 16))
			.stringColor_(Color.white);
		yPos = yPos + 40;

		Button(parent, Rect(20, yPos, 200, 30))
			.states_([
				["Enable Mastering Chain", Color.white, Color.new(0.3, 0.6, 0.3)],
				["Disable Mastering Chain", Color.white, Color.new(0.6, 0.3, 0.3)]
			])
			.action_({ |btn|
				if (btn.value == 1, {
					clip.masterBus.addMasteringChain;
					isActive = true;
				}, {
					clip.masterBus.removeMasteringChain;
					isActive = false;
				});
				signalFlowView.refresh;
			});

		controlViews[\masterLevel] = EZSlider(parent, Rect(20, yPos + 40, 400, 25),
			"Master Level",
			ControlSpec(-40, 12, \lin, 0.1, 0, "dB"),
			{ |ez| clip.masterBus.setMasterLevel(ez.value) },
			labelWidth: 120, numberWidth: 60
		);

		^(yPos + 100);
	}

	addEQControls { |parent, yPos|
		StaticText(parent, Rect(20, yPos, 800, 25))
			.string_("PARAMETRIC EQ")
			.font_(Font("Helvetica-Bold", 14))
			.stringColor_(Color.new(0.8, 0.9, 1));
		yPos = yPos + 35;

		// Low shelf
		StaticText(parent, Rect(40, yPos, 100, 20))
			.string_("Low Shelf")
			.stringColor_(Color.gray(0.8));
		yPos = yPos + 25;

		controlViews[\loFreq] = EZSlider(parent, Rect(40, yPos, 380, 25), "Frequency",
			ControlSpec(20, 500, \exp, 1, 80, "Hz"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\loGain] = EZSlider(parent, Rect(40, yPos, 380, 25), "Gain",
			ControlSpec(-12, 12, \lin, 0.1, 0, "dB"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 45;

		// Mid parametric
		StaticText(parent, Rect(40, yPos, 100, 20))
			.string_("Parametric Mid")
			.stringColor_(Color.gray(0.8));
		yPos = yPos + 25;

		controlViews[\midFreq] = EZSlider(parent, Rect(40, yPos, 380, 25), "Frequency",
			ControlSpec(200, 8000, \exp, 1, 1000, "Hz"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\midGain] = EZSlider(parent, Rect(40, yPos, 380, 25), "Gain",
			ControlSpec(-12, 12, \lin, 0.1, 0, "dB"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\midQ] = EZSlider(parent, Rect(40, yPos, 380, 25), "Q",
			ControlSpec(0.5, 5, \lin, 0.1, 1, ""), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 45;

		// High shelf
		StaticText(parent, Rect(40, yPos, 100, 20))
			.string_("High Shelf")
			.stringColor_(Color.gray(0.8));
		yPos = yPos + 25;

		controlViews[\hiFreq] = EZSlider(parent, Rect(40, yPos, 380, 25), "Frequency",
			ControlSpec(2000, 20000, \exp, 1, 8000, "Hz"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\hiGain] = EZSlider(parent, Rect(40, yPos, 380, 25), "Gain",
			ControlSpec(-12, 12, \lin, 0.1, 0, "dB"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 60;

		^yPos;
	}

	addCompressorControls { |parent, yPos|
		StaticText(parent, Rect(20, yPos, 800, 25))
			.string_("GLUE COMPRESSOR")
			.font_(Font("Helvetica-Bold", 14))
			.stringColor_(Color.new(0.8, 0.9, 1));
		yPos = yPos + 35;

		controlViews[\thresh] = EZSlider(parent, Rect(40, yPos, 380, 25), "Threshold",
			ControlSpec(-40, 0, \lin, 0.1, -12, "dB"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\ratio] = EZSlider(parent, Rect(40, yPos, 380, 25), "Ratio",
			ControlSpec(1, 20, \lin, 0.1, 3, ":1"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\attack] = EZSlider(parent, Rect(40, yPos, 380, 25), "Attack",
			ControlSpec(0.001, 0.1, \exp, 0.001, 0.01, "s"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\release] = EZSlider(parent, Rect(40, yPos, 380, 25), "Release",
			ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\makeupGain] = EZSlider(parent, Rect(40, yPos, 380, 25), "Makeup Gain",
			ControlSpec(-12, 24, \lin, 0.1, 0, "dB"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 60;

		^yPos;
	}

	addLimiterControls { |parent, yPos|
		StaticText(parent, Rect(20, yPos, 800, 25))
			.string_("BRICK-WALL LIMITER")
			.font_(Font("Helvetica-Bold", 14))
			.stringColor_(Color.new(0.8, 0.9, 1));
		yPos = yPos + 35;

		controlViews[\ceiling] = EZSlider(parent, Rect(40, yPos, 380, 25), "Ceiling",
			ControlSpec(-6, 0, \lin, 0.1, -0.3, "dB"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 30;

		controlViews[\dur] = EZSlider(parent, Rect(40, yPos, 380, 25), "Lookahead",
			ControlSpec(0.001, 0.05, \exp, 0.001, 0.01, "s"), nil,
			labelWidth: 100, numberWidth: 60);
		yPos = yPos + 60;

		^yPos;
	}

	addPresets { |parent, yPos|
		var presetButtons;

		StaticText(parent, Rect(20, yPos, 800, 25))
			.string_("PRESETS")
			.font_(Font("Helvetica-Bold", 14))
			.stringColor_(Color.new(0.8, 0.9, 1));
		yPos = yPos + 35;

		presetButtons = HLayoutView(parent, Rect(40, yPos, 800, 35));

		Button(presetButtons, 150@30)
			.states_([["Neutral", Color.white, Color.gray(0.3)]])
			.action_({ this.loadPreset(\neutral) });

		Button(presetButtons, 150@30)
			.states_([["Live Performance", Color.white, Color.new(0.3, 0.5, 0.7)]])
			.action_({ this.loadPreset(\live) });

		Button(presetButtons, 150@30)
			.states_([["Punchy/Loud", Color.white, Color.new(0.7, 0.3, 0.3)]])
			.action_({ this.loadPreset(\punchy) });

		Button(presetButtons, 150@30)
			.states_([["Warm/Subtle", Color.white, Color.new(0.6, 0.5, 0.3)]])
			.action_({ this.loadPreset(\warm) });

		^(yPos + 50);
	}

	addApplyButton { |parent, yPos|
		Button(parent, Rect(40, yPos, 200, 40))
			.states_([["Apply Settings", Color.white, Color.new(0.2, 0.5, 0.8)]])
			.font_(Font("Helvetica-Bold", 13))
			.action_({ this.applySettings });
	}

	loadPreset { |presetName|
		var presets = (
			neutral: (
				loGain: 0, midGain: 0, hiGain: 0,
				thresh: -12, ratio: 3, makeupGain: 0, ceiling: -0.3
			),
			live: (
				loGain: 2, midGain: -1, hiGain: 1,
				thresh: -18, ratio: 4, attack: 0.005, release: 0.25, makeupGain: 3,
				ceiling: -0.5
			),
			punchy: (
				loGain: 3, midGain: 0, hiGain: 2,
				thresh: -24, ratio: 6, attack: 0.003, release: 0.15, makeupGain: 6,
				ceiling: -0.1
			),
			warm: (
				loGain: 1, midGain: -0.5, hiGain: -1,
				thresh: -15, ratio: 2.5, attack: 0.015, release: 0.35, makeupGain: 2,
				ceiling: -0.8
			)
		);

		var preset = presets[presetName];
		if (preset.isNil, {
			"Preset % not found".format(presetName).error;
			^this;
		});

		"Loading preset: %".format(presetName).postln;

		// Update GUI controls
		preset.keysValuesDo { |key, value|
			if (controlViews[key].notNil, {
				controlViews[key].value_(value);
			});
		};

		// Auto-apply
		this.applySettings;
	}

	applySettings {
		"Applying mastering settings...".postln;

		clip.masterBus.setEQ(
			loFreq: controlViews[\loFreq].value,
			loGain: controlViews[\loGain].value,
			midFreq: controlViews[\midFreq].value,
			midGain: controlViews[\midGain].value,
			midQ: controlViews[\midQ].value,
			hiFreq: controlViews[\hiFreq].value,
			hiGain: controlViews[\hiGain].value
		);

		clip.masterBus.setCompressor(
			thresh: controlViews[\thresh].value,
			ratio: controlViews[\ratio].value,
			attack: controlViews[\attack].value,
			release: controlViews[\release].value,
			makeupGain: controlViews[\makeupGain].value
		);

		clip.masterBus.setLimiter(
			ceiling: controlViews[\ceiling].value,
			dur: controlViews[\dur].value
		);

		"Settings applied!".postln;
	}

	close {
		if (window.notNil, { window.close });
	}
}
