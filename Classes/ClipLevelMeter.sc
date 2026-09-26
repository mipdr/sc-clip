/*
 * ClipLevelMeter
 *
 * Live post-fader level metering for a set of MixerChannels (SC-Clip's
 * channels and master). Plays a \clipLevelMeter synth at the tail of each
 * mixer's fader group and keeps the latest stereo peak / RMS, a peak hold,
 * and a latched clip flag (peak >= 0 dBFS) per mixer. Draw it with
 * ClipLevelMeterView.
 *
 * Usage:
 *   ~meter = ~clip.createLevelMeter;
 *   ~meter.peaks;       // [[peakL, peakR], ...] linear amplitude, one per mixer
 *   ~meter.clipped;     // [[bool, bool], ...]
 *   ~meter.resetClips;
 *   ~meter.free;
 */

ClipLevelMeter {
	classvar <holdTime = 1.5;  // seconds a peak hold stays before falling

	var <server, <names, <mixers;
	var <synths, <oscFunc;
	var <peaks, <rms, <holds, <clipped;
	var holdStamps;

	*new { |server, names, mixers|
		^super.newCopyArgs(server, names, mixers).init;
	}

	init {
		var stereo = { |value| Array.fill(mixers.size, { [value, value] }) };

		peaks = stereo.(0);
		rms = stereo.(0);
		holds = stereo.(0);
		holdStamps = stereo.(0);
		clipped = stereo.(false);

		// queueBundle defers the synth until the mixer's groups exist on the
		// server (same mechanism as MixerChannel.playfx)
		synths = mixers.collect { |mixer|
			var synth;
			mixer.queueBundle({
				synth = Synth.tail(mixer.fadergroup, \clipLevelMeter, [\bus, mixer.inbus.index]);
			});
			synth;
		};

		oscFunc = OSCFunc({ |msg| this.prUpdate(msg) }, '/clipLevelMeter', server.addr);
	}

	prUpdate { |msg|
		var index = synths.detectIndex { |synth| synth.nodeID == msg[1] };
		var now = Main.elapsedTime;

		// Replies from other ClipLevelMeter instances' synths
		if (index.isNil, { ^this });

		2.do { |ch|
			var peak = msg[3 + (ch * 2)];

			peaks[index][ch] = peak;
			rms[index][ch] = msg[4 + (ch * 2)];

			if (peak >= holds[index][ch] or: { (now - holdStamps[index][ch]) > holdTime }, {
				holds[index][ch] = peak;
				holdStamps[index][ch] = now;
			});

			if (peak >= 1, { clipped[index][ch] = true });
		};
	}

	resetClips {
		clipped.do { |pair| pair.fill(false) };
	}

	free {
		synths.do { |synth| synth.free };
		oscFunc.free;
	}
}

/*
 * ClipLevelMeterView
 *
 * Draws a ClipLevelMeter as vertical stereo bars on a -60..+6 dB scale:
 * dim bar = peak, bright bar = RMS, white line = peak hold, top box lights
 * red on clipping (click the view to reset). Scales with its size; use
 * .view to put it in a layout.
 */

