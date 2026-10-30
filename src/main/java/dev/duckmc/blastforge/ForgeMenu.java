package dev.duckmc.blastforge;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.view.AnvilView;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ForgeMenu implements Listener {
    private static final class Session {
        final AnvilView view;
        final Location location;
        long revision;
        AnvilOperation operation=new AnvilOperation(false,null);
        ItemStack base,donor;
        Session(AnvilView view,Location location){this.view=view;this.location=location;}
        AnvilView view(){return view;}
        Location location(){return location;}
    }
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final BlastForge plugin;
    private final ExtractionMenu extraction;
    private boolean normalOperationsOnly;
    ForgeMenu(BlastForge plugin,ExtractionMenu extraction) { this.plugin=plugin;this.extraction=extraction; }
    void normalOperationsOnly(){normalOperationsOnly=true;}
    void open(Player player,Location location) {
        player.closeInventory();
        var builder=MenuType.ANVIL.builder().title(Component.text("The Forging Anvil")).checkReachable(location!=null);
        if(location!=null)builder.location(location);
        AnvilView view=builder.build(player);
        sessions.put(player.getUniqueId(),new Session(view,location));
        player.openInventory(view);
    }
    private boolean owns(InventoryView view) {
        Session session=sessions.get(view.getPlayer().getUniqueId());
        return session!=null && session.view().getTopInventory().equals(view.getTopInventory());
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent event) {
        if(!owns(event.getView())) return;
        Session session=sessions.get(event.getView().getPlayer().getUniqueId());
        long revision=++session.revision;
        session.base=copy(event.getInventory().getItem(0));
        session.donor=copy(event.getInventory().getItem(1));
        session.operation=normalOperationsOnly?new AnvilOperation(false,event.getResult()):AnvilOperation.resolve(session.base,session.donor,event.getResult());
        // Leave ordinary operations entirely to Paper, including result, XP,
        // repair-material count, prior-work cost and the too-expensive limit.
        if(!session.operation.custom())return;
        event.setResult(session.operation.result());
        // Paper supports cost zero. Native cross-material rejection resets it to
        // -1 AFTER this event, so restore the preview/cost after native processing.
        event.getView().setRepairCost(0);
        event.getView().setRepairItemCountCost(0);
        plugin.getServer().getScheduler().runTask(plugin,() -> {
            if(!owns(event.getView()) || session.revision!=revision) return;
            var inventory=event.getView().getTopInventory();
            inventory.setItem(2,session.operation.result().clone());
            event.getView().setRepairCost(0);
            ((Player)event.getView().getPlayer()).updateInventory();
        });
    }
    private static ItemStack copy(ItemStack item){return item==null?null:item.clone();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        if(!owns(event.getView())) return;
        Session session=sessions.get(event.getWhoClicked().getUniqueId());
        if(session.location()!=null&&!plugin.stations().isStation(session.location().getBlock())){event.setCancelled(true);return;}
        if(!session.operation.custom())return;
        // Prevent collect-to-cursor from pulling the preview into an inventory.
        if(event.getClick()==ClickType.DOUBLE_CLICK) { event.setCancelled(true); return; }
        if(event.getRawSlot()!=2) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)) return;
        boolean shift=event.getClick()==ClickType.SHIFT_LEFT || event.getClick()==ClickType.SHIFT_RIGHT;
        if(!shift && event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT) return;
        if(!shift && !event.getCursor().getType().isAir()) return;
        if(shift && player.getInventory().firstEmpty()<0) return;
        Inventory top=event.getView().getTopInventory();
        if(!java.util.Objects.equals(session.base,top.getItem(0))
            || !java.util.Objects.equals(session.donor,top.getItem(1)))return;
        if(Extraction.eligible(top.getItem(0),top.getItem(1))) {
            ItemStack original=top.getItem(0).clone(),books=top.getItem(1).clone();
            // Inventory screens must not be replaced inside a click callback.
            // Revalidate everything next tick before moving ownership to the selector.
            plugin.getServer().getScheduler().runTask(plugin,()->{
                if(!owns(event.getView()) || !player.isOnline()
                    || session.location()!=null&&!plugin.stations().isStation(session.location().getBlock())
                    || !original.equals(top.getItem(0)) || !books.equals(top.getItem(1)))return;
                top.setItem(0,null);top.setItem(1,null);top.setItem(2,null);
                extraction.open(player,session.location()==null?player.getLocation():session.location(),original,books,session.location()==null);
            });
            return;
        }
        ItemStack result=copy(session.operation.result());
        if(result==null) return;
        // Inputs were checked against the server's prepared snapshot above.
        top.setItem(0,null); top.setItem(1,null); top.setItem(2,null);
        if(shift) player.getInventory().addItem(result);
        else player.setItemOnCursor(result);
        player.playSound(session.location()==null?player.getLocation():session.location(),Sound.BLOCK_ANVIL_USE,1,1);
        plugin.getServer().getScheduler().runTask(plugin,player::updateInventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drag(InventoryDragEvent event) {
        if(owns(event.getView()) && event.getRawSlots().contains(2)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true)
    public void wear(com.destroystokyo.paper.event.block.AnvilDamagedEvent event) {
        // Keep the special station permanent while native anvil transactions run.
        if(owns(event.getView()))event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if(!owns(event.getView())) return;
        // The native anvil container returns both inputs on close. Its result is
        // only a preview, and must never be returned as an extra item.
        event.getView().getTopInventory().setItem(2,null);
        sessions.remove(event.getPlayer().getUniqueId());
    }
    void shutdown() {
        for(UUID id:java.util.List.copyOf(sessions.keySet())) {
            Player player=Bukkit.getPlayer(id);
            if(player!=null) player.closeInventory();
        }
        sessions.clear();
    }
}
