# The Forge

**Rare ores, a magical forge, elemental spell books and one legendary weapon — for Paper, Folia, Purpur and Pufferfish 1.21.x.**

## 📥 Download & quick setup
| What | Where |
|---|---|
| **Plugin** — `TheForge-1.4.0.jar` | Here on Modrinth (Versions tab) or on [GitHub Releases](https://github.com/duckking2091-max/TheForge/releases/latest) |
| **Resource pack** — `TheForge-pack.zip` | [The Forge Resource Pack on Modrinth](https://modrinth.com/resourcepack/the-forge-resource-pack) (the same file works for every 1.21.x version) |

1. Put `TheForge-1.4.0.jar` in your server's `plugins` folder (Paper, Folia, Purpur or Pufferfish **1.21.0 – 1.21.11**, Java 21).
2. Open [The Forge Resource Pack](https://modrinth.com/resourcepack/the-forge-resource-pack), go to its **Versions** tab, right-click the download button of the newest version and **Copy link**.
   Then add to `server.properties`. The SHA-1 of the current pack (1.4.0) is `29b9b1913a1c8f2c7958958a80eb691bda6f038a`:
   ```
   resource-pack=<the copied Modrinth link>
   resource-pack-sha1=29b9b1913a1c8f2c7958958a80eb691bda6f038a
   require-resource-pack=true
   ```
   No other hosting needed: Modrinth's link is a direct download.
3. Restart the server. New players get the guide book automatically, or use `/forge guide`.

You only set the pack up once: it keeps working when you change your server's 1.21 version.

---

The Forge adds a full progression on top of vanilla survival: dig deeper with Blast mining, find three new ores,
forge and upgrade your gear at a custom anvil, learn elemental magic from a wandering Magician, and — if you are
brave enough — perform the Dark Ritual to create the only Legendary Scythe on the server.

No mods needed: everything runs on the server, with a resource pack for the custom models and screens.

---

## ✨ Features

### ⛏️ Blast mining
A new enchantment for pickaxes and shovels from the enchanting table.
**Blast I / II / III** mines 3×3×1, 3×3×2 or 3×3×3 at once, facing the block you hit.
Durability, Unbreaking, Fortune, Silk Touch and protected regions all work per block. Sneak to mine just one block.

### 💎 Three new tiers: Siftie → Enderite → Wardenite
| Tier | Where | How |
|---|---|---|
| **Siftie** | Deep Overworld (Y −60 to −30) | Mine with diamond or better, smelt Raw Siftie |
| **Enderite** | Buried in the outer End islands | Netherite pickaxe, smelt Raw Enderite |
| **Wardenite** | No ore! | Wardens drop scrap — 8 scrap + an Echo Shard make an ingot |

Full sets of tools and armour for each tier, each stronger than the last, with their own perks
(Enderite armour softens ender pearls, Wardenite armour resists the Warden's sonic boom).

### 🔨 The Forging Anvil
Buy the **Book of Forging** from the Wandering Miner and craft the Forging Anvil. One screen, three workshops:
- **Forging** — merge enchantments (even conflicting ones!), repair with ingots, combine damaged items,
  or extract enchantments onto books.
- **Upgrading** — template + gear + ingot: Diamond → Netherite → Siftie → Enderite → Wardenite.
- **Copying templates** — Siftie, Enderite and Wardenite templates can be duplicated (makes 2): Diamond + 7 deepslate, Siftie Ingot + 7 End Stone, or Echo Shard + 7 deepslate around the template.
  Enchantments and names are kept.
- **Dark Ritual** — eight rare ingredients around one slot create the **Legendary Scythe**.

### 📖 Five magic books
Hold a book to cast, carry it for its passive. Each book has three spells:

| Book | Spells | Passive |
|---|---|---|
| 🔥 **Fire** | Fire Field · Fire Ring · Fireball | Immune to fire & lava, cooked drops |
| 💧 **Water** | Bubble Launch · Summon Drowned allies · Water Strike | No drowning, fast swimming |
| 🪨 **Earth** | Excavation · 9×7 Stone Wall | Haste II |
| 🌬️ **Wind** | Flight · Suffocation ring · Wind Charge | No fall damage |
| 🌑 **Darkness** | Blindness · Wither Skeletons · Dragon's Breath | All four passives |

The Darkness Book is never sold — craft it from the four elemental books.
Spells respect PvP, teams and protected regions, and every temporary block is restored exactly.

### ⚔️ The Legendary Scythe
Only **one** can exist on the whole server. Dash through enemies, fly for 30 seconds, or spin the Windmill.
It stays claimed while anyone owns it — in a chest, offline, anywhere. Only when it is truly destroyed
(lava, the void, broken, despawned…) can someone perform the Dark Ritual again.

### 🧙 Two custom merchants
- **The Magician** — a villager who claims an enchanting table. Every Magician has its own random trades:
  2 offers at first, one more elemental book at every level until all four are for sale.
- **The Wandering Miner** — appears near players who settle in one place. Trades metals, minerals and enchanted
  pickaxes, and always sells the Book of Forging.

### 📚 The Forging Starter
New players get an illustrated two-page guide book with crafting grids, upgrade paths, the ritual layout and every
spell explained — using the server's real settings.

---

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

## ⚙️ Configuration
Don't want a tier? Set `tiers-enabled.siftie` / `enderite` / `wardenite` to `false` in `materials.yml`: its ore, drops, recipes and upgrades are switched off, and gear players already own keeps working.

Adjust everything you like in `plugins/TheForge/` (`magic.yml`, `materials.yml`, `villagers.yml`, `scythe.yml`, …)
and run `/forge reload`.

## 🛠️ Commands
| Command | What it does |
|---|---|
| `/forge guide` | Get The Forging Starter guide book |
| `/forge give <player> <item>` | Give any The Forge item (admin) |
| `/forge scythe status` | See the Legendary Scythe and where it was last seen (admin) |
| `/forge scythe destroy/restore <id>` | Fix the scythe limit by hand (admin) |
| `/forge miner spawn` | Summon a Wandering Miner (admin) |
| `/forge reload` | Reload the settings (admin) |

Admin commands default to operators. Players need `blastforge.use` (on by default) to cast spells.

## ❓ Good to know
- Siftie and Enderite only generate in **new** chunks — explore!
- Everything is configurable: drop chances, prices, cooldowns, damage, ore heights, the scythe limit.
- Your data is safe: interrupted crafts, crashes and full inventories never duplicate or delete items.
