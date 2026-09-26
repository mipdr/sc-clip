/*
 * ClipMasteringGUI
 *
 * Native SuperCollider GUI for the mastering chain.
 * Features Max-style signal flow diagram and interactive controls.
 * Built with Qt layouts, so the window can be freely resized/maximized.
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

	// Colors -- set explicitly (plus a dark palette on the window) so the
	// GUI reads the same regardless of the desktop's Qt theme
	*textColor { ^Color.gray(0.85) }
	*backgroundColor { ^Color.gray(0.08) }
	*panelColor { ^Color.gray(0.11) }
	*fieldColor { ^Color.gray(0.18) }

	*palette {
		^QPalette.dark
			.window_(this.backgroundColor)
			.windowText_(this.textColor)
			.base_(this.fieldColor)
			.baseText_(this.textColor)
			.button_(Color.gray(0.25))
			.buttonText_(this.textColor);
	}

	init {
		controlViews = Dictionary.new;
	}

	show {
		if (window.notNil and: { window.isClosed.not }, {
			window.front;
			^this;
		});

		// SCClip adds the mastering chain at boot, so reflect its actual state
		isActive = clip.masterBus.eqSynth.notNil;

		this.createWindow;
		window.view.palette = ClipMasteringGUI.palette;
		this.createSignalFlowView;

		window.layout = VLayout(
			[signalFlowView, stretch: 1],
			[this.createControls, stretch: 3]
		).margins_(20).spacing_(20);

		window.front;
	}

	createWindow {
		window = Window("SC-Clip Mastering Chain", Rect(100, 100, 900, 700))
			.background_(ClipMasteringGUI.backgroundColor)
			.onClose_({ "Mastering GUI closed".postln; });
	}

	createSignalFlowView {
		signalFlowView = UserView()
			.background_(ClipMasteringGUI.panelColor)
			.minHeight_(160)
			.maxHeight_(300)
			.animate_(true)
			.frameRate_(30);

		signalFlowView.drawFunc_({ |view|
			this.drawSignalFlow(view.bounds.width, view.bounds.height);
		});
	}

	// Everything is laid out relative to the view's current size, so the
	// diagram scales with the window
	drawSignalFlow { |width, height|
		var stages = [\channels, \master, \eq, \comp, \limiter, \out];
		var margin = 40;
		var spacing = (width - (2 * margin)) / stages.size;
		var boxWidth = spacing * 0.75;
		var boxHeight = (height * 0.35).clip(30, 90);
		var x = margin + ((spacing - boxWidth) / 2);
		var y = (height - boxHeight) / 2;
		var fontSize = (boxHeight * 0.2).clip(10, 16);

		Pen.use {
			Pen.smoothing_(true);

			// Title
			Pen.font_(Font("Helvetica", 14));
			Pen.color_(ClipMasteringGUI.textColor);
			Pen.stringAtPoint("Signal Flow: Channels → Master → EQ → Compressor → Limiter → Out",
				Point(margin, 20));

			// Draw boxes for each stage
			stages.do { |stage, i|
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
				Pen.font_(Font("Helvetica-Bold", fontSize));
				Pen.color_(textColor);
				Pen.stringCenteredIn(stage.asString.toUpper,
					Rect(boxX, y, boxWidth, boxHeight));

				// Connection arrow to next stage
				if (i < (stages.size - 1), {
					this.drawArrow(
						Point(boxX + boxWidth, y + (boxHeight / 2)),
						Point(boxX + spacing, y + (boxHeight / 2)),
						if (isActive or: { i < 2 }, { Color.new(0.4, 0.6, 0.8) }, { Color.gray(0.3) })
					);
				});
			};

			// Status indicator
			Pen.font_(Font("Helvetica-Bold", 12));
			Pen.color_(if (isActive, { Color.green }, { Color.gray(0.7) }));
			Pen.stringAtPoint(
				if (isActive, { "● MASTERING ACTIVE" }, { "○ Mastering Bypassed" }),
				Point(margin, height - 25)
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

	// Returns the scrollable controls area. Its canvas uses layouts, so
	// sliders stretch with the window and the EQ / dynamics columns sit side
	// by side; the scrollbar only appears when the window is too short.
	createControls {
		var scrollView = ScrollView()
			.background_(ClipMasteringGUI.panelColor)
			.hasHorizontalScroller_(false);
		var canvas = View().background_(ClipMasteringGUI.panelColor);

		canvas.layout = VLayout(
			this.masterControls,
			HLayout(
				[this.eqControls, stretch: 1],
				[VLayout(this.compressorControls, this.limiterControls, nil).margins_(0).spacing_(20), stretch: 1]
			).margins_(0).spacing_(40),
			this.presets,
			HLayout(this.applyButton, nil),
			nil
		).margins_(20).spacing_(20);

		scrollView.canvas = canvas;
		^scrollView;
	}

	heading { |string, size = 14, color|
		^StaticText()
			.string_(string)
			.font_(Font("Helvetica-Bold", size))
			.stringColor_(color ?? { ClipMasteringGUI.textColor })
			.fixedHeight_(size * 2);
	}

	subheading { |string|
		^StaticText().string_(string).stringColor_(ClipMasteringGUI.textColor).fixedHeight_(22);
	}

	// Creates a labelled slider row, registers it in controlViews, and
	// returns its layout
	param { |key, label, spec, action|
		var param = ClipMasteringParam(label, spec, action);
		controlViews[key] = param;
		^param.layout;
	}

	masterControls {
		var enableButton = Button()
			.fixedSize_(220@30)
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
			})
			.value_(isActive.binaryValue);

		^VLayout(
			this.heading("MASTER CONTROLS", 16, Color.white),
			HLayout(enableButton, nil),
			this.param(\masterLevel, "Master Level",
				ControlSpec(-40, 12, \lin, 0.1, 0, "dB"),
				{ |param| clip.masterBus.setMasterLevel(param.value) })
		).margins_(0).spacing_(8);
	}

	eqControls {
		^VLayout(
			this.heading("PARAMETRIC EQ"),

			this.subheading("Low Shelf"),
			this.param(\loFreq, "Frequency", ControlSpec(20, 500, \exp, 1, 80, "Hz")),
			this.param(\loGain, "Gain", ControlSpec(-12, 12, \lin, 0.1, 0, "dB")),

			this.subheading("Parametric Mid"),
			this.param(\midFreq, "Frequency", ControlSpec(200, 8000, \exp, 1, 1000, "Hz")),
			this.param(\midGain, "Gain", ControlSpec(-12, 12, \lin, 0.1, 0, "dB")),
			this.param(\midQ, "Q", ControlSpec(0.5, 5, \lin, 0.1, 1, "")),

			this.subheading("High Shelf"),
			this.param(\hiFreq, "Frequency", ControlSpec(2000, 20000, \exp, 1, 8000, "Hz")),
			this.param(\hiGain, "Gain", ControlSpec(-12, 12, \lin, 0.1, 0, "dB")),
			nil
		).margins_(0).spacing_(8);
	}

	compressorControls {
		^VLayout(
			this.heading("GLUE COMPRESSOR"),
			this.param(\thresh, "Threshold", ControlSpec(-40, 0, \lin, 0.1, -12, "dB")),
			this.param(\ratio, "Ratio", ControlSpec(1, 20, \lin, 0.1, 3, ":1")),
			this.param(\attack, "Attack", ControlSpec(0.001, 0.1, \exp, 0.001, 0.01, "s")),
			this.param(\release, "Release", ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s")),
			this.param(\makeupGain, "Makeup Gain", ControlSpec(-12, 24, \lin, 0.1, 0, "dB"))
		).margins_(0).spacing_(8);
	}

	limiterControls {
		^VLayout(
			this.heading("BRICK-WALL LIMITER"),
			this.param(\ceiling, "Ceiling", ControlSpec(-6, 0, \lin, 0.1, -0.3, "dB")),
			this.param(\dur, "Lookahead", ControlSpec(0.001, 0.05, \exp, 0.001, 0.01, "s"))
		).margins_(0).spacing_(8);
	}

	presets {
		var presetButton = { |name, preset, color|
			Button()
				.minHeight_(30)
				.states_([[name, Color.white, color]])
				.action_({ this.loadPreset(preset) });
		};

		^VLayout(
			this.heading("PRESETS"),
			HLayout(
				presetButton.("Neutral", \neutral, Color.gray(0.3)),
				presetButton.("Live Performance", \live, Color.new(0.3, 0.5, 0.7)),
				presetButton.("Punchy/Loud", \punchy, Color.new(0.7, 0.3, 0.3)),
				presetButton.("Warm/Subtle", \warm, Color.new(0.6, 0.5, 0.3))
			)
		).margins_(0).spacing_(8);
	}

	applyButton {
		^Button()
			.fixedSize_(200@40)
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

/*
 * ClipMasteringParam
 *
 * One labelled "slider + number box" row for ClipMasteringGUI. Unlike
 * EZSlider (which positions its parts with fixed bounds), this lives in a
 * layout, so the slider stretches with the window. Exposes the same
 * value / value_ interface EZSlider did, so ~gui.controlViews[\key].value_(x)
 * keeps working. value_ does not fire the action (same as EZSlider).
 */

