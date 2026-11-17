# RotP Speed King (`speedking`): source and license

## Public release

- Module: `rotp-speed-king`, mod id `speedking`, version `1.0-port.21`.
- Original 1.16.5 add-on: `RotP-speedking-1.0.jar` (速度之王, mod id `speedking`), by 1adolf1(B站超级气球真理部_official); declared license: GNU GPL v3.0; SHA-256 `2C7926A44071483156186E73E1C35C512BEAFB2FAD955618490348273A42F229`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-08-22 to 2026-11-14); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module ports the complete reachable content of the supplied Speed King donor.

- Source artifact: `RotP-speedking-1.0.jar`
- Archived donor: `RotP-speedking-1.0.jar`
- Archived decompilation: `099_RotP-speedking-1.0`
- Artifact bytes: `213988`
- SHA-256: `2C7926A44071483156186E73E1C35C512BEAFB2FAD955618490348273A42F229`
- Original mod ID and version: `speedking`, `1.0`
- Original metadata license: `GNU GPL v3.0`
- Original authors: `1adolf1`, with StandoByte credited

The donor contains 60 ZIP entries, 39 file entries, 19 class entries, and 20
non-class files. The port retains all 12 registered action IDs, the Stand and
pickaxe entities, heat injection, vision, cooking, grab, persistent heat-trap
placement and triggering, donor model and textures, marker, sounds, controls,
and resolve-level gates.

Speed King is explicitly `HYBRID_FORM`. Its body uses close-range direct
control with `effectiveRange=4`, `rangeMax=8`, `manualControl=true`, and
distance strength decay only for live body melee during real O-key manual
control. Heat trap and heat vision are independently owned abilities and do
not inherit body distance decay. The donor's 4/8 values are supplementary
implementation evidence; no stronger canonical meter value was supplied.

The punch, barrage, heavy punch and finisher are the core's Stand attacks, as
the donor registered the core `StandEntityLightAttack`, `StandEntityMeleeBarrage`
and `StandEntityHeavyAttack` (the finisher being the heavy punch's finisher
variation): their damage comes from the Stand's strength, they fill and spend
the finisher meter, the barrage clashes, the punch summons only the main arm,
the barrage both arms, and the heavy punch and finisher the whole Stand. The
guard is the core Stand guard (the donor's plain `StandEntityBlock`) and, like
it, brings out only the arms; the pickaxe throw and the heat moves bring out the
whole Stand.

Cooldowns and stamina follow the donor's `InitStands` builders and the 1.16
builder defaults: only heat vision has a cooldown (100 ticks); the heavy and
finisher punches cost 50 stamina and the light punch 10, paid as they strike
with the result ignored, so low stamina never refuses them. The barrage drains
4 per tick while it runs. Every hit is weakened by the Stand's stamina
condition. While heat vision's lease holds a target, its Glowing stands in for
the target's own; the original comes back with the ticks it would have run
meanwhile already spent, so it keeps counting down as it did in 1.16.

Not from the donor: a player's barrage ends on a tick it cannot pay. That is
the owner's rule for the core barrage (decision D-core-90); the 1.16 barrage
went on at 0 stamina.

The idle loop, the three summon poses and the guard are clips converted from
the donor `ExampleStandModel` (`initIdlePose`, `initIdlePose2Loop`,
`initSummonPoseRotations`) and the 1.16 `HumanoidStandModel` guard pose, and
the melee clips end on the donor idle and keep its legs, as 1.16 laid the idle
under every move and returned to it. Not from the donor: 1.16's idle loop also
eased the look itself, so the head nodded to twice the look every 4 s; here the
head stays on the look. The heat moves and the pickaxe throw keep their Java arm
poses (`SpeedKingModel`), which also gives this rig, whose bones keep the 1.16
names, the 1.16 motion tilt.

The skin's rig adds `leftArmXRot` and `rightArmXRot` above the arms, as the donor
rig has for its legs, so the barrage's arms follow the look's pitch as 1.16's
`armsRotationFull` did: `migration/arm_xrot_rig.py`, to be run after
`migration/update-from-donor.ps1`. The module's own geo stays the donor's bytes.
