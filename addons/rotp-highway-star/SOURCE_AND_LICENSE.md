# Highway Star Stand (`highwaystar`): source and license

## Public release

- Module: `rotp-highway-star`, mod id `highwaystar`, version `1.0.0-port.1`.
- Original 1.16.5 add-on: see the record below (original authors: Grasshopper).
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-10-29 to 2026-11-04); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Highway Star migration source and license

- Donor: `RotP-Highway Star-1.0.jar`, inside `mods (2).zip` in the 2026-10-29 test-evidence folder
- Donor SHA-256: `A94AB835B2104857D056AF485021ED490C90D480D440071177EBC2F29428C6F9`
- Donor platform: Minecraft 1.16.5 / Forge 36 / Ripples of the Past 0.2.x
- Donor mod id and version: `highwaystar` / `1.0.0` (jar manifest version 1.0)
- Donor author: Grasshopper (support https://ifdian.net/a/mazaNB666)
- License: GNU GPL v3.0, as the donor's `META-INF/mods.toml` states (`license="GNU GPL v3.0"`)
- Provenance: the author tested the owner's 1.21.1 build on 2026-10-29 and sent this add-on with
  the mod pack for porting.

The Java implementation is a clean NeoForge 1.21.1 port onto the ROTP core's ability, Stand skin and
Stand entity API. The donor's models, animations, textures, sounds, sound declarations, icons, logo and
language files are redistributed under the donor's GPL-3.0 license. The default Stand skin under
`assets/highwaystar/stand_skins/highway_star` carries the donor's model and clips with the bones renamed
to the core's humanoid Stand layout; the donor files themselves stay unchanged under
`assets/highwaystar/geo` and `assets/highwaystar/animations`.

`assets/highwaystar/textures/ability/manual_control.png` is the core's own manual control icon
(`jojo_ripples`, GPL-3.0), copied so that the add-on's namespace resolves it.
