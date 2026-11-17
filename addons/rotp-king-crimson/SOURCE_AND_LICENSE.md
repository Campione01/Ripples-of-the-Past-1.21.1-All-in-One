# RotP King Crimson (`rotp_kingcrimson`): source and license

## Public release

- Module: `rotp-king-crimson`, mod id `rotp_kingcrimson`, version `1.0.3-port.32`.
- Original 1.16.5 add-on: `RotP-绯红之王-1.0.3.jar` (RotP 绯红之王, mod id `rotp_kingcrimson`), by HelloThere, DeezQuartz, StandoByte; declared license: GNU GPL v3.0; SHA-256 `72528112AC68750B0499414626AFEBDF5695F4C554F185FDFE19B5845B60E2F0`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-11-13); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## King Crimson Phase 5 Source And License Record

Canonical input:

`RotP-绯红之王-1.0.3.jar`

SHA-256:

`72528112AC68750B0499414626AFEBDF5695F4C554F185FDFE19B5845B60E2F0`

Canonical CFR source:

`064_RotP-绯红之王-1.0.3`

The legacy metadata declares `GNU GPL v3.0` and credits HelloThere,
DeezQuartz, and StandoByte. This bounded port uses the SPDX identifier
`GPL-3.0-only`.

Phase 1 preserves the canonical Java model geometry, 1024x1024 default and
glow Stand textures, base action icons, Stand icon, and only the sound files
reachable from summon, unsummon, punch, barrage, and heavy punch. The
reproducible converter maps the legacy Java model onto the current native
semantic Stand skeleton, converts the two legacy idle poses and all five
legacy summon poses, and filters the pinned core's current shared combat/grab
animations to that skeleton.

The permanent small face modeled on King Crimson's forehead remains part of
the base Stand geometry. At the Phase 1 boundary, no Epitaph gameplay, player
layer, shader, or tracked state was included.

Phase 2 adds the independently closable ordinary physical slice: Chop,
Impale, single and triple projectile throws, Bleeding, Gaping Wound, and
Mangled Body. Their icons, impact sounds, voice lines, pose rotations, and
legacy timing fractions are regenerated from the same hash-pinned source.
Server code owns damage, stamina, cooldowns, item consumption, guard/shield
breaks, and wound behavior. The current core owns Stand visual context and
global transparency; this addon adds no custom renderer or global visual
state.

Phase 3 adds only Epitaph's independently closable prediction and one-hit
dodge slice. The server owns the serialized player attachment, resolve-based
prediction tier, 60-tick window, one safe relocation, cooldown and stamina,
expiry, stand-swap/death/logout/dimension cleanup, and tracked-player
snapshots. Login fails closed by clearing a persisted active window. This
phase originally deferred the legacy future Time Skip branch; the final donor
gameplay audit now closes that branch without changing the Phase 3 input.

The client adds a normal player render layer and first-person layer for the
byte-exact legacy Epitaph overlay. Their alpha and classic-outline decision
come from the core-owned Stand visual context and AlphaMultiBufferSource; no
player renderer or model is replaced or globally mutated. Specifically, the
layer uses `StandVisualContext.world` and `AlphaMultiBufferSource`. The
byte-exact legacy Epitaph PostChain is structurally namespaced to the current
core `jojo_ripples:color_convolve` program and registered through the
core-managed addon post-effect lifecycle. Its only reachable chain is
`rotp_kingcrimson:shaders/post/epitaph.json`. The addon records that exact
chain for Super Resolution's injected-chain blacklist but does not read or
write Iris or Super Resolution state.

Epitaph's estimated vision (2026-09-15, beyond the donor, which kept only the
reaction to a foreseen hit) drew a dark-red image where each creature was
expected to be, and from round 24 a fate held creatures to it. The estimated
vision and the fates are both gone since R772.1, with all their code: the
server estimates no path or flight, no creature is held to one, and no vision
image is drawn. The half-transparent drawing of those images (owner decision
2026-09-23) went with them, so the afterimage entity and its renderer draw Time
Erase afterimages alone, in the plain buffer under the dark red tint.

Since R772.1 Epitaph is a foresight of the owner's own design (2026-11-07 to
2026-11-11) in own code. At the press the server copies the user and the
creatures, players and Stands around him into a sealed room that changes
nothing in the real world, runs that room ahead and shows it to the user. The
real bodies are then led through what was foreseen, so it comes true; with the
pull-back setting on, a foreseen player and what he changed meanwhile are first
put back to the press. A Time Erase begun during that walk takes its user out
of what was foreseen. The room is never saved.

