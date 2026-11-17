# RotP - Gold Experience Requiem (`geraddon`): source and license

## Public release

- Module: `rotp-gold-experience-requiem`, mod id `geraddon`, version `1.0.0-port.5-candidate.31`.
- Original 1.16.5 add-on: see the record below (original authors: Inza).
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-11-14); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Gold Experience Requiem source and license

### Canonical merged source

- Logical unit: production rows 90 and 91, merged as one addon
- Legacy mod id: `geraddon`
- Legacy version: `1.0.0-AMDFix`
- Canonical merged decompile: `RotP-GoldExperienceRequiem-1.0.0-AMDFix-Canonical-Merged`
- Canonical visual donor JAR SHA-256: `441DBB27E1A113983EF17D656EC56538B5AD91DB257C51C103A239A8EA6833CC`
- Canonical gameplay donor JAR SHA-256: `1318E2C318DA50084A5E0152EA213E313AB3687C36EB4F729170706D7569460C`
- Canonical visual evidence tree SHA-256: `BEDD3E25193D29DBE9DC35B3E9DF963F3DAF7F04FC3909F99377DEB700C430C8`
- Canonical gameplay evidence tree SHA-256: `E90A68E07CFF70663F1BF5BC5B2F195F40D07919C732688D77DA6822A3DCE6BD`
- Decompiler: CFR 0.152
- Legacy author: Inza
- Legacy license: GNU GPL v3

The gameplay donor is authoritative for registrations, mechanics, sounds,
language, and non-model resources. The visual donor is authoritative only for
the GER geometry, skeleton motion, and active Stand texture. Duplicate
evidence directories are byte-identical copies and do not create additional
modules.

### Port scope

This module preserves the ten legacy action registry IDs, one Stand, sixteen
direct sound assets, the complete current Gold Experience utility suite, the
legacy evolution trigger, Life Shot variants, Disabler, the Undie death loop,
resolve-gated flight, Return to Zero, and every reachable resource surface.
Gold Experience evolution remains a server-owned Beetle Arrow interaction
through `StandPowerTransitions`.

The current core humanoid contract owns punch, barrage, heavy punch, charged
heavy, guard, grab, grabbed attacks, throw, and automatic release. Life Shot
uses the public selected-lifeform API. Disabler is bounded to loaded entities
and public action state. No unrelated action or leap class is injected.

### Return to Zero

Return to Zero is player-reachable from the special wheel before Disabler. Its
Shift variation sets the mark. The donor values remain 160 cooldown ticks, 200
stamina, and an eight-chunk mark radius; there is no additional donor resolve
gate.

The server stores one transient, owner-scoped mark with day time, player
inventories and selected slots, player and Stand transforms, full non-player
and non-Stand entity NBT, relationships, periodic pose frames, and first block
preimages. The history is bounded to 200,000 block operations, 1,200 entity
frames sampled every ten ticks, 256 transported ghost entities, and 120 ghost
points per entity.

Execution first stages and validates the entire plan, including dimension,
loaded-target, block-state, block-entity, entity-type, and recreation checks.
Only then does one guarded commit apply blocks, entities, players, Stands,
relationships, and day time. An inverse snapshot is captured before mutation
and applied if any commit step fails. Preflight failure consumes no mark,
stamina, or cooldown. Commit failure restores the exact previous stamina and
does not set cooldown or consume the mark. Successful completion alone
consumes the mark, sets cooldown, applies the scoped time-stop interaction
below, and starts the synchronized presentation.

#### GER and time stop gameplay extrapolation

The manga does not establish how Return to Zero resolves simultaneous core
time-stop fields. The following is a canon-guided gameplay extrapolation, not a
manga-confirmed rule. A GER mark snapshots module-owned monotonic generation
keys for active public `TimeStopState` instances without creating an absent
attachment. Core instance IDs may reuse the time-stop user's entity ID, so a
same-user restart receives a new generation and cannot alias a pre-mark
instance. After a successful commit, RTZ terminates only a generation absent
from the mark whose chunk area intersects the marked rewind area. An instance
already active at the mark and a later unrelated non-intersecting instance are
preserved. A non-positive core chunk range is global and therefore intersects
the mark.

