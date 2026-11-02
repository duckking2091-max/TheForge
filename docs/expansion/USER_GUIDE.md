# The Forge 1.3.0 — player and server guide

Target: Paper, Folia, Purpur or Pufferfish 1.21.0 – 1.21.11 (Java 21+). Purpur and Pufferfish are built on Paper and run The Forge unchanged; on Folia every job runs on the region that owns it. Spigot is not supported.
Players also get this information in-game: new players receive **The Forging Starter**, an illustrated two-page
guide book (craft another with a book + an iron ingot, or use `/forge guide`).

### Supported versions
The Forge runs on **every Paper, Folia, Purpur and Pufferfish 1.21.x server, from 1.21.0 to 1.21.11**, and its single resource pack works on every
1.21.x client. A few features need a newer Minecraft and are simply skipped on older ones:

| Feature | Needs |
|---|---|
| Spears (Siftie / Enderite / Wardenite) | 1.21.11 |
| Wardenite helmet 3D model, the flying water block in Water Strike | 1.21.4+ (older: normal Wardenite armour / a glass block) |
| Custom armour looks for Siftie, Enderite and Wardenite | 1.21.2+ (older: netherite look; stats are the same) |
| Blast offered by enchanting tables | most 1.21 builds (the very first ones: librarian books only) |

**Folia note:** supported since 1.2.0 and tested by the server owner. Every timed job runs on the region thread that owns it; temporary spell blocks left at a shutdown are restored after the next start instead of during it.

## Install or update

1. Stop the server and back up the worlds and `plugins/TheForge` together.
2. Keep one The Forge JAR in `plugins` (`TheForge-1.4.0.jar`).
3. Start Paper, Folia, Purpur or Pufferfish 1.21.x. The settings files, `guide.yml` and `blastforge.db` are created in `plugins/TheForge`.
   Never delete the database when editing settings.
4. Resource pack: host `TheForge-pack.zip` at a direct link and set `resource-pack` and
   `resource-pack-sha1` in `server.properties` (see the README). With `resource-pack.url` empty in `config.yml`,
   The Forge uses the server's pack. `assume-installed: true` (default) shows the custom screens to everyone.
   When you update the pack, change only the link and SHA-1 in `server.properties`.
5. `/forge reload` applies edited settings; invalid settings are rejected with a message. Restart fully for a new JAR.

## The Forging Anvil

Buy the **Book of Forging** from a Wandering Miner (30 emeralds + 1 anvil), then craft:

    Iron Block | Book of Forging | Iron Block
       empty   |  Normal Anvil   |   empty
      Diamond  |     Diamond     |  Diamond

Right-click the placed anvil. The tabs along the bottom switch between **Forging**, **Upgrading** and the
**Dark Ritual**; the name-tag tab on the Forging screen opens a normal anvil for renaming. Closing the screen returns
ingredients; anything that does not fit is given to you automatically as soon as you make room.

- **Forging** — item to keep in slot 1, second item in slot 2. Combine enchantments (the higher level wins;
  conflicting enchantments can coexist), repair with the matching material (each material adds 25% durability),
  merge two damaged copies (+12%), or extract enchantments onto plain books. Default cost 4 XP levels; extracting is free.
- **Upgrading** — template + gear + matching ingot. Diamond → Netherite → Siftie → Enderite → Wardenite,
  default 5 / 8 / 12 / 16 levels, 3 seconds. Enchantments, name and relative durability are kept.
- **Dark Ritual** — eight labelled slots: any magic book, Wardenite ingot, Enderite stick, Dragon's Heart,
  Echo Shard, Warden's Heart, Bat Wing, Netherite ingot. Creates the Legendary Scythe (5 seconds).

## Blast

Enchanting-table enchantment for pickaxes and shovels. Blast I / II / III mine 3×3×1, 3×3×2 and 3×3×3, oriented to the
face you mine, with normal durability, Unbreaking, Fortune / Silk Touch and protection checks for every block.
Sneak to mine a single block.