Phase 4 adds the canonical heavy-punch replacement chain: ordinary Heavy Punch,
Ground Punch, its Ground Slam finisher, and the low-health Desperate Eyejab
replacement. The server owns hit resolution, damage source identity,
armor penetration, block and shield behavior, knockback, finisher
consumption, stamina, and cooldowns. The original animation timing, icons,
voice lines, and impact sounds are regenerated from the hash-pinned source.
The canonical Eyejab event references a sound file absent from the source
JAR, so the packaged King Crimson Impale impact is used as the recorded
fallback instead of inventing an untraceable asset.

Phase 4 also adds the left and right Half Blindness effects. Their third-person
and first-person visuals are ordinary player render layers plus a namespaced
GUI overlay. They do not replace `PlayerRenderer`, take over a framebuffer,
mutate Iris or Super Resolution state, or enter the Stand transparency chain.

Phase 5 resolves `SAFE_REGION_FAST_FORWARD_V1` with a bounded, collision-checked
server relocation of at most 32 blocks. It advances only the user and summoned
Stand age counters by the canonical implied 200 ticks; it never invokes an
entity tick method, recursively ticks nearby entities, or loads destination
chunks. The server also owns stamina, cooldown, the ten-tick damage window,
target loss, sound selection, and tracked state.

Phase 5 resolves `OBSERVER_SCOPED_TIME_ERASE_PRESENTATION_V1` with serialized
server state, one active Time Erase user, activation and per-tick stamina,
bounded duration, interaction exit, damage handling, prior invisibility
restoration, and dynamic cooldown. The client uses the public
`ClientSkyRenderers`, `LivingEntityMaterialTintPolicies`, and
`ObserverWorldRenderPolicies` APIs for Time Erase, plus `AddonPostEffect` for
Time Skip. Donor reachability proves that `time_erase_kc.json` and
`time_erase_kc_old.json` had no runtime call sites, so neither is packaged as
an active PostChain. The active Time Erase path is the six-face projected sky
with seed 31100, a base pass plus thirteen portal layers, donor red-dominant
colors, owner-scoped cloud/world suppression, and a whole-living-material red
tint that excludes the owner, owner Stand, decoy chain, and afterimages. Only
`rotp_kingcrimson:shaders/post/time_skip.json` remains active here and ends at
`minecraft:main`.

Time Erase also restores the donor's 375 ms, sixteen-frame activation and
termination transition. Activation is owner-only; termination is sent to players
within 192 blocks and, as in the donor (which sent it only to King Crimson users),
is drawn only for players whose own Stand is King Crimson; it stops the owner's
looping start sound and sends the end sound. Every Time Skip shows the same
375 ms flash to the same audience. The owner receives regular invisibility plus the core full-invisibility
effect at amplifier 999999, the summoned Stand receives regular invisibility,
and pre-existing owner and Stand effects are restored on exit. As in the
donor, sprinting is not cancelled during Time Erase.