When a new intersecting generation freezes the marked GER owner, the public
`TimeStopLifecycleEvent.Added` hook queues one autonomous response for the next
server tick. Execution occurs outside the core Added commit stack. Before the
queue is consumed, the server revalidates the exact same mark, its one-tick
maturity, the owner's dimension, life and actual frozen state, a still-active
post-mark intersecting generation, GER identity, configured and unlocked RTZ,
cooldown, stamina, and summoned-Stand health and stun state. It then initializes
the existing RTZ action, performs it once, and clears that action. A failed gate
is not retried for the same Added event and leaves the time stop and mark
unchanged.

This autonomous entry bypasses only the fact that a frozen player cannot send
the manual input. It does not register a movement authorizer, alter stopped-time
awareness, grant a status-effect immunity, permit manual frozen-time actions,
or stop a pre-mark or non-intersecting instance.

Each selected instance is terminated through public
`TimeStopState.removeInstance(int)`. Core time-stop cooldown settlement,
stamina refund rules, entity and gamerule restoration, client synchronization,
awareness updates, audio/effect cleanup, and lifecycle hooks therefore remain
on the normal core path. King Crimson Time Erase capture, termination, and RTZ
presentation-mode selection are unchanged.

What a successful press restores follows the project owner's rule of
2026-11-08. The initiator keeps his place; his inventory rewinds, his health
and absorption go back up to the mark's, and his fire is put out. Of the
effects he gained since the mark the harmful ones are taken off and the others
stay, Resolve's among them; those he had at the mark stay as they are (the
coordinator, R772.1). His own Stand stays as it is. A living
entity of his side (any that is not his enemy) keeps its place and its seat,
loads the marked NBT otherwise, and has its health and absorption raised to
the mark's; one that died since the mark is recreated. His enemy (a hostile
creature, or one that has him or one of his own as its target) goes back to
its marked place and loads the marked NBT, but keeps its present health and
absorption, its harmful effects and its fire; one that died since the mark
stays dead, and a living one has its AI paused for 10 ticks through the core's
`MobAiPause`. Another player goes back to his marked place with his marked
inventory and keeps his health and effects. A living entity whose end nobody
saw (it left or despawned) is not recreated; missing non-living entities are
recreated. Entities absent from the mark are removed inside the marked area.
The day time goes back to the mark's when the level's time runs at its normal
rate or slower, or when a player who is ahead of the initiator in his own time
(Made in Heaven's user) stands inside the marked area.

### Owner rules of 2026-11-08

These are the project's own rules, not donor behaviour.

- A summoned Requiem returns to zero, by itself, a hit that would take more
  than 7% of its user's maximum health after armour, Resistance and
  protection enchantments (10 stamina a point of damage), and a lasting
  harmful effect or a fire (50 stamina); the attacker's shot is removed, its
  running action is cut and it stands again where its attack began. For the
  Disabler's 9 seconds every hit and effect on its user is returned this way
  with no threshold and no cost, and enemies the Disabler holds make no
  special attack.
- The death loop starts only from a Finisher Punch whose true damage reaches
  the victim's health and absorption; otherwise the punch lands once as true
  damage. No totem, invulnerable state, damage cap or cancelled hit takes a
  victim out of the loop; a Requiem holder is never put in it.
- Every hit of Requiem's Stand has a damage type of the module
  (`geraddon:requiem_attack`, `requiem_explosion`, `requiem_life_shot`,
  `requiem_true`, `death_loop`) tagged to pass shields, the frames after a
  hit, vanilla's invulnerable states, Resistance, and the core's guards
  (Hamon Protection among them), invulnerable states and damage caps. What
  lessens a hit as armour does (a Stand's resistance by its durability,
  Resolve) is passed by true damage only: `requiem_true`, the Finisher
  Punch's hit, and `death_loop`. Creative and spectator players stay out,
  which the module sees to itself. The holder's own melee while Requiem is
  out passes the core's defences through the rule the module registers with
  `DefencePiercing.registerAttackerRule` (never a Stand's body), and a
  shield, the invulnerable flag and Resistance through the module's
  handlers; vanilla's other invulnerable states hold against it, as its
  vanilla damage type cannot carry vanilla's tag.
- Bites the Dust on a user whose Requiem is out is defused as it would go off.
  A full Time Erase that starts within 8 chunks of a summoned Requiem's user
  is refused, King Crimson's step within 4; an eraser who comes within 4
  chunks in a running erase is thrown out of it. A Requiem holder sees an
  eraser and is not moved to his shifted image.

### King Crimson's hooks and the fatigue (R772.1)

When King Crimson is loaded the module registers two rules with it at
setup, both through `GerRtzRewindService.KingCrimsonBridge` behind
`ModList.isLoaded`: Epitaph's exemption (`EpitaphPredictions.registerExemption`,
a Requiem holder is told of a prediction aimed at him and never walked), and
the answer to a creature's erase (`KingCrimsonCreatureErase.registerAnswer`):
the erase of a creature that fights a holder, or the escape of one his hit
would kill, is returned to zero as it would begin, with Requiem's effect on
the creature, whatever the range and with his Stand away. A press of Return to
Zero first asks `EpitaphPredictions.breakPrediction`; a press that breaks a
prediction does nothing else (no rewind, no stamina, no cooldown, the mark
kept). Only a key press asks it: Return to Zero's answer to a stop of time
answers the stop and leaves a prediction aimed at him as it is. In the core's mode without cooldowns Return to Zero counts in the
fatigue as an ordinary skill: a press and its answer to a stop of time both
reach `setCooldownOnUse`, which records the use.

Block recording is deliberately restricted to public NeoForge events: break,
entity place and multi-place, fluid placement, tool modification, farmland
trample, explosion detonation, piston pre, crop grow pre, neighbor notify, and
player right-click block. A mod that performs an arbitrary direct
`level.setBlock` call without any of these events is outside the recorder.
This limitation is explicit because the module does not add a global block
mixin.

### Visual provenance

#### New GER model (anime and manga builds)

The design source of the new GER model (the `ger_stand` default and the
`ger_manga` skin) is the original work, JoJo's Bizarre Adventure Part 5 (manga
and anime). JCraft's GER model served only as a reference for how the crest is
laid out in blocks (structure and proportions); no JCraft file, value or
texture is used.

