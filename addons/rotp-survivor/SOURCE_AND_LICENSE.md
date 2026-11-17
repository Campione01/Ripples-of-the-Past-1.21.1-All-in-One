# Survivor Stand Addon (`survivor`): source and license

## Public release

- Module: `rotp-survivor`, mod id `survivor`, version `1.0.0-port.2`.
- Original 1.16.5 add-on: see the record below (original authors: Grasshopper).
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-10-29 to 2026-11-14); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Survivor source and license

- Donor: `RotP-Survivor-1.0.jar`, from the archive `mods (2).zip` in
  `10.29rotp專項測試證據留存` (the add-on pack the player sent on 2026-10-29)
- Donor SHA-256: `41E2B98F956D323DAC180D3A6985E68EBC446A4165706B15C8A4C8F7621A7586`
- Donor platform: Minecraft 1.16.5 / Forge 36 / Ripples of the Past 0.2+
- Donor mod id and version: `survivor` / `1.0.0` ("Survivor Stand Addon")
- Donor author: Grasshopper; support https://ifdian.net/a/mazaNB666
- License: GNU GPL v3.0, as the donor's `META-INF/mods.toml` states (`license="GNU GPL v3.0"`)
- Provenance: the donor's author, the player who tested the 1.21.1 build on 2026-10-29, sent it to
  the owner on that day for this port

The Java implementation is a clean NeoForge 1.21.1 port onto the Ripples of the Past 1.21.1 core API.
The donor's textures, logo, sound declarations and language files are redistributed under the
donor's GPL-3.0 license; the default Stand skin (`stand_skins/survivor`, its icon a copy of the
donor's power icon) and the config screen language keys are added for the 1.21 layout. Port code is
provided under GPL-3.0-only.
