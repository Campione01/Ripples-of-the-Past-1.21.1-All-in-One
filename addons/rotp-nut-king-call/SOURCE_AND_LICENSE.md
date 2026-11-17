# Nut King Call Stand Addon (`nutkingcall`): source and license

## Public release

- Module: `rotp-nut-king-call`, mod id `nutkingcall`, version `1.0.0-port.2`.
- Original 1.16.5 add-on: see the record below (original authors: Grasshopper, Day_GeE).
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-10-29 to 2026-11-14); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Nut King Call source and license

This module is a NeoForge 1.21.1 port of the Forge 1.16.5 Ripples of the Past
add-on Nut King Call, which its author sent to the owner on 2026-10-29 for this
port (archive `mods (2).zip`, entry `mods/NutKingCall-Addon-1.0.0.jar`).

- Donor: `NutKingCall-Addon-1.0.0.jar`
- Donor SHA-256: `2F9D4BA5296932E1BB3678FF660FB06DD1D4FFB52593A0D01D39115BE3F184D0`
- Donor platform: Minecraft 1.16.5 / Forge 36 / Ripples of the Past 0.2.2 / GeckoLib 3
- Donor mod id and version: `nutkingcall` / `1.0.0`
- Original authors: Grasshopper (author), Day_GeE (modeler)
- License: GNU GPL v3.0, as stated in the donor's `META-INF/mods.toml`

The Java implementation is a clean port to the current ROTP core and NeoForge
APIs. The donor's model, animations, textures, block state and model,
language files and logo are redistributed under the donor's GPL-3.0 license;
port code is provided under GPL-3.0-only.

The skin's animation file adds two clips to the donor's: `punch_right` and
`punch_left` are exact copies of the donor's `attack1` and `attack2`, written by
`migration/punch_clips.py`, so that each hand plays its own punch on this core.
