# RotP - Echoes (`rotp_echoes`): source and license

## Public release

- Module: `rotp-echoes`, mod id `rotp_echoes`, version `1.0.0-act1-3-port.18`.
- Original 1.16.5 add-on: `RotP-EchoesACT1-3Addon-1.0.jar` (Rotp-Echoes, mod id `myrotpaddon`), by Grasshopper,Day_GeE; declared license: GNU GPL v3.0; SHA-256 `C746A7304AF1515F2BD3DC3FFBBFEC6AC73D7EADEE64408EC29F255DBC1D170F`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-11-14); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module ports the Echoes 1.16.5 donor identified below. The
September 2026 donor update
records its inventory and freeze-policy differences.

**Day_GeE** is the author of the Echoes add-on and its models, and "the author"
below means Day_GeE. By the author's agreement, as relayed by the owner of this
port, the work is used here under GPL v3.

- Source artifact: `RotP-EchoesACT1-3Addon-1.0.jar`
- Artifact bytes: `844677`
- SHA-256: `C746A7304AF1515F2BD3DC3FFBBFEC6AC73D7EADEE64408EC29F255DBC1D170F`
- Previous donor SHA-256: `8CEF2D458F55777FF1DDF23A5D55B253986738AB49550A0598FD6EF99A0BA030`
- Original mod ID: `myrotpaddon`
- Original display name: `Rotp-Echoes`
- Original metadata license: `GNU GPL v3.0`
- Original Minecraft range: `1.16.5,1.17)`
- Original Forge loader range: `[36,)`

The locked donor contains ACT2 and ACT3 classes, registrations and resources.
ACT1 is absent from that artifact despite its ACT1-3 filename.

The donor has 118 ZIP entries, 92 file entries, 46 class entries, 38 Java
source units, and 46 non-class files. `InitPotions` and `FreezeEffect` remain
dead: their deferred register is not attached to the mod event bus and no
reachable code uses the effect. The new `FreezeHelper` event listener has no
caller that activates its freeze state, so the port adds no activation path.

