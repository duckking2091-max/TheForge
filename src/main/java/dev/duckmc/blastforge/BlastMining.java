package dev.duckmc.blastforge;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.enchantments.Enchantment;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class BlastMining implements Listener {
    private final BlastForge plugin;
    private final Enchantment blast;
    private final Set<UUID> mining = new HashSet<>();
    BlastMining(BlastForge plugin, Enchantment blast) { this.plugin=plugin; this.blast=blast; }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBreak(BlockBreakEvent event) {
        Player player=event.getPlayer();
        if (mining.contains(player.getUniqueId()) || player.isSneaking() || player.getGameMode()!=GameMode.SURVIVAL) return;
        ItemStack tool=player.getInventory().getItemInMainHand();
        int level=tool.getEnchantmentLevel(blast);
        if (level<1 || !BlastTools.supported(tool.getType()) || !eligible(event.getBlock(),tool)) return;
        Block origin=event.getBlock();
        var hit=player.rayTraceBlocks(6);
        if(hit==null || !origin.equals(hit.getHitBlock()) || hit.getHitBlockFace()==null) return;
        BlockFace inward=hit.getHitBlockFace().getOppositeFace();
        int axis=inward.getModX()!=0 ? 0 : inward.getModY()!=0 ? 1 : 2;
        int sign=inward.getModX()+inward.getModY()+inward.getModZ();
        int slot=player.getInventory().getHeldItemSlot();
        ItemStack identity=withoutDamage(tool);
        var cells=MiningShape.offsets(Math.min(3,level),axis,sign);
        // Wait for the original break and its durability charge to finish. The event reference
        // also lets us observe cancellations made later in the same event dispatch.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if(event.isCancelled() || !(origin.getType().isAir() || origin.isLiquid()) || !player.isOnline()
                || player.getGameMode()!=GameMode.SURVIVAL || !player.getWorld().equals(origin.getWorld())) return;
            mining.add(player.getUniqueId());
            try {
                for(var cell:cells) {
                    if(cell.x()==0 && cell.y()==0 && cell.z()==0) continue;
                    ItemStack held=player.getInventory().getItemInMainHand();
                    if(player.getInventory().getHeldItemSlot()!=slot || !identity.equals(withoutDamage(held))) break;
                    int y=origin.getY()+cell.y();
                    if(y<origin.getWorld().getMinHeight() || y>=origin.getWorld().getMaxHeight()) continue;
                    int x=origin.getX()+cell.x(), z=origin.getZ()+cell.z();
                    if(!origin.getWorld().isChunkLoaded(x>>4,z>>4)) continue;
                    Block block=origin.getWorld().getBlockAt(x,y,z);
                    if(eligible(block,held) && !spawnProtected(block,player)
                        && block.getWorld().getWorldBorder().isInside(block.getLocation())
                        && !plugin.stations().isStation(block)) {
                        // Paper performs real player mining: event protection checks, loot,
                        // XP, Silk Touch/Fortune, Unbreaking, and one durability use per block.
                        player.breakBlock(block);
                    }
                }
            } finally { mining.remove(player.getUniqueId()); }
        });
    }
    private boolean eligible(Block block, ItemStack tool) {
        return !block.getType().isAir() && block.getType().getHardness()>=0
            && (BlastTools.mineable(tool.getType(),block.getType())||plugin.customOre(block)) && block.isValidTool(tool) && plugin.customHarvest(block,tool);
    }
    private boolean spawnProtected(Block block,Player player) {
        var server=plugin.getServer();
        var spawn=block.getWorld().getSpawnLocation();
        return SpawnProtection.blocked(block.getWorld().equals(server.getWorlds().getFirst()),
            !server.getOperators().isEmpty(),player.isOp(),server.getSpawnRadius(),
            block.getX()-spawn.getBlockX(),block.getZ()-spawn.getBlockZ());
    }
    private ItemStack withoutDamage(ItemStack stack) {
        ItemStack copy=stack.clone();
        if(copy.getItemMeta() instanceof Damageable meta) { meta.setDamage(0); copy.setItemMeta(meta); }
        return copy;
    }
}
