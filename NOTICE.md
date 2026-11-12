# Attribution Notice

## Project lineage

This repository is an independent modified work derived from:

1. **Ripples of the Past for Forge 1.16.5**, created by StandoByte:
   <https://github.com/StandoByte/Ripples-of-the-Past>
2. **Ripples of the Past for NeoForge 1.21.1**, ported by StandoByte:
   <https://github.com/StandoByte/Ripples-of-the-Past-1-21-1>

The official 1.21.1 source comparison used for the initial publication audit
was commit `1a68125557221d1461ab1ba01119815ef0ba206d` (2026-03-29).
The initial public snapshot still contains substantial files identical to that
baseline, in addition to adapted and independently modified files.

Substantial portions of the source tree retain upstream file organization,
Java package names, registries, data files, models, animations, textures, and
documentation. Those names are retained for compatibility and provenance; they
do not indicate that this independent repository is official or endorsed.

## Original authors and contributors

- **StandoByte**: original project, game design, and programming
- **Yujin**: Hamon tweaks, Pillar Man abilities, model animations, structure
  design/building, and 2D sprites
- **WhiteMind** and **August_dr**: Star Platinum and The World models
- **August_dr**: 3D models, player animations, and Hamon Master remodel
- **KWiNTA**: 2D sprites
- **iIIiBlackiIIi**: player animations
- **Motvik**: JoJo character clothes models
- **HK47bot** and **Weever**: code contributions

Historical translation credits preserved from the original project:

- Brazilian Portuguese: Complexo
- Chinese (Simplified): GeziGG233, Funnylovexx
- Chinese (Traditional): chongsanxiao
- Classical Chinese: voidzyy
- Italian: Giancarlo Esposito
- Japanese: SK, hatidotch
- Spanish: dhilansyt, ElTormakzXD
- Venezuelan Spanish: ElTormakzXD
- Ukrainian: kage0100

For the exact historical metadata and any supporter credits not reproduced
here, consult the upstream repositories and their release metadata.

## Independent modifications

**Campione01** maintains this independent source tree. The initial public
snapshot records modifications made up to and including **2026-07-26**.
Because the local working directory did not contain a valid upstream Git
history at publication time, the initial commit represents the complete
reviewed source snapshot rather than a commit-by-commit attribution boundary.
Known modification areas are summarized in [CHANGES.md](CHANGES.md).

## Repository status

This repository is not an official release, fork, or support channel of
StandoByte.

Third-party code notices are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Resource provenance and
exclusions are in [ASSET_SOURCES.md](ASSET_SOURCES.md).

<!-- OFFCLOTH -->
## Clothes from the newer official sources

The clothes system's list-and-merge character format, the 60 added JoJo
character files with their English and Russian names, the updated Jotaro Kujo
*Stardust Crusaders* (anime) clothes model, the taller clothes screen texture,
and the clothes-or-armor setting's text follow the official Ripples of the Past
for NeoForge 1.21.1 sources the owner received on 2026-11-12. The Jotaro
clothes sets credit their author, **Motvik**, in their `authors` field, and the
clothes item tooltip shows it. The Chinese character names, and the Stand ids
mapped to this port's core and add-ons, were written for this port.

<!-- OFFVOICE -->
## Voice packs from the newer official sources

The voice pack format (`assets/<namespace>/voicelines/<pack>/`), the three
packs `jotaro_p3`, `josuke4` and `joseph_p2`, and eight voice clips (Jotaro's
"yare yare" set of seven and Joseph's Tommy Gun scream) follow the official Ripples
of the Past for NeoForge 1.21.1 sources the owner received on 2026-11-12, with
the owner's permission to use official assets, anime voice clips included.
The packs here name this port's existing subtitle keys, and the Jotaro pack's
"Star Platinum, The World" line is limited to Star Platinum; the loader and its
merging of resource packs were written for this port.

<!-- OFFMENUS -->
## HUD and menu pieces from the newer official sources

The Resolve pips of the HUD's pip layout, the cartography table's explorer map
screen and mode icons, and the hamon temple map icon are textures of the
official Ripples of the Past for NeoForge 1.21.1 sources the owner received on
2026-11-12, used with the owner's permission. The tutorial toasts, the explorer
map mode and the Stand toggle HUD setting follow that version's behaviour and
were rewritten into this port's settings and checks.

<!-- lane OFFART (R772.1) -->
## Later official Stand art

Some Stand assets follow a later state of the official 1.21.1 port than the
baseline above: the archive of its resources the maintainer received on
2026-11-12. The files are listed in [ASSET_SOURCES.md](ASSET_SOURCES.md),
under "Official Stand art of 2026-11-12".
<!-- end lane OFFART -->

<!-- OFFPOSES -->
## JoJo poses from the newer official sources

The pose set format (`assets/<namespace>/jojo_pose/<set>/`), Jotaro's three
poses `point`, `cap` and `volume13`, and the pose list's slot sprites follow the
official Ripples of the Past for NeoForge 1.21.1 sources the owner received on
2026-11-12, with the owner's permission to use official assets. The cap pose's
"yare yare" lines moved from the pose data into a voice pack; the loader, the
bone table that puts the official rig's clips on this port's player model, and
the pose key were written for this port.
<!-- end OFFPOSES -->