The current port registers `rotp_echoes:echoes_act0`, `rotp_echoes:echoes_act1`,
`rotp_echoes:echoes_act2` and `rotp_echoes:echoes_act3` in
[EchoesEntities
and EchoesStands;
RotpEchoes attaches both
registers to the mod event bus. The donor inventory above covers ACT2 and ACT3.
Since R772 the gameplay follows the author's latest build
(`RotP-EchoesACT-master`): the arrow gives ACT0, the egg, which hatches into
ACT1; the ACTs carry his statistics; an ACT selector on the skill bar switches to
any ACT the Resolve level has reached (all in Creative), keeping the Resolve
level and changing the Stand where it stands. It replaces the donor's evolve and
degrade steps, whose unsafe background threads and command-based Stand swaps the
port had already replaced by a server action. ACT1 has his sound actions (the
sound picker, the self sound, sounds on block faces and sounds given to a living
target) and his Sound Give Punch, the heavy punch's finisher that also gives the
chosen sound, ACT2 his onomatopoeia on living targets and on block faces (as marks
or, in the entity form, as things) and ACT3 his cancel actions and self hand
freeze. ACT2's and ACT3's finishers are his too: the finisher variations of their
heavy punches, six hits on the aimed target, ACT3's then freezing it, in place of
the donor's skill-bar finishers. His voices, stingers, selector panel, icons, the word models, the mark
pictures, the sound haunt overlays and the sound picker's mob icons (which he
shipped in the minecraft namespace, here the addon's own) are copied byte-exact; the
master's `common_summon565656.ogg`, which its sound table never reached, is kept
under `legacy_archive`.

ACT2 and ACT3 use addon Stand entity types and a narrow model/renderer extension
of the core Stand pipeline. The idle offset is the core default, which the
donors kept: 0.75 behind the user's right shoulder, feet 0.2 above the user's
feet, turned with the view yaw.
Donor icons, sounds, and locales are migrated into the current resource layout.

Since R772 the four skins carry the Echoes author's own 1.21.1 models, clips,
textures and Stand icons, taken from his 1.21.1 Echoes
(`java-1.21-template-main.zip`, sent with his R772 report) and installed by
install_author_skins.py,
which records every input and output in
author-1211-skins.json.
They are the rigs of his latest 1.16.5 build (`RotP-EchoesACT-master`, models
dated 2026-10-25) on the 1.21.1 RotP skeleton, and replace the 2026-09-16
Blockbench rigs. Since R772.1, at his request, every ACT plays his 1.21.1 clips,
which drive the head themselves, at his scale of 0.65 x 1.95, and the Stand
entities have the same size. The light attack is his four punches, in the order
of his clips `punch` to `punch4`, as his `AddonStands` declares them, through
the core's light combo. The installer's changes to his clips are the ones this
core needs, listed at its head. Each skin's O slot icon is a copy of his Stand
icon of that ACT, every ACT carries his attack and guard icons, and ACT3 also his
`heavy_charged`, `gravity_infuse` and `hand_freeze` icons and his charged-heavy
sound. ACT1's finisher keeps the icon of his 1.16.5 Sound Give Punch and ACT3's
the donor's freezing one, both finishers of ours whose moves his 1.21.1 icons,
copies of his heavy punch, do not show.

Since R772.1 ACT3's code also reuses Day_GeE's 1.21.1 code from the same project
(`java-1.21-template-main.zip`, package `com.grasshopper.rotp_echoes`), sent to
the owner of this port for that purpose, adapted to this core rather than copied.
From his `GravityItemHelper` and `GravityItemEvents`: gravity on an item with an
owner and no timer, an item on the ground losing it when its owner is out of
range, the throw as a real flight that hits what is in its way, and the particles
on the item. From his `DisablerTagHandler`: a frozen entity dealing no damage and
taking no knockback, the self freeze's press to the ground, and the freeze's
range measured from its user. From his abilities: refusing a press
with nothing to do, moving a freeze to whoever is in the aim, either cancel key
lifting either freeze, the cry at the start of the charged heavy, and the bearing
shot. From his `FreezeSoundHelper`: a voice on every press. From his
`EchoesClientSoundEvents`: the barrage voice repeating for as long as the barrage
lasts. The numbers stay those of his 1.16.5 master where it has them. The block
mark, the finisher's freeze, the hand-freeze restrictions, tick-based timing and
the restored flight remain this port's. From his 1.16.5 master, by the same
permission, comes the rule that freezing Killer Queen's Sheer Heart Attack freezes
its owner's hands, and the self freeze stays that build's toggle, draining stamina.

Two clips are not in his 1.21.1 skins, because our abilities need them and his
build has no such moves; for the author to replace if he wishes:
install_master_clips.py
retargets them from his 1.16.5 build onto these rigs (inputs, bone map and
playback check in
master-1165-clips.json).
They are ACT2's `launch`, for the tail needle and the word attach, from his
`forceLaunchPose`, and ACT1's `block`, the guard, whose waist stands on his ACT1
idle's. ACT3 guards with the core's default clip, which does not fit ACT1's tail.
ACT2's `block` is this port's own, by the rules of `tools/stand-animation-rules`:
the default clip's hands do not reach the middle on his short arms. ACT3 has his
grab moves; its throw starts from
his grab's hold pose and shows his grab icon. The ACT3 barrage plays one voice at a time, picked from his two barrage
voices; the donor's third clip, which its sound table never reached, is kept
under `legacy_archive`. Five locales
come from the current donor; fifteen earlier translations are retained. Dormant
donor resources are retained byte-exact under
`assets/rotp_echoes/legacy_archive` without runtime registration.

The addon registers no mixin or global render takeover; since R772 it has three
packet channels (the ACT selector's choice, ACT2's words and ACT1's sounds). Detailed source inventory, state ownership,
input policy, rendering ownership, and runtime validation gaps are recorded
under `migration/manifests/`.