## Materials

- **Siftie** — new Overworld chunks, Y −60 to −30, rarer than diamonds and mostly buried in stone (about 1 small vein every 4 chunks), diamond pickaxe or better. Raw Siftie smelts into ingots.
- **Enderite** — buried inside outer End islands (at least 1024 blocks from the centre), rare on the surface,
  netherite pickaxe or better; Wardenite pickaxes cannot insta-mine it. Raw Enderite smelts into ingots;
  two ingots stacked make an Enderite Stick.
- **Wardenite** — no ore. Wardens drop Wardenite Scrap; 8 scrap around an Echo Shard make an ingot.
  The Wardenite template is found in Ancient City chests.
- Templates: Siftie = 4 Siftie ingots + 4 blaze rods around a netherite template; Enderite = 4 Raw Enderite around a
  Siftie template.

All values are in `materials.yml`. Existing chunks are not regenerated.

### Turning a tier off
Each material tier can be switched off in `materials.yml`:
```yaml
tiers-enabled:
  siftie: true
  enderite: true
  wardenite: true
```
A tier set to `false` has no ore generation, no ore drops, Warden drops or Ancient City loot, no recipes or smelting,
and nothing can be upgraded into it: the upgrade path skips it (for example Netherite → Enderite while Siftie is off;
the Enderite template is then crafted around a netherite template). Tools, armour and items players already own keep
working. The Dark Ritual takes a Netherite Ingot instead of a Wardenite ingot, and a Blaze Rod instead of an Enderite
stick, while those tiers are off. The guide book hides the pages about a tier that is off. Run `/forge reload` after changing it.


## Magic books

Hold a book in your main hand to cast. Carrying a book anywhere in your inventory or off-hand gives its passive.
The cooldown bar above the hotbar shows every ability of the held book.

| Book | Right-click | Sneak + right-click | Sneak + left-click |
|---|---|---|---|
| Fire | Fire Field | Fire Ring | Fireball |
| Water | Bubble Launch | Summon Drowned | Water Strike |
| Earth | Excavation | Stone Wall | — |
| Wind | Flight | Suffocation | Wind Charge |
| Darkness | Blindness | Summon Wither Skeletons | Darkness Breath |
| Legendary Scythe | Dash | Flight | Windmill |

- **Fire Field** — real fire on the ground from 2 to 5 blocks around you; it never spreads and disappears after 5 s.
- **Fire Ring** — a ring of flames following you; enemies inside are hurt and keep burning 3 s after leaving.
- **Fireball** — a real fireball (no block damage); whatever it hits burns 10 s.
- **Bubble Launch** — launches you upward with a trail of bubbles.
- **Summon Drowned** — 3 Drowned allies for 60 s. They fight hostile mobs (and players where PvP is on), never you,
  and follow you when there is nothing to fight. If a summon fails, the reason is shown above the hotbar.
- **Water Strike** — needs water within 5 blocks. Hold Shift to hold the water and aim; release Shift to throw it.
  The target drowns for 5 s. Real water is never moved or copied.
- **Excavation** — a temporary 3×3 hole that refills itself.
- **Stone Wall** — a temporary 9 wide × 7 tall wall for 5 s (stone, blackstone in the Nether, end stone bricks in the End).
- **Flight** (Wind and Scythe) — fly for the duration; holding Sneak never cancels it. Your previous flight state is restored.
- **Suffocation** — a ring of gusts; enemies inside lose air and take damage.
- **Darkness Breath** — a real Ender Dragon fireball with its lingering breath cloud (also hurts undead mobs).
- **Windmill** — the upright scythe circles you, hitting what it sweeps through.

Passives: Fire — immune to fire and lava, cooked animal drops. Water — no drowning, faster swimming.
Earth — Haste II. Wind — no fall damage. Darkness — all four.
The Darkness Book is never sold: craft it from the Fire, Water, Earth and Wind books.

