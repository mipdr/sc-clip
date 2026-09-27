/*
 * SC-Clip Mastering Chain GUI Demo
 *
 * Demonstrates a custom-drawn, Max-style signal flow diagram
 * with interactive controls for the mastering chain.
 *
 * Run from command line (no IDE needed):
 * sclang mastering_gui_demo.scd
 */

(
var win, userView, controlView;
var eqControls, compControls, limiterControls;
var drawSignalFlow;
var masteringActive = false;

// Create main window
win = Window("SC-Clip Mastering Chain", Rect(100, 100, 900, 600))
    .background_(Color.gray(0.15));

// Top section: Signal flow diagram (custom drawing)
userView = UserView(win, Rect(20, 20, 860, 200))
    .background_(Color.gray(0.1))
    .animate_(true)  // Enable animation for VU meters (future)
    .frameRate_(30);

// Signal flow drawing function
drawSignalFlow = {
    var x = 40, y = 100;
    var boxWidth = 120, boxHeight = 60;
    var spacing = 160;
    var levels = [0.5, 0.7, 0.6, 0.4];  // Fake levels for demo

    Pen.use {
        // Anti-aliasing
        Pen.smoothing_(true);

        // Title
        Pen.font_(Font("Helvetica", 14));
        Pen.color_(Color.gray(0.7));
        Pen.stringAtPoint("Signal Flow: Channels → Master → EQ → Compressor → Limiter → Out",
            Point(40, 20));

        // Draw boxes for each stage
        [\channels, \master, \eq, \comp, \limiter, \out].do { |stage, i|
            var boxX = x + (i * spacing);
            var isActive = masteringActive or: { i < 2 };  // Channels and Master always active
            var boxColor = if (isActive, {
                Color.new(0.2, 0.4, 0.6, 0.8)
            }, {
                Color.gray(0.25)
            });
            var textColor = if (isActive, {
                Color.white
            }, {
                Color.gray(0.5)
            });

            // Box
            Pen.fillColor_(boxColor);
            Pen.addRect(Rect(boxX, y, boxWidth, boxHeight));
            Pen.fill;

            // Border
            Pen.strokeColor_(Color.gray(0.6));
            Pen.width_(2);
            Pen.addRect(Rect(boxX, y, boxWidth, boxHeight));
            Pen.stroke;

            // Label
            Pen.font_(Font("Helvetica-Bold", 12));
            Pen.color_(textColor);
            Pen.stringCenteredIn(stage.asString.toUpper,
                Rect(boxX, y + 20, boxWidth, 20));

            // Level meter (simple bar)
            if (isActive and: { i < 5 }, {
                var meterWidth = boxWidth * 0.8;
                var meterHeight = 4;
                var meterX = boxX + ((boxWidth - meterWidth) / 2);
                var meterY = y + boxHeight - 15;
                var level = levels.wrapAt(i);

                // Background
                Pen.fillColor_(Color.gray(0.2));
                Pen.addRect(Rect(meterX, meterY, meterWidth, meterHeight));
                Pen.fill;

                // Level
                Pen.fillColor_(Color.green(0.8));
                Pen.addRect(Rect(meterX, meterY, meterWidth * level, meterHeight));
                Pen.fill;
            });

            // Connection arrow to next stage
            if (i < 5, {
                var arrowStartX = boxX + boxWidth;
                var arrowEndX = boxX + boxWidth + spacing - boxWidth;
                var arrowY = y + (boxHeight / 2);
                var arrowColor = if (isActive, {
                    Color.new(0.4, 0.6, 0.8)
                }, {
                    Color.gray(0.3)
                });

                Pen.strokeColor_(arrowColor);
                Pen.width_(3);
                Pen.line(Point(arrowStartX, arrowY), Point(arrowEndX, arrowY));
                Pen.stroke;

                // Arrow head
                Pen.fillColor_(arrowColor);
                Pen.moveTo(Point(arrowEndX, arrowY));
                Pen.lineTo(Point(arrowEndX - 10, arrowY - 5));
                Pen.lineTo(Point(arrowEndX - 10, arrowY + 5));
                Pen.lineTo(Point(arrowEndX, arrowY));
                Pen.fill;
            });
        };

        // Status text
        Pen.font_(Font("Helvetica", 11));
        Pen.color_(if (masteringActive, { Color.green }, { Color.gray(0.5) }));
        Pen.stringAtPoint(
            if (masteringActive, { "● MASTERING ACTIVE" }, { "○ Mastering Bypassed" }),
            Point(40, 175)
        );
    };
};

userView.drawFunc_(drawSignalFlow);

// Control section (scrollable)
controlView = ScrollView(win, Rect(20, 240, 860, 340))
    .background_(Color.gray(0.12))
    .hasHorizontalScroller_(false);

// Content view for controls
var contentView = View(controlView, Rect(0, 0, 840, 900));

var yPos = 20;

// === MASTER CONTROLS ===
StaticText(contentView, Rect(20, yPos, 800, 30))
    .string_("MASTER CONTROLS")
    .font_(Font("Helvetica-Bold", 16))
    .stringColor_(Color.white);
yPos = yPos + 40;

Button(contentView, Rect(20, yPos, 200, 30))
    .states_([
        ["Enable Mastering Chain", Color.white, Color.new(0.3, 0.6, 0.3)],
        ["Disable Mastering Chain", Color.white, Color.new(0.6, 0.3, 0.3)]
    ])
    .action_({ |btn|
        if (btn.value == 1, {
            "Enabling mastering chain...".postln;
            // ~clip.masterBus.addMasteringChain;  // Uncomment when connected
            masteringActive = true;
        }, {
            "Disabling mastering chain...".postln;
            // ~clip.masterBus.removeMasteringChain;  // Uncomment when connected
            masteringActive = false;
        });
        userView.refresh;  // Redraw signal flow
    });

EZSlider(contentView, Rect(20, yPos + 40, 400, 25), "Master Level",
    ControlSpec(-40, 12, \lin, 0.1, 0, "dB"),
    { |ez|
        "Master level: % dB".format(ez.value.round(0.1)).postln;
        // ~clip.masterBus.setMasterLevel(ez.value);  // Uncomment when connected
    },
    labelWidth: 120, numberWidth: 60
);

yPos = yPos + 100;

// === EQ SECTION ===
StaticText(contentView, Rect(20, yPos, 800, 25))
    .string_("PARAMETRIC EQ")
    .font_(Font("Helvetica-Bold", 14))
    .stringColor_(Color.new(0.8, 0.9, 1));
yPos = yPos + 35;

eqControls = Dictionary.new;

// Low shelf
StaticText(contentView, Rect(40, yPos, 100, 20))
    .string_("Low Shelf")
    .stringColor_(Color.gray(0.8));
yPos = yPos + 25;

eqControls[\loFreq] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Frequency",
    ControlSpec(20, 500, \exp, 1, 80, "Hz"),
    { |ez| "Low shelf freq: % Hz".format(ez.value.round(1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

eqControls[\loGain] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Gain",
    ControlSpec(-12, 12, \lin, 0.1, 0, "dB"),
    { |ez| "Low shelf gain: % dB".format(ez.value.round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 45;

// Mid parametric
StaticText(contentView, Rect(40, yPos, 100, 20))
    .string_("Parametric Mid")
    .stringColor_(Color.gray(0.8));
yPos = yPos + 25;

eqControls[\midFreq] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Frequency",
    ControlSpec(200, 8000, \exp, 1, 1000, "Hz"),
    { |ez| "Mid freq: % Hz".format(ez.value.round(1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

eqControls[\midGain] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Gain",
    ControlSpec(-12, 12, \lin, 0.1, 0, "dB"),
    { |ez| "Mid gain: % dB".format(ez.value.round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

eqControls[\midQ] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Q",
    ControlSpec(0.5, 5, \lin, 0.1, 1, ""),
    { |ez| "Mid Q: %".format(ez.value.round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 45;

// High shelf
StaticText(contentView, Rect(40, yPos, 100, 20))
    .string_("High Shelf")
    .stringColor_(Color.gray(0.8));
yPos = yPos + 25;

eqControls[\hiFreq] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Frequency",
    ControlSpec(2000, 20000, \exp, 1, 8000, "Hz"),
    { |ez| "High shelf freq: % Hz".format(ez.value.round(1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

eqControls[\hiGain] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Gain",
    ControlSpec(-12, 12, \lin, 0.1, 0, "dB"),
    { |ez| "High shelf gain: % dB".format(ez.value.round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 60;

// === COMPRESSOR SECTION ===
StaticText(contentView, Rect(20, yPos, 800, 25))
    .string_("GLUE COMPRESSOR")
    .font_(Font("Helvetica-Bold", 14))
    .stringColor_(Color.new(0.8, 0.9, 1));
yPos = yPos + 35;

compControls = Dictionary.new;

compControls[\thresh] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Threshold",
    ControlSpec(-40, 0, \lin, 0.1, -12, "dB"),
    { |ez| "Comp threshold: % dB".format(ez.value.round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

compControls[\ratio] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Ratio",
    ControlSpec(1, 20, \lin, 0.1, 3, ":1"),
    { |ez| "Comp ratio: %:1".format(ez.value.round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

compControls[\attack] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Attack",
    ControlSpec(0.001, 0.1, \exp, 0.001, 0.01, "s"),
    { |ez| "Comp attack: % ms".format((ez.value * 1000).round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

compControls[\release] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Release",
    ControlSpec(0.01, 2, \exp, 0.01, 0.3, "s"),
    { |ez| "Comp release: % ms".format((ez.value * 1000).round(1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

compControls[\makeupGain] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Makeup Gain",
    ControlSpec(-12, 24, \lin, 0.1, 0, "dB"),
    { |ez| "Comp makeup: % dB".format(ez.value.round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 60;

// === LIMITER SECTION ===
StaticText(contentView, Rect(20, yPos, 800, 25))
    .string_("BRICK-WALL LIMITER")
    .font_(Font("Helvetica-Bold", 14))
    .stringColor_(Color.new(0.8, 0.9, 1));
yPos = yPos + 35;

limiterControls = Dictionary.new;

limiterControls[\ceiling] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Ceiling",
    ControlSpec(-6, 0, \lin, 0.1, -0.3, "dB"),
    { |ez| "Limiter ceiling: % dB".format(ez.value.round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 30;

limiterControls[\dur] = EZSlider(contentView, Rect(40, yPos, 380, 25), "Lookahead",
    ControlSpec(0.001, 0.05, \exp, 0.001, 0.01, "s"),
    { |ez| "Limiter lookahead: % ms".format((ez.value * 1000).round(0.1)).postln },
    labelWidth: 100, numberWidth: 60
);
yPos = yPos + 60;

// === PRESET SECTION ===
StaticText(contentView, Rect(20, yPos, 800, 25))
    .string_("PRESETS")
    .font_(Font("Helvetica-Bold", 14))
    .stringColor_(Color.new(0.8, 0.9, 1));
yPos = yPos + 35;

var presetButtons = HLayoutView(contentView, Rect(40, yPos, 800, 35));

Button(presetButtons, 150@30)
    .states_([["Neutral (Bypass)", Color.white, Color.gray(0.3)]])
    .action_({
        "Loading Neutral preset...".postln;
        eqControls[\loGain].value_(0);
        eqControls[\midGain].value_(0);
        eqControls[\hiGain].value_(0);
        compControls[\thresh].value_(-12);
        compControls[\ratio].value_(3);
        compControls[\makeupGain].value_(0);
        limiterControls[\ceiling].value_(-0.3);
    });

Button(presetButtons, 150@30)
    .states_([["Live Performance", Color.white, Color.new(0.3, 0.5, 0.7)]])
    .action_({
        "Loading Live Performance preset...".postln;
        eqControls[\loGain].value_(2);
        eqControls[\midGain].value_(-1);
        eqControls[\hiGain].value_(1);
        compControls[\thresh].value_(-18);
        compControls[\ratio].value_(4);
        compControls[\makeupGain].value_(3);
        limiterControls[\ceiling].value_(-0.5);
    });

Button(presetButtons, 150@30)
    .states_([["Punchy/Loud", Color.white, Color.new(0.7, 0.3, 0.3)]])
    .action_({
        "Loading Punchy preset...".postln;
        eqControls[\loGain].value_(3);
        eqControls[\midGain].value_(0);
        eqControls[\hiGain].value_(2);
        compControls[\thresh].value_(-24);
        compControls[\ratio].value_(6);
        compControls[\makeupGain].value_(6);
        limiterControls[\ceiling].value_(-0.1);
    });

Button(presetButtons, 150@30)
    .states_([["Warm/Subtle", Color.white, Color.new(0.6, 0.5, 0.3)]])
    .action_({
        "Loading Warm preset...".postln;
        eqControls[\loGain].value_(1);
        eqControls[\midGain].value_(-0.5);
        eqControls[\hiGain].value_(-1);
        compControls[\thresh].value_(-15);
        compControls[\ratio].value_(2.5);
        compControls[\makeupGain].value_(2);
        limiterControls[\ceiling].value_(-0.8);
    });

yPos = yPos + 50;

// Apply button
Button(contentView, Rect(40, yPos, 200, 40))
    .states_([["Apply Settings to SC-Clip", Color.white, Color.new(0.2, 0.5, 0.8)]])
    .font_(Font("Helvetica-Bold", 13))
    .action_({
        "Applying settings to mastering chain...".postln;
        /*
        ~clip.masterBus.setEQ(
            loFreq: eqControls[\loFreq].value,
            loGain: eqControls[\loGain].value,
            midFreq: eqControls[\midFreq].value,
            midGain: eqControls[\midGain].value,
            midQ: eqControls[\midQ].value,
            hiFreq: eqControls[\hiFreq].value,
            hiGain: eqControls[\hiGain].value
        );
        ~clip.masterBus.setCompressor(
            thresh: compControls[\thresh].value,
            ratio: compControls[\ratio].value,
            attack: compControls[\attack].value,
            release: compControls[\release].value,
            makeupGain: compControls[\makeupGain].value
        );
        ~clip.masterBus.setLimiter(
            ceiling: limiterControls[\ceiling].value,
            dur: limiterControls[\dur].value
        );
        */
        "Settings applied!".postln;
    });

// Instructions
StaticText(contentView, Rect(260, yPos + 10, 500, 40))
    .string_("Uncomment the ~clip calls in the code to connect to a running SC-Clip instance")
    .font_(Font("Helvetica", 10))
    .stringColor_(Color.gray(0.5));

// Show window
win.front;

"Mastering GUI launched!".postln;
"Adjust controls and click 'Apply Settings' to update SC-Clip.".postln;
)
