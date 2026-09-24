/*
 * ClipMIDI
 *
 * Tiny hardware-agnostic helper that guards MIDI input setup so it only
 * happens once per sclang session.
 *
 * MIDIIn.connectAll is itself meant to be safely re-callable (it does an
 * internal disconnectAll + reconnect-fresh each time -- see its source),
 * but on at least one real rig (jackdmp + ALSA seq, multiple class-compliant
 * USB MIDI devices) calling it a second time from a second controller's
 * setup (e.g. ClipLaunchpadMini.connect followed by
 * ClipTR8s4Channel.initTR8s4Channel, both of which need it) has been
 * observed to hang sclang completely, with no error -- not just the boot
 * routine, the whole interpreter stops responding to input. Routing every
 * caller through here instead of calling MIDIClient.init/MIDIIn.connectAll
 * directly means the real work happens exactly once per session no matter
 * how many controllers connect, sidestepping that hang.
 */

ClipMIDI {
	classvar <inputsConnected = false;

	// Initialize MIDIClient if needed and connect all external MIDI sources
	// to sclang. Safe to call from multiple controllers' connect methods --
	// only does the actual work the first time.
	*connectAllInputs {
		if (inputsConnected.not, {
			if (MIDIClient.initialized.not, { MIDIClient.init });
			MIDIIn.connectAll;
			inputsConnected = true;
			"ClipMIDI: Connected all MIDI inputs".postln;
		});
	}
}