Spells respect PvP, teams and protected regions. Temporary blocks are restored exactly and never overwrite a block a
player changed meanwhile. Summons drop no loot or XP. Values are in `magic.yml`.

## Merchants

**Magician.** An adult, jobless villager near an unclaimed enchanting table becomes a Magician (purple robe and hat).
Every Magician has its own random trades, rolled once and kept forever:

- Level 1: exactly 2 trades — one random elemental book and one random extra.
- Levels 2–4: each adds another random book and extra, so all four books are for sale by level 4.
- Level 5: two more extras (Echo Shards only appear here).
- Extras: XP bottles, lapis, an Unbreaking III book, ender pearls, amethyst, echo shards.

Prices: Fire 24, Water 28, Earth 32, Wind 40 emeralds. Levels need 10 / 70 / 150 / 250 trading XP. Restocks twice a day
while working at its table. Configure in `villagers.yml` under `magician.random-trades` (trades and books per level,
duplicates) and each trade's `min-level`, `emeralds`, `amount`, `max-uses`.

**Wandering Miner** (brown overalls, lantern hat, backpack). Every 20 minutes, a player who stayed within 64 blocks of
one spot for 10 minutes has a 5% chance (+5% per nearby villager) of a Miner appearing. At most 16 at once, none within
128 blocks of another; each leaves after 30 minutes. He offers exactly 12 trades, rolled once:

- 9 mineral trades, as items only (never ores): iron, gold and copper ingots or raw metal 1–5 at a time, plus coal,
  redstone, lapis, diamonds, amethyst and quartz. The first offer is always a metal sale.
- 2 random enchanted pickaxes.
- The Book of Forging, always: 30 emeralds + 1 anvil.

All prices are in `villagers.yml` under `miner`.

## The Legendary Scythe

Only one may exist on the server (`scythe.yml` `maximum-scythes`). It still counts while held, stored in any chest,
ender chest or shulker box, dropped, carried by an offline player, in an unloaded chunk or in another dimension.
It is freed — and the Dark Ritual and the Dragon Heart / Warden Heart / Bat Wing drops resume — only when its
destruction is confirmed:

- its durability runs out (5000);
- dropped on the ground and burnt by lava or fire, cut by cactus, blown up, fallen into the void or despawned;
- deleted in Creative mode (checked after the inventory closes and it is found nowhere);
- removed by another plugin, only if `plugin-removal-destroys: true` (otherwise the console tells you the command to run).

Nobody receives a replacement; a new scythe needs a new Dark Ritual. Copying a scythe in Creative is refused, and copies
of a destroyed scythe stop working. A Wardenite ingot in the Forging tab repairs 25% durability.

Admins: `/forge scythe status` lists every scythe with its state and last known location;
`scythe destroy <id>` frees the limit when a scythe is gone in a way the plugin cannot detect (world reset, `/clear`);
`scythe restore <id>` undoes a wrong destruction if the limit has room.

## Administration

| Command | Permission |
|---|---|
| /forge reload | blastforge.reload |
| /forge give <player> <item> [amount] | blastforge.give |
| /forge guide [player] | everyone / blastforge.give for others |
| /forge open [player] | blastforge.open |
| /forge scythe status · limit <n> · destroy <id> · restore <id> | blastforge.scythe.admin |
| /forge miner spawn | blastforge.miner.spawn |
| /forge debug | blastforge.debug |

`/theforge` and `/blastforge` also work. `blastforge.use` (everyone by default) allows casting. Settings: `config.yml` (forging, resource pack, guide),
`materials.yml`, `magic.yml`, `villagers.yml`, `scythe.yml`, `messages.yml`; the guide's text is in `guide.yml`
(add `[figure:name]` to show a picture). Back up `blastforge.db` together with the worlds, while the server is stopped,
and never restore only one of them to an older copy. Interrupted operations are kept in quarantine for an admin to
review rather than duplicating items.