Beyond the donor, Time Erase's decoy goes on with what its user's keys did at
the press (walking, sprinting, sneaking, jumping and the motion he had) and does
not fight; a killed decoy simply disappears. Since R772.1 (the owner's design
of 2026-11-07) the decoy also decides at the press whether to carry on, to
keep its distance from a threat, to flee or to stand, and walks a path of the
vanilla ground navigation instead of holding a key blindly; it deals no
damage, picks nothing up and drops nothing. The idea of a decoy that acts out
the user's input, chooses what to do at the press and follows a path comes
from the King Crimson clone of Roundabout by Hydraheads
(https://github.com/Hydraheads/RoundaboutMod). No Roundabout code or asset is
used: the implementation is this port's own, under this module's license.

The canonical Time Erase sky textures, all sixteen Time Skip frames, default
ability icons, effect icon, and all 72 packaged sound payloads are restored at
their original active paths. The canonical
`kingcrimson_timeerase` event references a missing
`diavolo_timeerasure2.ogg`, so it uses the packaged
`diavolo_kingcrimson` event as a recorded fallback. All donor action icons,
including the thirty per-skin Time Erase and Time Skip variants, now retain
their original `textures/action` paths and current `textures/ability` aliases.
The Epitaph future-branch `epitaph_timeskip.ogg` stays packaged and
hash-pinned. Until R772.1 the port restored the donor's reaction to a
predicted hit: a dodge, and from resolve level 3 the future branch (the
donor's additional 150 stamina, a bounded Time Skip toward the attacker, the
canonical 40 implied age ticks). By the owner's decision of 2026-11-07 Epitaph
no longer dodges. A hit is now answered by a passive skill of this port's own
design, unlocked with Time Erase and working with or without Epitaph held: a
hit above a tenth of max health is escaped by a two-second Time Erase (300
stamina, 30 seconds of cooldown), and a killing hit, or any hit at a tenth of
health or below, by a four-second one for free, once in ten minutes. The
donor's numbers of the future branch remain recorded in
`KingCrimsonDonorGameplaySpec` and in the donor gameplay manifest; no code
reads them. By the ruling of 2026-11-11 the sound of that branch plays again:
at the start of a passive erase, to its user alone.

The donor's custom barrage pair is also active. The normal barrage retains
the donor's half-health gate at resolve level 3 or above and contextually
switches to the separately registered Doppio barrage while the Stand is
absent or arms-only. Doppio keeps its own cry, 60-tick cooldown, half-health
gate, and arms-only auto-summon behavior. The donor registers Bloodthrow only
as a sound/icon asset and never registers or calls a Bloodthrow action, so
there is no donor Bloodthrow gameplay to port. Blood Throw as this port has it
since R772.1 is the owner's own design of 2026-11-07, on that sound event and
icon: during a Time Erase its user flicks blood at whom he aims at (2 health,
50 stamina, 10 seconds of cooldown), and when the erase ends it lands, over a
player's screen through the core's `ScreenSplash`, as half blindness on a
creature. The donor's event had no audio; it now plays the module's own
`kingcrimson_dash` recording.

The donor also constructs the seven-event `kc_ost` graph and binds it to the
King Crimson Stand. All seven registrations and their canonical event
references are restored through the current core `OstSoundList`; the four
streamed tracks under `sounds/ost/king_crimson_1.ogg` through
`king_crimson_4.ogg` are byte-exact copies from the hash-pinned donor.

No legacy reflection or donor mixin is copied. The addon's own gameplay mixins
touch no render state. Two date from round 69: they end Time Erase when a
Stand punch or barrage reaches its perform phase and keep the caster's own
Time Erase decoy's Stand out of the caster's pick. R772.1 adds five for the
rebuilt Time Erase: `TimeEraseReachEntityMixin`, `TimeEraseReachLivingMixin`
and `TimeEraseReachPlayerMixin` (what the world asks of an entity is answered
for an erasing user as for nobody there), `TimeEraseShiftLerpMixin` (on the
erasing user's client another player's entity is put on his shifted image)
and `LivingLastHurtAccessor` (the passive reads the last hit taken). One more
runs on the client alone, `TimeEraseOutsideParticleMixin`: it decides which
particles the erasing user's particle engine lets in. The addon performs no
framebuffer takeover or global render cancellation, and it copies no static
player map, Iris mutation, or Super Resolution mutation. No global renderer
mutation is copied. The donor's owner-only dark-red moving afterimage, tracked
player-profile decoy and Stand, mob redirection, and lifecycle cleanup are
restored with module-owned entities and payloads. The donor's delayed entity
rewind is not restored: by the owner's decision of 2026-09-23 (manga logic,
where the world simply resumes at the end of the erased time), Time Erase end
rewinds no real entity (since R772.1 it does put the other players forward,
onto the images the erasing user saw of them; see the rebuild below). The
donor's full-color stationary afterimage is not spawned either. It did two things, for the Time Erase user alone: it marked
where each entity would be rewound to, and, glowing on the red `KC_RedGlow`
team, it showed an outline of every tracked creature within 192 blocks through
walls (where the creature stood when the erase began, later trailing it by 60
ticks). The rewind marker is gone by the same owner decision. The through-wall
outline is kept: asked separately, the owner chose on 2026-09-23 to put it on
the remaining moving afterimage. Each dark-red afterimage now glows in the
`KC_RedGlow` red (`TextFormatting.RED`) for its whole life, so the Time Erase
user alone sees a red outline of every tracked creature through walls, trailing
it by 10 ticks instead of 60. The 192 blocks are only the server scan: the
outline is drawn only where the afterimage itself renders, about 64 blocks from
the camera at the default Entity Distance, the same limit as 1.16 (both
versions size it 0.6x1.8), and only for creatures the owner's client tracks.
No other player receives these afterimages, so no
other player sees the outline.
Observer terrain, weather, and block-entity suppression uses the core
observer policy API and only the local Time Erase observer. Since R772.1 the
particle pass is drawn again: the erasing user's own particle engine lets in
only the particles near creatures and moving things outside the erase, in red
and capped, and his own thrown blood. Renderers that
bypass the guarded vanilla methods remain unsupported and fail open; no Iris or
Sodium equivalence is claimed.

The eight-row historical disposition receipt classifies every row as A.
Seven donor-reachable media rows are exact active resources. The runtime-
registered legacy mixin descriptor is a legal semantic conversion to the
Phase 5 public post-effect, compatibility, layer, overlay, and server-state
owners. The descriptor's donor bytes are also retained exactly under
`assets/rotp_kingcrimson/legacy_archive/root`; the former root path is absent.

