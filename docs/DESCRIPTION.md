# The Forge

**Rare ores, a magical forge, elemental spell books and one legendary weapon — for Paper 1.21.11.**

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

## 📦 Installation
1. Requires **Paper 1.21.11** and **Java 21**.
2. Put `TheForge-1.0.1.jar` in `plugins` and restart the server.
3. Resource pack (strongly recommended): download `TheForge-1.21.11-pack.zip`, host it at a direct link, and set
   in `server.properties`:
   ```
   resource-pack=<direct link to the zip>
   resource-pack-sha1=<sha-1 from TheForge-1.21.11-pack.sha1>
   require-resource-pack=true
   ```
4. Adjust everything you like in `plugins/TheForge/` (`magic.yml`, `materials.yml`, `villagers.yml`, `scythe.yml`, …)
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