#### Day_GeE's resource pack: `ger_pack`, and the default's clips and icons

On 2026-11-05 the default skin became the model, clips, entity texture and
icons of the resource pack "Gold Experience Requiem Model Optimization", the
work of **Day_GeE** for the 1.16 addon, handed in by the project owner with the
player tests of that day (archive SHA-256
`88F7EA204FEA45466029BF3EE423EC26FD40EBABD7DECED931E434D84B34A696`). It is a
modified work: its rig and clips build on the GNU GPL v3 donor below. Day_GeE
asked to be credited by that name, through the project owner, on 2026-11-07,
and is: the `skin.json` of the skin `ger_pack` names him in its `authors`. The
pack carries no licence file of its own. It is offered under GNU GPL v3 by
Day_GeE's permission, as relayed by the project owner.

Since 2026-11-14 the pack's model and texture are the selectable skin
`ger_pack` (`stand_skins/ger_pack/`, the same bytes as before) and the default
`ger_stand` is the new model above. The default keeps the pack's clips,
unchanged: its animation file is the same file as `ger_pack`'s, so the credit
to Day_GeE covers them. Its icons that show the Stand itself (`stand_icon.png`,
`manual_control.png` and the punch, heavy punch, barrage, Undie punch, grab and
release icons in `textures/ability/`) were redrawn on 2026-11-14 after the new
model, keeping the pack's poses and framing; the effect and item icons are still
the pack's copies, credited as above. `ger_manga` carries its own `stand_icon.png`
and `manual_control.png` with the manga head; its other icons fall back to the
default's.

