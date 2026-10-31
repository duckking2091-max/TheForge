package dev.duckmc.blastforge;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

final class ForgeStations implements Listener {
    private final JavaPlugin plugin;
    private final ForgeItems items;
    private final ForgeMenu menu;
    private BiConsumer<Player,Location> opener;
    private Consumer<Block> placed=b->{},removed=b->{};
    private final Map<Location,Object> generations=new HashMap<>();
    private final Map<Location,BlockBreakEvent> pendingBreaks=new HashMap<>();
    ForgeStations(JavaPlugin plugin,ForgeItems items,ForgeMenu menu) { this.plugin=plugin; this.items=items; this.menu=menu; this.opener=menu==null?null:menu::open; }
    void opener(BiConsumer<Player,Location> opener){this.opener=opener;}
    void visuals(Consumer<Block> placed,Consumer<Block> removed){this.placed=placed;this.removed=removed;}
    private NamespacedKey key(Block block) {
        return new NamespacedKey("blastforge","station_"+(block.getX()&15)+"_"+block.getY()+"_"+(block.getZ()&15));
    }
    boolean isStation(Block block) {
        return Tag.ANVIL.isTagged(block.getType()) && marked(block);
    }
    private boolean marked(Block block) { return block.getChunk().getPersistentDataContainer().has(key(block),PersistentDataType.BYTE); }
    private void mark(Block block,boolean value) {
        if(value) block.getChunk().getPersistentDataContainer().set(key(block),PersistentDataType.BYTE,(byte)1);
        else block.getChunk().getPersistentDataContainer().remove(key(block));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void place(BlockPlaceEvent event) {
        Block block=event.getBlockPlaced();
        boolean prior=marked(block);
        if(!prior && !items.isAnvil(event.getItemInHand()) && !generations.containsKey(block.getLocation())) return;
        Object generation=new Object();
        generations.put(block.getLocation(),generation);
        mark(block,items.isAnvil(event.getItemInHand()));
        plugin.getServer().getScheduler().runTask(plugin,() -> {
            if(generations.remove(block.getLocation(),generation) && event.isCancelled()) mark(block,prior);
            if(!event.isCancelled()&&isStation(block))placed.accept(block);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent event) {
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK
            || event.getClickedBlock()==null
            || event.useInteractedBlock()==Event.Result.DENY || !isStation(event.getClickedBlock())) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        if(event.getPlayer().isSneaking() || event.getHand()!=EquipmentSlot.HAND) return;
        event.setCancelled(true);
        if(opener!=null)opener.accept(event.getPlayer(),event.getClickedBlock().getLocation());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drops(BlockDropItemEvent event) {
        BlockBreakEvent broken=pendingBreaks.remove(event.getBlock().getLocation());
        if(broken==null || broken.isCancelled() || !Tag.ANVIL.isTagged(event.getBlockState().getType())) return;
        if(!event.getItems().isEmpty()) {
            event.getItems().getFirst().setItemStack(items.anvil());
            for(int i=event.getItems().size()-1;i>=1;i--) event.getItems().remove(i);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void broken(BlockBreakEvent event) {
        if(!isStation(event.getBlock())) return;
        Block block=event.getBlock();
        Object generation=new Object();
        generations.put(block.getLocation(),generation);
        pendingBreaks.put(block.getLocation(),event);
        mark(block,false);
        removed.accept(block);
        plugin.getServer().getScheduler().runTask(plugin,() -> {
            pendingBreaks.remove(block.getLocation(),event);
            if(generations.remove(block.getLocation(),generation) && event.isCancelled()) mark(block,true);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void falling(EntityChangeBlockEvent event) { if(isStation(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void physics(BlockPhysicsEvent event) { if(isStation(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explosion(EntityExplodeEvent event) { event.blockList().removeIf(this::isStation); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explosion(BlockExplodeEvent event) { event.blockList().removeIf(this::isStation); }
}
