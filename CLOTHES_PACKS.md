# Making clothes packs

Clothes sets are data-driven. A pack adds them with a data pack (the set and its character) and a resource pack
(model, textures, item models, names). Both may be shipped together as one small mod jar.

This follows the official mod's user templates (`readme.txt` and `clothes_example.jar`), with the paths this port reads.

## Files

```
data/<ns>/ripples_clothes/character/<character>.json   a JoJo character (optional when the character exists)
data/<ns>/ripples_clothes/clothes/<set>.json           a clothes set
assets/<ns>/geo/clothes/<model>.geo.json                the model (GeckoLib format, made in Blockbench)
assets/<ns>/textures/clothes/<model>.png                its texture
assets/<ns>/items/<item>.json                           item model definition of each piece
assets/<ns>/models/item/<item>.json                     the item model it names
assets/<ns>/textures/item/<item>.png                    the item icon
assets/<ns>/lang/<locale>.json                          names
```

`<ns>` is your own namespace. Use `jojo_ripples` only to add to a character this mod already has.

## Character

```json
{
	"stand": ["jojo_ripples:the_world", "my_pack:the_world_alt"]
}
```

- `stand` is one Stand id or a list of them, and may be left out.
- When several packs hold the same character file, the game merges them: the character keeps the Stand ids of all of
  them. A pack can therefore give an existing character another Stand, such as an add-on's, by shipping
  `data/jojo_ripples/ripples_clothes/character/<character>.json` with only the new id.
- Names: `character.<ns>.<character>` (full) and `character.<ns>.<character>.short`.
- The mod ships 61 characters, from `jonathan` to `kira8`.

## Clothes set

```json
{
	"character": "jojo_ripples:dio",
	"story_part": "jojo_ripples:p3",
	"authors": "Your name",
	"pieces": {
		"head": {
			"asset_id": "my_pack:dio_p3",
			"item_model": "my_pack:dio_p3_head",
			"item_name": { "translate": "item.my_pack.dio_p3_head" },
			"equip_sound": "item.armor.equip_generic"
		},
		"chest": {
			"asset_id": "my_pack:dio_p3",
			"item_model": "my_pack:dio_p3_chest",
			"item_name": { "translate": "item.my_pack.dio_p3_chest" },
			"split_into": {
				"top": { "asset_id": "my_pack:dio_p3_jacket", "item_model": "my_pack:dio_p3_jacket", "item_name": { "translate": "item.my_pack.dio_p3_jacket" } },
				"bottom": { "asset_id": "my_pack:dio_p3_shirt", "item_model": "my_pack:dio_p3_shirt", "item_name": { "translate": "item.my_pack.dio_p3_shirt" } }
			}
		}
	}
}
```

- `character`: the character the set belongs to.
- `story_part`: optional, `jojo_ripples:p1` to `jojo_ripples:p9`.
- `authors`: optional. The item tooltip credits them ("Created by: ...").
- `pieces`: any of `head`, `chest`, `legs`, `feet`. In each piece:
  - `asset_id` names both the model `geo/clothes/<path>.geo.json` and the texture `textures/clothes/<path>.png`;
  - `texture_id`, if given, takes the texture from another id;
  - `equip_sound` is optional;
  - `split_into` lets a player right-click the piece to split it into a `top` and a `bottom`, and combine them back.
- Name of the set: `clothes.<ns>.<set>`.

The sewing machine lists every set by character, and the creative tab shows each piece.

## Model

Make the model in Blockbench in the GeckoLib animated model format.

- Keep the main parts named `head`, `body`, `right_arm`, `right_arm_slim`, `left_arm`, `left_arm_slim`, `right_leg` and
  `left_leg`, so the clothes follow the player's model and animations.
- A part whose name starts with `slot0_` belongs to the head piece, `slot1_` to the chest, `slot2_` to the legs and
  `slot3_` to the feet. Its child parts belong to the same piece.
- The `_slim` arm parts are drawn on players with slim arms instead of the other two.

## Packing

- As packs: put `data` in a data pack and `assets` in a resource pack.
- As one jar: add `META-INF/neoforge.mods.toml` with your own `modId` and name, and credit the people who made the art.

## In game

- The clothes screen (the button beside the inventory) names the character the wearer counts as: the one every worn
  piece belongs to.
- The mod's settings, client page, section Clothes: "Worn item priority" picks what a player shows when wearing armor and clothes in the same
  slot. It defaults to the clothes.
