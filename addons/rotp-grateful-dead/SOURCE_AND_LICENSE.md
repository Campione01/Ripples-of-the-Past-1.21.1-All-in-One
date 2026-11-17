# RotP Grateful Dead (`rotp_grateful_dead`): source and license

## Public release

- Module: `rotp-grateful-dead`, mod id `rotp_grateful_dead`, version `0.1.13-port.15`.
- Original 1.16.5 add-on: `RotP-Next-Album-0.1.11.jar` (RotP:NextAlbum, mod id `rotp_nextalbum`), by Babelmoth; declared license: GNU GPL v3.0; SHA-256 `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-11-14); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Grateful Dead Source and License

This module is a bounded NeoForge 1.21.1 migration of the Grateful Dead
logical unit from `RotP-Next-Album-0.1.11.jar`.

Canonical artifact:

- Path: `RotP-Next-Album-0.1.11.jar`
- Size: `2258019` bytes
- SHA-256: `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`
- Legacy mod id: `rotp_nextalbum`
- Legacy implementation version: `0.1.11`
- Legacy author: `Babelmoth`
- Legacy license: GNU GPL v3.0

The CFR 0.152 decompile at
`025_RotP-Next-Album-0.1.11`
was used to recover behavior and registration topology. Canonical binary
resources were read from the locked JAR and its extracted baseline.

The port is distributed under `GPL-3.0-only`. Original code and assets remain
attributed to Babelmoth and the original Next Album contributors; Ripples of
the Past and its current addon API are attributed to StandoByte and their
contributors.

Touch, aura and remove-aging actions operate on the user's action performer;
they do not require or automatically summon a Stand body. A target's own aging
is cleared immediately on death, independently of fading aging cast by that
target. These follow the canonical StandAction inputs and AgingEntityEvents
death cleanup. Ordinary aging rates, aura ownership and block fading remain
separate from those entry and lifecycle rules.

Touch lasts at most `max(40, floor(60 + durability * 6))` ticks, using the
Stand's current developed durability. Movement during its perform phase is
60 percent of ordinary input speed; releasing the button starts recovery and
restores the normal multiplier.

Incoming aging damage preserves the canonical multiplier. A finite input that
would overflow the float range saturates at that range instead of introducing
infinite damage into Minecraft's damage pipeline.

The default Stand atlas is `RotP-Next-Album-0.1.13.jar`'s own, every pixel a 2x2
block on a 256x256 sheet, so the Stand looks as it did in 1.16.5. The aging overlay
is a 256x256 derivative: an AI image-generation edit supplied material and
color reference, after which a deterministic local post-process preserved the
canonical alpha and UV silhouette and rebuilt material regions. The migrated model
takes 0.1.13's 34 cubes and doubled donor UVs while using current core bone names;
the head and body remain independent at their donor action pivots. The 32x32 ability icons are
Babelmoth's own `textures/action` icons from `RotP-Next-Album-0.1.13.jar`
(SHA-256 `C5F70C6852D7C2D5781CF13756F0EE857AF3A41405E61F7AF6F02AB6DDC45175`), enlarged
2x nearest-neighbour; its put-down icon serves the core grab release. Only `heavy_charged`
and `manual_control`, which 0.1.13 does not have, stay module-local pixel-art derivatives
using that same palette. The Stand's summon and unsummon sounds and Prosciutto's shouts are
that jar's exact files. Geometry and
animation JSON were structurally converted to the current Stand skin skeleton
and current action animation names. Its jab, strong punch, finisher, summon, grab,
aging and the wider barrage sweep come from `RotP-Next-Album-0.1.13.jar` through
`migration/tools/grateful_dead_0113_clips.py`; `aging` is carried for the donor's aging
ability and nothing plays it yet.

The three canonical aging-blur post chains are retained byte-for-byte under
the inert owner `legacy_archive`. Their active owner-namespace copies replace
the removed 1.16.5 `blur` program with Minecraft 1.21.1 `box_blur` plus linear
filtering while preserving the two-pass directions and 8/18/30 radii.

No custom shader program, mixin, global living renderer replacement,
PlayerRenderer cancellation, framebuffer replacement, chunk force-loading,
or optional scale-library dependency is packaged. Exact visual and gameplay
parity limitations are recorded in
`META-INF/migration/blocker-ledger.json`.
