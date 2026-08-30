# RangefinderCam

A small camera app for the LG Intuition (Android 4.1.2, API16). Pick a look
before you shoot - high-contrast black & white, sepia/faded, or grain-only -
and it's baked into the photo on capture, with film grain composed onto
every look. Photos save to `Pictures/RangefinderCam/` and show up in the
stock Gallery app.

Built with the classic `android.hardware.Camera` API (this device predates
Camera2) and a plain, unfiltered live preview - the look is applied once to
the captured JPEG, not rendered live, to avoid the GPU cost of a real-time
shader pipeline on this phone's old Adreno 200.

Manual ISO is supported by this hardware's Camera1 vendor parameters
(`iso-values=auto,ISO_HJR,100,200,400,800,1600`) and exposure compensation
is available (-6 to +6 EV steps) as the closest approximation to a shutter
control this old API exposes - true manual shutter speed isn't part of
Camera1 at all. Aperture is fixed lens hardware on this phone (and on
virtually all phone cameras), not software-adjustable.

See `docs/superpowers/specs/2026-08-29-rangefindercam-design.md` for the full
design.