The registration-reachability audit also closes the older deferred and
archive-only paths. All fifteen donor action registrations, both Time Erase
projection entities, and the donor mixin semantics are repaired active. The
legacy skin manifest has 273 active rows and zero archived rows. The 63 PNG
receipts retained under `legacy_archive/skin_resources` are provenance-only
fully equivalent duplicates: every SHA-256 matches an active ability resource
and none is stranded. `kingcrimson_bloodthrow` and `doppio_damnit` are the only
proven-inert gameplay registrations; donor evidence records zero action
registrations and zero runtime call sites while their media bytes remain
packaged (Blood Throw's present gameplay is the owner's design recorded above,
not the donor's). Exact restoration of the two filenames absent from the canonical
donor and world-pass equivalence in renderers that bypass vanilla guards are
external-blocked; their active local fallbacks and fail-open boundaries are
recorded rather than hidden by deletion.

Time Erase's rebuild of R772.1 (2026-11-07 to 2026-11-11, the owner's design)
is this port's own code throughout: the erase's length by Resolve level and
setting, the erasing user out of the world's reach, the other players' shifted
images and their landing at the end, the day's clock moved on by the erased
time, the creatures' short pause after the end, the erase ended by another
time ability, the decoy's choices, the passive reflex and last-chance erases,
Blood Throw, the Time Erase Step and the erasing user's view of what moves
outside. It uses the core's public `DefencePiercing`, `MobAiPause`,
`TimeEraseWitnesses`, `TimeEraseRunningEvent`, `TimeEraseStartEvent`,
`BlinkLanding` and `ScreenSplash`; the step follows the model of the core's
time-stop blink. No donor code exists for any of it. The Time Erase Step's icon
is derived by this project's own script from each skin's Time Erase icon (the
face cut on its trailing side, three speed lines behind it), sixteen files
named `textures/ability/kingcrimson_timeerase_step.png`; the donor has no such
icon.

The owner's rules of 2026-11-11 for the erase are own code as well: two erasing
users who cannot reach each other, the bounded drift of creatures, the erase of
a creature that carries King Crimson (its own body stays in the world, an
unseen fake body of its kind hunts the player's drifted place, the entity type
`time_erase_drift_body` stands there for him, one swap at the strike, a passive
escape from a killing hit), the unused share of the start's stamina given back
at an early end, and a cooldown that goes by the ticks the erase ran (as long
as it ran by default since 2026-11-12; the donor's setting, which the donor
never applied, said twice). They use
the core's public `CreeperFuseSuppressionProviders`, `StandAbilityStamina`,
`TimeStopLearning` and `PerceptionBlockers` besides the classes named above.
In the mode with no cooldowns the erase and its step meet the core's
`StandAbilityFatigue` as its time stop does; the reflex is its small practical
skill and waits for nothing there, and the last chance keeps its own wait in
both modes.
Another add-on answers a creature's erase through
`KingCrimsonCreatureErase.registerAnswer`; King Crimson calls no other add-on.

Time Erase's breaking ground (2026-11-05) is own code. The rising blocks follow an
idea of Roundabout (https://github.com/Hydraheads/RoundaboutMod), design only: no
Roundabout code or asset is used. The wind's strip in
`assets/rotp_kingcrimson/textures/environment/time_erase_crests.png` is own work,
made by this project's own generator.

The universe of `shaders/core/time_erase_universe.fsh` (2026-11-07) is own work, generated
from this project's patch for the iterationRP shader pack, not from that pack's code. Its
display transform carries the minimal AgX fit of Benjamin Wrensch
(https://iolite-engine.com/blog_posts/minimal_agx_implementation, MIT license), with the
curve's constants as the pack uses them. `time_erase_halo` and `time_erase_outline` are own
work; the outline's line expansion follows vanilla's `rendertype_lines` in method.

The look of the erase in R772.1 (2026-11-12) is own code as well: whole blocks of the ground
that float up off the plates after the crack, as the anime shows the erased world (an idea of
the anime; no asset of it is used); the floor lines graded by their depth under the feet; the
crimson halo of the afterimages with no shader pack too, the vanilla red outline staying only as
its fallback; plate levels above 96 for a pack that declares
`rotp_kingcrimson:time_erase_plate_light` in its id map; the plates' merged undersides drawn
with the block's own texture; daylight on the real ground held before the plates and on a pack
that does not declare the void; a public read-only view of the running erase and the near plan's
law for a far plan, for the far-terrain add-on. The keys of chop, impale, throw and ground slam
no longer pass through the bind pose in their first tick (the donor's first pose of each was the
bind pose; dropped as for the other add-ons' clips). On a pack that does not declare the void the
universe fades off the returned world over half a second, so that the pack's exposure can follow.
