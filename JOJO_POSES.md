# Making JoJo pose packs

A JoJo pose is a player clip a player strikes from the pose list and holds. A resource pack adds poses; no data pack
is needed. This follows the official mod's pose files, played on this port's own player model.

## Files

```
assets/<ns>/jojo_pose/<set>/animation.json      the poses of the set: one clip per pose
assets/<ns>/jojo_pose/<set>/data.json           who may strike them (optional)
assets/<ns>/voicelines/<pack>/data.json         the character a voice pack is for (optional)
assets/<ns>/voicelines/<pack>/sounds.json       voice lines, one trigger per pose
```

`<ns>` is your own namespace. The mod ships `jojo_rotp_clothes:jotaro_p3` with `point`, `cap` and `volume13`.

## animation.json

A GeckoLib animation file (Blockbench, "GeckoLib Animated Model" format). Each animation is one pose; its name is the
pose's name: lowercase `a-z`, `0-9`, `_`, `.` and `-`. The clip plays once and its last frame is held, so a short clip
(the official ones are 0.2 s) blends from standing into the pose. The list shows each pose's last frame.

Make the clip on the official user template `gecko player anim.bbmodel` (from the official "User content templates")
or on any model with these bones:

| Bone | On this port's player model |
| --- | --- |
| `body`, `torso`, `torso_bend`, `head` | the same part |
| `left_arm`, `right_arm`, `left_leg`, `right_leg` and their `_bend` bones | the same part (`_bend` bends the limb at its middle) |
| `leftItem`, `rightItem` | the held item |
| `cape`, `cape_bend` | the cape |
| `body_rot` (official rig, pivot at the feet) | folded into `body` |
| `head_rot` | folded into `head` |
| `left_arm_xrot`, `right_arm_xrot`, `left_leg_xrot`, `right_leg_xrot` | folded into the limb |
| `left_item`, `right_item` | read as `leftItem`, `rightItem` |
| `waist`, `neck`, `torso_no_arms`, `torso_lower`, `left_shoulder`, `right_shoulder`, `hips` | not moved |

The table is `OfficialPlayerBones` in the source. "Folded" means the two bones' moves are put on one part as the
official rig stacks them, so a clip made for the official rig looks the same here.

## data.json

```json
{
	"character": "jojo_ripples:jotaro",
	"story_part": [ "jojo_ripples:p3" ],
	"anim_specific": {
		"volume13": {
			"stand_summon_pose": { "stand_id": "jojo_ripples:star_platinum", "anim_name": "summon2" }
		}
	}
}
```

- `character`: the poses are for a player whose worn clothes make them this character (every worn piece belongs to it).
  Without `character` the poses are for every player, also one who counts as no character.
- `story_part`: one id or a list; the worn clothes' story part must be one of them.
- `anim_specific.<pose>.stand_summon_pose`: a Stand of `stand_id` summoned while its user holds the pose appears with
  that summon clip (only `summon<N>` clips).
- The official field `voice_line` is not read: give the line in a voice pack (below). `authors` is not shown.

## Voice line

A pose's line goes through the voice packs, as every other voice line does. Its trigger is
`<ns>:vl_jojo_pose/<set>/<pose>`, with the pose set's namespace. A voice pack's `sounds.json`:

```json
{
	"jojo_rotp_clothes:vl_jojo_pose/jotaro_p3/cap": [
		{ "subtitle": "jojo_ripples.subtitle.yare_yare", "sounds": [ "jojo_ripples:stand_user/jotaro_yare_yare_1" ] }
	]
}
```

The pack's `data.json` names its `character` and `story_part` as for any voice pack. A player who counts as no
character says no pose line. A key without a namespace takes the pack's own.

## Packs over packs

Every resource pack's copy counts. In `animation.json` a higher pack's pose replaces a lower pack's pose of the same
name, and its other poses come after the lower pack's. In `data.json` each field of the highest pack that sets it wins,
and in `anim_specific` each pose's entry of the highest pack.

## In game

- Open the chat: the poses your worn character may strike stand at the right edge. The mouse wheel moves through a
  long list. Click one to strike it; a line typed in the chat box is sent with it, as Enter would.
- Key "JoJo pose list" (unbound by default; Controls, Ripples of the Past) opens the same list on its own screen, and
  ends the pose you hold.
- A pose ends when you move, jump, sneak, swing your arm or use a Stand ability you perform yourself; any other
  action replaces it. Other players see it.
