# RotP November Rain (`rotp_november_rain`): source and license

## Public release

- Module: `rotp-november-rain`, mod id `rotp_november_rain`, version `1.0.5-mc1.21.1`.
- Original 1.16.5 add-on: `November-Rain-1.0.jar` (RotP Stand Addon, mod id `november-rain-addon`), by Ptitboo83,the one who will make the texture; declared license: GNU GPL v3.0; SHA-256 `EE86C1E4922B419AE5640538293CF732A50C8B1DEF1433A42DD4C560A9D1C109`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-11-04); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module ports the November Rain addon from:

- Source artifact: `[十一月雨]November-Rain-1.0.jar`
- SHA-256: `EE86C1E4922B419AE5640538293CF732A50C8B1DEF1433A42DD4C560A9D1C109`
- Original mod ID: `november-rain-addon`
- Original authors: `Ptitboo83, the one who will make the texture`
- Original metadata license: `GNU GPL v3.0`

The 1.21.1 port normalizes the mod ID to `rotp_november_rain`, migrates the
registration and action lifecycle to NeoForge 21.1, and adapts the original
resource model to the current ROTP stand-skin renderer.

November Rain plays as the 1.16.5 remake below (owner decision 2026-11-04),
which supersedes the 1.0 prototype's single Light Rain action.

The Stand's model, textures and poses come from the 1.16.5 remake of the
same addon, which supersedes the 1.0 prototype geometry (`geometry.unknown`,
six flat placeholder bones) and the stand-in model this port used before:

- Source artifact: `RotP-NovemberRain-Addon-1.0.4.jar` (from a player's 1.16.5 pack)
- SHA-256: `AC94F1CED51500AACC2622EDEB2DF5BEA24A2F7227E170A8517E44F33910DABA`
- Mod ID: `rotp_nr`
- Authors: `Ptitboo83 & Where`
- Metadata license: `GNU GPL v3.0`

`migration/convert-remake-geometry.py` converts the remake's
`assets/rotp_nr/geo/november_rain.geo.json` (bones named after the legacy
`HumanoidStandModel` fields) to the current Stand rig: every cube, pivot, UV
and per-cube rotation is kept, and the rig's helper bones are added at the
pivots of the parts they move. The texture (`textures/entity/stand/november_rain.png`),
the Stand icon (`textures/power/november_rain.png`) and the Light Rain icon
(`textures/action/light_rain.png`) are the remake's bytes. The clips in
`animations/november_rain.animation.json` restate the remake's
`NovemberRainModel` poses: the idle and summon poses with the head following
the look, and the RAIN_ANIM pose Light Rain holds while it rains, easing back
to the idle pose over the recovery.

The gameplay is the remake's (`com.ptitboo83.nr`, read from its class files):
its seven actions with their numbers (`light_rain` with the roof check, the
anvil damage and Slowness V; `heavy_rain`; `rain_shield`; `rain_barrier`;
`destroy_item`; `barrage_rain`; `precision_drop`), the `rain_drop` projectile
(its `RainDropModel` cube and `textures/entity/rain_drop.png`), the
`cut_eyelid` effect (colour 7997962, `textures/mob_effect/cut_eyelid.png`) with
its HUD overlay (`textures/effect/cut_eyelid.png` over the right half of the
screen), the summon sound (`sound/november_rain_summon.ogg`, here under
`sounds/` so that it resolves, and downmixed from stereo to one channel, the
average of the two, so that it is heard where the Stand is and fades with
distance), the action icons (`textures/action/*.png`) and
the Stand colour (1856637, `#1C547D`). The remake's LMB and RMB hotbars become
the core's standard controls: Light Rain on the left and Rain Shield on the
right mouse button, the other five actions in the skill bar, no other key bound.
Heavy Rain, Rain Shield and Large Rain Shield hold the RAIN_ANIM pose of the
`light_rain` clip; the other actions keep the idle pose, as their builders did.
Rain drops and Precision Drop break blocks only where the server allows it
(the core's block-breaking game rule and `JojoModUtil.canEntityDestroy`); the
remake did not ask.
Not ported: the remake's unused `rain_drop.geo.json`, `textures/misc/cut_eyelid.png`
and `textures/action/rain_drop.png` (no code reads them), its unused
`igniteTNT` helper and its empty `example_stand_ost` sound list.
