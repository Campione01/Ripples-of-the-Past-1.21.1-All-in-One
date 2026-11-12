# Asset Provenance and Publication Scope

## Retained assets

The retained models, animations, textures, sprites, structures, language files,
data files, sound effects, and voice assets were inherited from the two
upstream Ripples of the Past repositories or were developed in this independent
working tree.

Known upstream asset contributors are:

- Yujin
- WhiteMind
- August_dr
- KWiNTA
- iIIiBlackiIIi
- Motvik

Detailed contribution roles are preserved in [NOTICE.md](NOTICE.md).
Campione01 does not claim original authorship of inherited upstream assets.

## Stand Aura FX shader source

The `stand_aura_composite` shader is a Minecraft 1.21.1/GLSL 150 adaptation
of the GPLv3 StandAuraFx shader by KINnaoinza, fixed at revision
`6f36008b37bc7165a8c1fd594b246923557dc417`:
<https://github.com/KINnao087/StandAuraFx/tree/6f36008b37bc7165a8c1fd594b246923557dc417>.
The adaptation retains the upstream SDF/noise/color algorithm and moves
framebuffer ownership into ROTP's existing exact-entity mask compositor.

## Grab ability icons

The grab icons of Star Platinum, The World and Crazy Diamond
(`stand_skins/<stand>/assets/jojo_ripples/textures/ability/grab.png`) adapt
the grab icon of the GPLv3 RotP Echoes add-on for 1.21.1 by Grasshopper and
Day_GeE: its zombie is kept, and the gripping arm is redrawn with each Stand's
arm from its own punch icon.

## Deliberately excluded assets

The Git snapshot excludes:

| Path | Reason |
| --- | --- |
| `src/main/resources/User content templates/` | Optional Blockbench and clothes-pack development examples that are not required at runtime. |

The exclusions are implemented in `.gitignore`; the local development copies
are not deleted.

## Historical sound sources

The original 1.16.5 metadata records these sound-effect sources:

- Spybreak001: <https://youtube.com/user/Spybreak001>
- dendy: <https://www.youtube.com/channel/UCfFQSPN-Zxx8jEALchUPpPw>
- Folzy SFX Sound Design
- Supah X:
  <https://www.youtube.com/channel/UCMmaTmRcF9UYb_Kf-wGT1eA/videos>
- JoJo SFX Green Screen & Blue Screen:
  <https://www.youtube.com/channel/UCkxl6FSocj8mHEIJRw5KjYg>
- PassioneSound: <https://www.youtube.com/@PassioneSound>
- JoJo's Sound Design Discord server

The original metadata identifies voice lines from *Eyes of Heaven* and
*All Star Battle*, sourced through <https://sounds-resource.com/>.

<!-- OFFCLOTH -->
## Clothes (official sources of 2026-11-12)

Taken from the official Ripples of the Past for NeoForge 1.21.1 sources the
owner received on 2026-11-12, with the owner's permission to use official
assets directly:

| Path | Source and author |
| --- | --- |
| `assets/jojo_rotp_clothes/geo/clothes/jotaro_p3_anime.geo.json` | official `geo_rotp/clothes/jotaro_p3_anime.geo.json`, model by Motvik (moved to this port's `geo` path) |
| `assets/jojo_ripples/textures/gui/container/clothes/inventory_clothes.png` | official clothes screen texture, 12 pixels taller for the character's name |
| `data/jojo_ripples/ripples_clothes/character/*.json` (60 added files) | official character files; Stand ids mapped to this port's core and add-ons |
| `data/jojo_ripples/ripples_clothes/clothes/jotaro_p3_{anime,manga}.json` | official files with `"authors": "Motvik"` |
| `assets/jojo_rotp_clothes/lang/{en_us,ru_ru}.json` character names | official; `josefumi.short` corrected to "Josefumi" |
| `assets/jojo_rotp_clothes/lang/{zh_cn,zh_tw}.json` | written for this port |

<!-- OFFVOICE -->
## Voice packs (official sources of 2026-11-12)

Taken from the official Ripples of the Past for NeoForge 1.21.1 sources the
owner received on 2026-11-12, with the owner's permission to use official
assets directly, anime voice clips included:

| Path | Source |
| --- | --- |
| `assets/jojo_ripples/sounds/stand_user/jotaro_yare_yare_{1,2,3,4}.ogg` | official clips, unchanged |
| `assets/jojo_ripples/sounds/stand_user/jotaro_yare_yare_daze_{1,2,3}.ogg` | official clips, unchanged |
| `assets/jojo_ripples/sounds/hamon_user/joseph_scream_shooting.ogg` | official clip, unchanged |
| `assets/jojo_ripples/voicelines/{jotaro_p3,josuke4,joseph_p2}/data.json` | official files, unchanged |
| `assets/jojo_ripples/voicelines/{jotaro_p3,josuke4,joseph_p2}/sounds.json` | official files; subtitles point to this port's existing keys, and `vl_time_stop` is limited to Star Platinum |
| `jojo_ripples.subtitle.{star_finger,yare_yare,yare_yare_daze}` (en_us, zh_cn, zh_tw) | written for this port |

<!-- OFFMENUS -->
## HUD and menus (official sources of 2026-11-12)

Taken unchanged from the official Ripples of the Past for NeoForge 1.21.1
sources the owner received on 2026-11-12, with the owner's permission to use
official assets directly:

| Path | Source and author |
| --- | --- |
| `assets/jojo_ripples/textures/hud/stand_resolve/stand_resolve_{stages_h,stage_[1-4]_h,stage_unlocked[1-4]_h,lvl_[1-4]_h}.png` (13 files) | official `textures/hud/stand_resolve/`, same names; the vertical and `_tph` files the official never draws were not taken |
| `assets/jojo_ripples/textures/gui/container/cartography_table2.png`, `cartography_table2_name_field.png` | official explorer map table screen |
| `assets/jojo_ripples/textures/gui/cartography_vanilla.png`, `cartography_explorer.png` | official cartography table mode icons |
| `assets/jojo_ripples/textures/map/decorations/hamon_temple.png` | official hamon temple map icon |
