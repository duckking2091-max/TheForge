package dev.duckmc.blastforge.expansion;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.Function;

/**
 * Miner / Magician outfits drawn by the villager model itself, with no extra entities:
 *  - the hat is head equipment (drawn on the head, like a carved pumpkin: turns and tilts with it);
 *  - the outfit (overalls + backpack, or robe + book) is the item in the villager's crossed arms, which the
 *    villager model draws fixed to its body; the model's "ground" transform moves it from the arms onto the body.
 * Nothing can float, trail, duplicate or be left behind. Every tick the outfit is matched to the villager's
 * current job: a Magician that is no longer a Librarian (or a Miner no longer a Toolsmith) is undressed, and
 * dressed again if the job comes back.
 */
final class MerchantAppearance implements Listener,AutoCloseable {
    private static final NamespacedKey KIND=new NamespacedKey("blastforge","villager_kind");
    private static final Set<String> OUTFIT_IDS=Set.of("magician_hat","magician_robes","miner_helmet","miner_backpack");
    private final JavaPlugin plugin;private final ExpansionItems items;private final Function<String,FileConfiguration> configs;private final PackVisuals pack;
    private final Map<UUID,String> tracked=new HashMap<>();private BukkitTask task;
    MerchantAppearance(JavaPlugin plugin,ExpansionItems items,Function<String,FileConfiguration> configs,PackVisuals pack){
        this.plugin=plugin;this.items=items;this.configs=configs;this.pack=pack;if(pack!=null)pack.onStatusChange(this::visibility);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    static String hat(String kind){return kind.equals("magician")?"magician_hat":"miner_helmet";}
    static String outfit(String kind){return kind.equals("magician")?"magician_robes":"miner_backpack";}
    static Villager.Profession job(String kind){return kind.equals("magician")?Villager.Profession.LIBRARIAN:Villager.Profession.TOOLSMITH;}
    void track(AbstractVillager merchant,String kind){
        tracked.put(merchant.getUniqueId(),kind);sync(merchant,kind);
        if(task==null)task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    /** Dress or undress to match the villager's current job. */
    private void sync(AbstractVillager merchant,String kind){
        boolean dressed=configs.apply("config").getBoolean("visuals.enabled",true)&&kind.equals(merchant.getPersistentDataContainer().getOrDefault(KIND,PersistentDataType.STRING,""))
            &&(!(merchant instanceof Villager v)||v.getProfession()==job(kind));
        EntityEquipment eq=merchant.getEquipment();if(eq==null)return;
        if(!dressed){undress(eq);return;}
        if(!hat(kind).equals(items.id(eq.getHelmet()))){eq.setHelmet(items.create(hat(kind)));eq.setHelmetDropChance(0);for(Player p:merchant.getTrackedBy())hide(p,merchant);}
        // Vanilla briefly shows trade items in the arms (and then empties them); put the outfit back afterwards.
        ItemStack hand=eq.getItemInMainHand();
        if(hand.getType().isAir()||(OUTFIT_IDS.contains(items.id(hand))&&!outfit(kind).equals(items.id(hand)))||hand.getType()==Material.IRON_PICKAXE){
            eq.setItemInMainHand(items.create(outfit(kind)));eq.setItemInMainHandDropChance(0);for(Player p:merchant.getTrackedBy())hide(p,merchant);}
    }
    private void undress(EntityEquipment eq){
        if(OUTFIT_IDS.contains(items.id(eq.getHelmet())))eq.setHelmet(null);
        if(OUTFIT_IDS.contains(items.id(eq.getItemInMainHand())))eq.setItemInMainHand(null);
    }
    void tick(){
        for(var entry:new ArrayList<>(tracked.entrySet())){
            Entity entity=plugin.getServer().getEntity(entry.getKey());
            if(!(entity instanceof AbstractVillager merchant)||!merchant.isValid()){tracked.remove(entry.getKey());continue;}
            sync(merchant,entry.getValue());
        }
        if(tracked.isEmpty()&&task!=null){task.cancel();task=null;}
    }
    /** Villagers that stopped being merchants while unloaded (or in an older version) lose any leftover outfit pieces. */
    @EventHandler public void loaded(EntitiesLoadEvent event){
        for(Entity entity:event.getEntities())if(entity instanceof AbstractVillager v&&v.getEquipment()!=null){
            String kind=v.getPersistentDataContainer().getOrDefault(KIND,PersistentDataType.STRING,"");
            if(kind.isEmpty())undress(v.getEquipment());
            for(Entity passenger:v.getPassengers())if(passenger instanceof ItemDisplay d&&OUTFIT_IDS.contains(items.id(d.getItemStack()))){v.removePassenger(d);d.remove();}   // rc.27 riders
        }
    }
    private void visibility(Player viewer){for(UUID id:tracked.keySet())if(plugin.getServer().getEntity(id) instanceof AbstractVillager m&&m.getWorld().equals(viewer.getWorld()))hide(viewer,m);}
    /** Players without the resource pack would see paper items: send them empty slots instead. */
    private void hide(Player viewer,AbstractVillager merchant){
        if(pack==null||pack.loaded(viewer)||merchant.getEquipment()==null)return;
        if(OUTFIT_IDS.contains(items.id(merchant.getEquipment().getHelmet())))viewer.sendEquipmentChange(merchant,EquipmentSlot.HEAD,new ItemStack(Material.AIR));
        if(OUTFIT_IDS.contains(items.id(merchant.getEquipment().getItemInMainHand())))viewer.sendEquipmentChange(merchant,EquipmentSlot.HAND,new ItemStack(Material.AIR));
    }
    @EventHandler public void tracked(PlayerTrackEntityEvent event){if(event.getEntity() instanceof AbstractVillager merchant&&tracked.containsKey(merchant.getUniqueId())){Player viewer=event.getPlayer();plugin.getServer().getScheduler().runTask(plugin,()->{if(viewer.isOnline()&&merchant.isValid())hide(viewer,merchant);});}}
    /** The villager stops being a Miner/Magician: undress it now. */
    void strip(AbstractVillager merchant){tracked.remove(merchant.getUniqueId());if(merchant.getEquipment()!=null)undress(merchant.getEquipment());}
    void remove(UUID id){tracked.remove(id);}
    @Override public void close(){if(task!=null){task.cancel();task=null;}tracked.clear();HandlerList.unregisterAll(this);}
}
