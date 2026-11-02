package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/** Capacity is issuance state; missing or unloaded physical items never free a slot. */
public final class ScytheLifecycle implements Listener {
    private final JavaPlugin plugin;private final ExpansionItems items;private final Function<String,FileConfiguration> configs;private final PersistentStore store;
    private final NamespacedKey uuidKey=new NamespacedKey("blastforge","scythe_uuid"),deathKey=new NamespacedKey("blastforge","rare_death_processed"),summonKey=new NamespacedKey("blastforge","magic_summon");
    public ScytheLifecycle(JavaPlugin plugin,ExpansionItems items,Function<String,FileConfiguration> configs,PersistentStore store){this.plugin=plugin;this.items=items;this.configs=configs;this.store=store;}
    public void start(){plugin.getServer().getPluginManager().registerEvents(this,plugin);}
    public int maximum(){String saved=store.get("settings","scythe_limit");return Math.max(0,saved==null?configs.apply("scythe").getInt("maximum-scythes",1):Integer.parseInt(saved));}
    public boolean available(){return store.occupiedScythes()<maximum();}
    public ItemStack issue(){UUID id=UUID.randomUUID();if(!store.reserveScythe(id,maximum()))return null;try{ItemStack item=items.create("legendary_scythe");item.editMeta(m->m.getPersistentDataContainer().set(uuidKey,PersistentDataType.STRING,id.toString()));store.activateScythe(id);return item;}catch(RuntimeException ex){store.releaseReservation(id);throw ex;}}
    public UUID identity(ItemStack item){if(!items.id(item).equals("legendary_scythe"))return null;String id=item.getItemMeta().getPersistentDataContainer().get(uuidKey,PersistentDataType.STRING);try{return id==null?null:UUID.fromString(id);}catch(IllegalArgumentException e){return null;}}
    @EventHandler(priority=EventPriority.MONITOR)
    public void broken(PlayerItemBreakEvent event){UUID id=identity(event.getBrokenItem());if(id!=null)destroy(id,"broken (durability) by "+event.getPlayer().getName());}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drops(EntityDeathEvent event){
        LivingEntity entity=event.getEntity();
        if(entity.getPersistentDataContainer().has(summonKey)||entity.getPersistentDataContainer().has(deathKey))return;
        String id=entity instanceof EnderDragon?"dragon_heart":entity instanceof Warden?"warden_heart":entity instanceof Bat?"bat_wing":null;
        if(id==null||store.get("rare_deaths",entity.getUniqueId().toString())!=null)return;
        store.put("rare_deaths",entity.getUniqueId().toString(),id);entity.getPersistentDataContainer().set(deathKey,PersistentDataType.BYTE,(byte)1);
        if(!available())return;
        double chance=configs.apply("scythe").getDouble("drops."+id.replace('_','-'),id.equals("dragon_heart")?1:id.equals("warden_heart")?.2:.5);
        if(ThreadLocalRandom.current().nextDouble()<Math.clamp(chance,0,1))event.getDrops().add(items.create(id));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drop(PlayerDropItemEvent event){creativePending.remove(identity(event.getItemDrop().getItemStack()));observe(event.getItemDrop().getItemStack(),"entity:"+event.getItemDrop().getUniqueId()+":"+event.getPlayer().getWorld().getUID());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void pickup(EntityPickupItemEvent event){observe(event.getItem().getItemStack(),"holder:"+event.getEntity().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(InventoryMoveItemEvent event){Location location=event.getDestination().getLocation();observe(event.getItem(),location==null?"inventory:unlocated":"container:"+location.getWorld().getUID()+":"+location.getBlockX()+":"+location.getBlockY()+":"+location.getBlockZ());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void click(InventoryClickEvent event){
        if(identity(event.getCurrentItem())==null&&identity(event.getCursor())==null)return;
        dev.duckmc.blastforge.Tasks.entity(plugin,event.getWhoClicked(),()->{
            for(ItemStack item:event.getWhoClicked().getInventory().getContents())observe(item,"player:"+event.getWhoClicked().getUniqueId());
            var top=event.getView().getTopInventory();Location location=top.getLocation();if(location!=null)for(ItemStack item:top.getContents())observe(item,"container:"+location.getWorld().getUID()+":"+location.getBlockX()+":"+location.getBlockY()+":"+location.getBlockZ());
        });
    }
    @EventHandler public void join(PlayerJoinEvent event){for(ItemStack item:event.getPlayer().getInventory().getContents())observe(item,"player:"+event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void enchant(org.bukkit.event.enchantment.EnchantItemEvent event){if(items.id(event.getItem()).equals("legendary_scythe")&&!configs.apply("scythe").getBoolean("enchanting-enabled",true))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event){
        ItemStack base=event.getInventory().getItem(0),result=event.getResult();
        if(base==null||result==null||!items.id(base).equals("legendary_scythe"))return;
        if(!configs.apply("scythe").getBoolean("enchanting-enabled",true)&&!base.getEnchantments().equals(result.getEnchantments()))event.setResult(null);
        if(!configs.apply("scythe").getBoolean("repair-enabled",true)&&base.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable before&&result.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable after&&after.getDamage()<before.getDamage())event.setResult(null);
    }
    private void observe(ItemStack item,String where){UUID id=identity(item);if(id!=null){store.put("scythe_location",id.toString(),where);seen.put(id,dev.duckmc.blastforge.Tasks.tick());}}
    /** Last tick each scythe was seen somewhere (used to cancel a pending Creative deletion). */
    private final Map<UUID,Integer> seen=new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Marks a scythe permanently destroyed: its slot frees up, the Dark Ritual and the heart/wing drops resume.
     * Nobody receives a replacement. Returns false if it was not an active scythe.
     */
    public boolean destroy(UUID id,String how){
        if(!store.destroyScythe(id))return false;
        store.put("scythe_location",id.toString(),"destroyed:"+how);
        plugin.getLogger().info("Legendary Scythe "+id+" was permanently destroyed ("+how+"). Scythes: "+store.occupiedScythes()+"/"+maximum()+(available()?" - the Dark Ritual is available again.":"."));
        return true;
    }
    /** An admin brings a wrongly destroyed scythe back, if the limit allows it. */
    public boolean restore(UUID id){
        if(!"DESTROYED".equals(store.scythes().get(id.toString())) || !available())return false;
        if(!store.reactivateScythe(id,maximum()))return false;store.put("scythe_location",id.toString(),"restored by an administrator");return true;
    }
    /**
     * A dropped scythe (an item entity) is only counted as destroyed when Minecraft itself destroys it:
     * burnt by lava/fire, cut by cactus, blown up, fallen into the void or despawned. Chunk unloads, pick-ups,
     * hoppers, dimension travel and quitting are ignored. Removal by another plugin is only trusted when
     * plugin-removal-destroys is true (default false), otherwise it is logged for an admin to decide.
     */
    @EventHandler(priority=EventPriority.MONITOR)
    public void removed(org.bukkit.event.entity.EntityRemoveEvent event){
        if(!(event.getEntity() instanceof Item item))return;UUID id=identity(item.getItemStack());if(id==null)return;
        String where=item.getWorld().getName()+" "+item.getLocation().getBlockX()+" "+item.getLocation().getBlockY()+" "+item.getLocation().getBlockZ();
        switch(event.getCause()){
            case DEATH->destroy(id,"burnt or broken as a dropped item at "+where+(item.getLastDamageCause()!=null?" by "+item.getLastDamageCause().getCause().name().toLowerCase(Locale.ROOT):""));
            case EXPLODE->destroy(id,"blown up as a dropped item at "+where);
            case OUT_OF_WORLD->destroy(id,"fell into the void at "+where);
            case DESPAWN->destroy(id,"despawned as a dropped item at "+where);
            case PLUGIN,DISCARD->{
                if(configs.apply("scythe").getBoolean("plugin-removal-destroys",false))destroy(id,"removed by another plugin at "+where);
                else{store.put("scythe_location",id.toString(),"unconfirmed removal ("+event.getCause()+") at "+where);
                    plugin.getLogger().warning("Legendary Scythe "+id+" was removed at "+where+" by "+event.getCause()+"; it is still counted. If it is really gone, use /forge scythe destroy "+id);}
            }
            default->{}   // UNLOAD, PICKUP, MERGE, ENTER_BLOCK, PLAYER_QUIT, TRANSFORMATION, DROP, HIT: still exists
        }
    }
    /**
     * Creative mode: the server cannot see an item held on the creative cursor, so emptying a slot could be a pick-up
     * or a deletion. A scythe that leaves a creative slot is only declared destroyed once the inventory is closed,
     * 2 seconds have passed, and it is in neither the player's inventory, the container they had open, nor dropped.
     * Creative copies of a scythe (middle-click / pick-block) are refused.
     */
    private final Map<UUID,UUID> creativePending=new java.util.concurrent.ConcurrentHashMap<>();   // scythe -> player
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void creative(InventoryCreativeEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        UUID placed=identity(event.getCursor());
        if(placed!=null){
            int copies=0;for(ItemStack other:player.getInventory().getContents())if(placed.equals(identity(other)))copies++;
            ItemStack replaced=event.getCurrentItem();if(placed.equals(identity(replaced)))copies--;
            if(copies>0){event.setCancelled(true);player.sendMessage(net.kyori.adventure.text.Component.text("The Legendary Scythe cannot be copied.",net.kyori.adventure.text.format.NamedTextColor.RED));return;}
            creativePending.remove(placed);seen.put(placed,dev.duckmc.blastforge.Tasks.tick());
        }
        UUID taken=identity(event.getCurrentItem());
        if(taken!=null && !taken.equals(placed))creativePending.put(taken,player.getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void creativeClose(InventoryCloseEvent event){checkCreative(event.getPlayer().getUniqueId(),event.getInventory(),false);}
    @EventHandler(priority=EventPriority.MONITOR)
    public void creativeQuit(PlayerQuitEvent event){checkCreative(event.getPlayer().getUniqueId(),null,true);}
    private void checkCreative(UUID player,org.bukkit.inventory.Inventory container,boolean quitting){
        for(var pending:new ArrayList<>(creativePending.entrySet())){
            if(!pending.getValue().equals(player))continue;UUID id=pending.getKey();int since=dev.duckmc.blastforge.Tasks.tick();
            Runnable check=()->{
                if(!player.equals(creativePending.get(id)))return;
                if(seen.getOrDefault(id,-1)>=since){creativePending.remove(id);return;}   // it was dropped or placed somewhere
                Player p=Bukkit.getPlayer(player);
                if(p!=null && dev.duckmc.blastforge.Tasks.owns(p)){
                    for(ItemStack item:p.getInventory().getContents())if(id.equals(identity(item))){creativePending.remove(id);return;}
                    if(id.equals(identity(p.getItemOnCursor()))){creativePending.remove(id);return;}
                    // a scythe thrown out of a creative inventory lands next to the player (no whole-world search: not allowed on Folia)
                    for(Item item:p.getWorld().getNearbyEntitiesByType(Item.class,p.getLocation(),32))if(id.equals(identity(item.getItemStack()))){creativePending.remove(id);return;}
                }
                if(container!=null)for(ItemStack item:container.getContents())if(id.equals(identity(item))){creativePending.remove(id);return;}
                creativePending.remove(id);destroy(id,"deleted in Creative mode by "+(p!=null?p.getName():player));
            };
            Player online=Bukkit.getPlayer(player);
            if(online!=null&&!quitting)dev.duckmc.blastforge.Tasks.entityLater(plugin,online,40,check);   // on the player's own thread
            else dev.duckmc.blastforge.Tasks.globalLater(plugin,40,check);
        }
    }
    public void close(){HandlerList.unregisterAll(this);}
}