`migration/pack-model-optimization/install_pack_skin.py` fits the pack to this
core and records every input and output in `pack-model-optimization.json` beside
it (its paths follow the move to `ger_pack`; it was not re-run, see its note):
the 1.16 geometry is divided by 2.01 (1.16 scaled a Stand by its 0.35 x 1.05
entity size, this core by `skin.json`), the bones become the core chain, and the
head's look moves to `head_rot` over a numeric counter-rotation on `head`; each
clip is proved against the pack's rig before anything is written. The 75 cubes
keep their UVs and the 512x512 texture its bytes. The pack has no barrage, aimed
pose or grab clips and no guard or grab icons; those stay the former default's.

#### Skins removed on 2026-11-14

`ger_original` (the model migrated from the 1.16.5 source, the default from
2026-09-12 to 2026-11-05), `ger_classic` (the 113-cube default before it),
`ger_custom` (a sculpted Blockbench model) and `ger_refined` (a cube-only remake
of the 113-cube model) are no longer in the add-on or in its artifacts. They
rested on the GNU GPL v3 donor below. Their skin folders, with the files edited
for this change, are kept outside the repository in
`install-2026-11-14-before`, with a
`MANIFEST.txt` (path, size, SHA-256). The modeling drafts and tools that made
them stay under `modeling/` and `migration/tools/` for provenance only and are
not packaged; the earlier Blender mesh drafts, together with the Gold
Experience reference copies they used (GNU GPL v3, from the current local
[ROTP core source](https://github.com/Campione01/Ripples-of-the-Past-1.21.1-All-in-One)),
are parked under `modeling/superseded/`. Runtime required-bone, canonical
transform and visible-region checks remain in place; baked cube counts may
differ for valid selectable skins. A future friend-authored skin will use a
different skin ID and is not represented by an empty placeholder.

The shared Shift+RMB key intentionally resolves GER moveset-local
`heavy_charged` on hold and `grab` on click. Both have the exact power type
`geraddon:ger_stand`; the core ability implementations do not change their
resource owner. The prior UI regression was binary icon reuse: GER's
`heavy_charged.png` matched C-Moon bytes, while the grab family matched D4C
punch/barrage/heavy bytes. Those reachable and contextual icons now reuse
GER's own punch, barrage, and heavy-punch art. The exact resolved tuples,
resource paths, and rejected foreign hashes are recorded in
`migration/manifests/resolved-control-surface.json`.

Life Shot beams and Disabler/evolution fields are synchronized exact-owner
entities. Their renderers use `StandVisualContext.world`,
`AlphaMultiBufferSource`, and the core Stand translucent material.

Disabler and RTZ each use a separate core `AddonPostEffect` lifecycle. RTZ
normal mode supplies the donor gold aura, golden fog, five bounded particle
storms, loop/fade/end audio, and invisible ghost history. TIME_ERASE mode
activates the donor cosmic and bad-signal look through the legal
`geraddon:shaders/post/rtz_bad_signal.json` four-pass PostChain and renders the
bounded ghost history after particles. Client settings can select NORMAL,
TIME_ERASE, or OFF for each server presentation type.

The server captures the public King Crimson Time Erase state before the
successful rewind closes temporal effects and carries that mode in the RTZ
start payload. A silent client diagnostic snapshot exposes a monotonic
presentation sequence and mode-specific render submission receipts for
runtime validation without changing the presentation.

The RTZ program resources were moved out of the archive and transformed only
as required by the 1.21.1 PostChain contract: obsolete camera varyings and
unlinked uniforms were removed, donor stages were linked through two generated
program JSON files, and the result blits back to the Minecraft main target.
The Disabler chain remains active and independent. Only the forbidden
core-namespace Gold Experience animation override remains inert under
`assets/geraddon/legacy_archive`.

There is no reflection, mixin, nearest-owner lookup, PlayerRenderer layer,
Iris/Sodium/Super Resolution implementation hook, framebuffer replacement,
direct GameRenderer shader takeover, or world-render cancellation. Exact donor
and transformed hashes are recorded in
`migration/manifests/legacy-resource-retention.json`.

No in-game visual, multiplayer, Iris, Super Resolution, or release-complete
claim is made until the runtime evidence matrix is executed.

### License

This derivative port and copied canonical resources are distributed under GNU
GPL v3.0 only (`GPL-3.0-only`). No warranty is provided.
