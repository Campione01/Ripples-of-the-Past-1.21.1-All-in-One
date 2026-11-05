# RotP Beach Boy (`rotp_beach_boy`): source and license

## Public release

- Module: `rotp-beach-boy`, mod id `rotp_beach_boy`, version `0.1.13-port.1`.
- Original 1.16.5 add-on: see the record below (original authors: Babelmoth).
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-11-04 to 2026-11-04); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Beach Boy source and license

- Donor: `RotP-Next-Album-0.1.13.jar` (RotP:NextAlbum), from the archive `mods (2).zip` in
  `10.29rotp專項測試證據留存` (the add-on pack the player sent on 2026-10-29)
- Donor SHA-256: `C5F70C6852D7C2D5781CF13756F0EE857AF3A41405E61F7AF6F02AB6DDC45175`
- Donor platform: Minecraft 1.16.5 / Forge 36 / Ripples of the Past 0.2.2+
- Donor mod id and version: `rotp_nextalbum` / 0.1.13; only its Beach Boy Stand is ported here
  (the rest of the add-on is ported in `rotp-next-album`, `rotp-grateful-dead` and `rotp-sex-pistols`)
- Donor authors: Babelmoth (author), StandoByte (credited); issues
  https://github.com/Babelmoth/Rotp-NextAlubm-1.16.5/issues/
- License: GNU GPL v3.0, as the donor's `META-INF/mods.toml` states (`license="GNU GPL v3.0"`)

The Java implementation is a clean NeoForge 1.21.1 port of the donor's Beach Boy classes onto the Ripples of
the Past 1.21.1 core API (Stand id `rotp_beach_boy:beach_boy`, namespace `rotp_beach_boy`). The donor's
models (`geo/beach_boy.geo.json`, `geo/beach_boy_hook.geo.json`, the item models), animation, textures,
action icons, sounds and the Beach Boy part of its language files are redistributed under the donor's
GPL-3.0 license, moved from the `rotp_nextalbum` namespace to `rotp_beach_boy`. Added for the 1.21 layout: the
default Stand skin (`stand_skins/beach_boy`, its icon a copy of the donor's power icon), the ability sprites
(copies of the donor's action icons under the ability names), the damage types and tags, the item tags
that take the fishing rod's enchantments, and the ability name keys. Port code is provided under
GPL-3.0-only.