ClipLevelMeterView {
	classvar minDb = -60, maxDb = 6;

	var <meter, <view;

	*new { |meter|
		^super.newCopyArgs(meter).init;
	}

	// 0..1 position of a dB value / linear amplitude on the meter scale
	*dbPos { |db| ^((db - minDb) / (maxDb - minDb)).clip(0, 1) }
	*ampPos { |amp| ^this.dbPos(amp.max(1e-6).ampdb) }

	init {
		view = UserView()
			.background_(Color.gray(0.11))
			.minWidth_((60 * meter.names.size) + 40)
			.minHeight_(200)
			.toolTip_("Click to reset clip indicators")
			.animate_(true)
			.frameRate_(30)
			.drawFunc_({ |v| this.draw(v.bounds.width, v.bounds.height) })
			.mouseDownAction_({ meter.resetClips });
	}

	draw { |width, height|
		var textColor = Color.gray(0.85);
		var red = Color(0.95, 0.25, 0.25);
		var zones = [
			[minDb, -12, Color(0.3, 0.8, 0.4)],
			[-12, -3, Color(0.9, 0.8, 0.3)],
			[-3, maxDb, red]
		];
		var gutter = 34, clipTop = 10, clipH = 12, labelH = 36;
		var top = clipTop + clipH + 6;
		var bottom = height - labelH;
		var meterH = bottom - top;
		var n = meter.names.size;
		var stripW = (width - gutter - 4) / n;
		var yFor = { |pos| bottom - (pos * meterH) };

		Pen.use {
			// dB scale
			Pen.font_(Font("Helvetica", 9));
			Pen.width_(1);
			[6, 0, -6, -12, -24, -36, -48, -60].do { |db|
				var y = yFor.(ClipLevelMeterView.dbPos(db));
				Pen.color_(Color.gray(0.6));
				Pen.stringRightJustIn(db.asString, Rect(0, y - 6, gutter - 6, 12));
				Pen.strokeColor_(Color.gray(if (db == 0, 0.45, 0.2)));
				Pen.line(Point(gutter, y), Point(width - 4, y));
				Pen.stroke;
			};

			n.do { |i|
				var x = gutter + (i * stripW);
				var barW = (stripW * 0.3).clip(6, 28);
				var gap = 4;
				var barsX = x + ((stripW - ((2 * barW) + gap)) / 2);
				var holdDb = meter.holds[i].maxItem.max(1e-6).ampdb;

				// Separate the master strip from the channels
				if (i == (n - 1) and: { n > 1 }, {
					Pen.strokeColor_(Color.gray(0.35));
					Pen.line(Point(x, clipTop), Point(x, height - 4));
					Pen.stroke;
				});

				2.do { |ch|
					var bx = barsX + (ch * (barW + gap));
					var holdPos = ClipLevelMeterView.ampPos(meter.holds[i][ch]);
					// [level position, alpha]: peak drawn dim, RMS bright on top
					var layers = [
						[ClipLevelMeterView.ampPos(meter.peaks[i][ch]), 0.45],
						[ClipLevelMeterView.ampPos(meter.rms[i][ch]), 1]
					];

					Pen.fillColor_(Color.gray(0.18));
					Pen.fillRect(Rect(bx, top, barW, meterH));

					layers.do { |layer|
						zones.do { |zone|
							var lo = ClipLevelMeterView.dbPos(zone[0]);
							var level = layer[0].min(ClipLevelMeterView.dbPos(zone[1]));
							if (level > lo, {
								Pen.fillColor_(zone[2].copy.alpha_(layer[1]));
								Pen.fillRect(Rect.fromPoints(
									Point(bx, yFor.(level)), Point(bx + barW, yFor.(lo))));
							});
						};
					};

					if (holdPos > 0, {
						Pen.fillColor_(Color.white);
						Pen.fillRect(Rect(bx, yFor.(holdPos) - 1, barW, 2));
					});

					// Clip indicator (latched until clicked)
					Pen.fillColor_(if (meter.clipped[i][ch], red, Color.gray(0.25)));
					Pen.fillRect(Rect(bx, clipTop, barW, clipH));
				};

				// Peak hold readout and name
				Pen.font_(Font("Helvetica", 10));
				Pen.color_(if (holdDb >= 0, red, textColor));
				Pen.stringCenteredIn(
					if (holdDb <= minDb, "-inf", { holdDb.round(0.1).asString }),
					Rect(x, bottom + 2, stripW, 14));
				Pen.font_(Font("Helvetica-Bold", 11));
				Pen.color_(textColor);
				Pen.stringCenteredIn(meter.names[i], Rect(x, bottom + 18, stripW, 16));
			};
		};
	}
}