ClipMasteringParam {
	var <spec, <value, <>action;
	var <labelView, <slider, <numberBox, <unitView;

	*new { |label, spec, action|
		^super.new.init(label, spec, action);
	}

	init { |label, argSpec, argAction|
		spec = argSpec.asSpec;
		action = argAction;

		labelView = StaticText()
			.string_(label)
			.stringColor_(ClipMasteringGUI.textColor)
			.fixedSize_(120@22);

		slider = Slider()
			.orientation_(\horizontal)
			.minWidth_(60)
			.fixedHeight_(22)
			.action_({ |sl|
				this.value_(spec.map(sl.value));
				action.value(this);
			});

		numberBox = NumberBox()
			.fixedSize_(70@22)
			.background_(ClipMasteringGUI.fieldColor)
			.normalColor_(ClipMasteringGUI.textColor)
			.maxDecimals_(3)
			.action_({ |nb|
				this.value_(nb.value);
				action.value(this);
			});

		unitView = StaticText()
			.string_(spec.units)
			.stringColor_(ClipMasteringGUI.textColor)
			.fixedSize_(30@22);

		this.value_(spec.default);
	}

	value_ { |val|
		value = spec.constrain(val);
		slider.value = spec.unmap(value);
		numberBox.value = value;
	}

	layout {
		^HLayout(labelView, [slider, stretch: 1], numberBox, unitView).margins_(0).spacing_(10);
	}
}
