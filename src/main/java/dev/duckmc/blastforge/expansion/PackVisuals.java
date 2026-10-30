package dev.duckmc.blastforge.expansion;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

/** Optional pack session, GUI glyphs and recipient-only armor presentation. */
public final class PackVisuals implements Listener,AutoCloseable {
    public static final UUID PACK_ID=UUID.nameUUIDFromBytes("blastforge:1.21.11:expansion".getBytes(StandardCharsets.UTF_8));
    public enum Theme { FORGING,UPGRADING,RITUAL }
    private final JavaPlugin plugin;private final ExpansionItems items;private final Function<String,FileConfiguration> configs;
    private final Set<UUID> loaded=new HashSet<>();private final List<Consumer<Player>> observers=new ArrayList<>();
    private final Map<UUID,String> titles=new HashMap<>();private BukkitTask task;private int offset;
    public PackVisuals(JavaPlugin plugin,ExpansionItems items,Function<String,FileConfiguration> configs){this.plugin=plugin;this.items=items;this.configs=configs;}
    public void start(){if(task!=null)return;plugin.getServer().getPluginManager().registerEvents(this,plugin);for(Player p:plugin.getServer().getOnlinePlayers())request(p);task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::refreshArmor,40,40);}
    /** True when this player shows the pack: the server-sent pack loaded, or the admin states every player installs it manually. */
    public boolean loaded(Player player){return loaded.contains(player.getUniqueId())||configs.apply("config").getBoolean("resource-pack.assume-installed",true);}
    public void onStatusChange(Consumer<Player> observer){observers.add(Objects.requireNonNull(observer));}
    private static String label(Theme theme){return switch(theme){case FORGING->"Forging";case UPGRADING->"Upgrade Gear";case RITUAL->"Dark Ritual";};}
    private String text(Player p,Theme theme,int mask){return loaded(p)?"\ue0f0"+(char)('\ue000'+theme.ordinal())+"\ue0f1"+(theme==Theme.FORGING?outline(mask):"")+label(theme):label(theme);}
    private static String outline(int mask){return switch(mask&3){case 1->"\ue0f0\ue010\ue0f1";case 2->"\ue0f0\ue011\ue0f1";case 3->"\ue0f0\ue012\ue0f1";default->"";};}
    /** Forging and upgrading use light vanilla-style panels, so their titles are dark grey like vanilla's. */
    private static NamedTextColor ink(Theme theme){return switch(theme){case RITUAL->NamedTextColor.LIGHT_PURPLE;case FORGING->NamedTextColor.GOLD;default->NamedTextColor.WHITE;};}
    /** The background picture is drawn white (text colour multiplies it); only the title words are coloured. */
    public Component title(Player player,Theme theme,int inputMask){
        if(!loaded(player))return Component.text(label(theme),NamedTextColor.AQUA);
        String full=text(player,theme,inputMask),words=label(theme),art=full.substring(0,full.length()-words.length());
        return Component.text(art,NamedTextColor.WHITE).append(Component.text(words,ink(theme)));
    }
    /** Safe to call from click handlers: mutation is deferred and checked against the same inventory. */
    public void updateTitle(Player player,InventoryView view,Theme theme,int inputMask){
        Inventory inventory=view.getTopInventory();plugin.getServer().getScheduler().runTask(plugin,()->{
            if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=inventory)return;
            String value=(loaded(player)?(theme==Theme.RITUAL?"§d":theme==Theme.FORGING?"§6":"§8"):"§b")+text(player,theme,inputMask);String identity=System.identityHashCode(inventory)+":"+value;
            if(identity.equals(titles.get(player.getUniqueId())))return;view.setTitle(value);titles.put(player.getUniqueId(),identity);
        });
    }
    /** Fix old armor as soon as a player could try to wear it. */
    private void repair(Player player){for(ItemStack stack:player.getInventory().getContents())items.repairEquippable(stack);}
    @EventHandler(priority=EventPriority.LOWEST) public void repairOnUse(PlayerInteractEvent event){if(items.repairEquippable(event.getItem()))event.getPlayer().updateInventory();}
    @EventHandler(priority=EventPriority.LOWEST) public void repairOnClick(org.bukkit.event.inventory.InventoryClickEvent event){boolean a=items.repairEquippable(event.getCurrentItem()),b=items.repairEquippable(event.getCursor());if(a||b&&event.getWhoClicked() instanceof Player p)plugin.getServer().getScheduler().runTask(plugin,()->{if(event.getWhoClicked() instanceof Player p)p.updateInventory();});}
    @EventHandler public void repairOnJoin(PlayerJoinEvent event){repair(event.getPlayer());}
    @EventHandler public void join(PlayerJoinEvent event){plugin.getServer().getScheduler().runTaskLater(plugin,()->request(event.getPlayer()),20);}
    /** True when the pack comes from server.properties (resource-pack=...): the server sends it itself on join. */
    private boolean serverPack(){
        var c=configs.apply("config");String own=c.getString("resource-pack.url","");
        return own.isBlank()&&!plugin.getServer().getResourcePack().isBlank();
    }
    private void request(Player player){
        if(!player.isOnline()||!configs.apply("config").getBoolean("resource-pack.enabled",true))return;
        if(serverPack())return;   // Minecraft already offers the server.properties pack; just listen for its status
        String url=configs.apply("config").getString("resource-pack.url",""),hash=configs.apply("config").getString("resource-pack.sha1","");
        if(url.isBlank())return;  // no pack configured anywhere
        if(!url.startsWith("https://")||!hash.matches("[0-9a-fA-F]{40}")){plugin.getLogger().warning("The Forge pack requires a direct HTTPS URL and a 40-digit ZIP SHA-1; keeping vanilla visuals.");return;}
        player.addResourcePack(PACK_ID,url,HexFormat.of().parseHex(hash),configs.apply("config").getString("resource-pack.prompt","The Forge models and interfaces"),false);
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event){
        if(!PACK_ID.equals(event.getID())&&!serverPack())return;
        boolean before=loaded(event.getPlayer());if(event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)loaded.add(event.getPlayer().getUniqueId());else if(event.getStatus()!=PlayerResourcePackStatusEvent.Status.ACCEPTED&&event.getStatus()!=PlayerResourcePackStatusEvent.Status.DOWNLOADED)loaded.remove(event.getPlayer().getUniqueId());
        if(before!=loaded(event.getPlayer())){titles.remove(event.getPlayer().getUniqueId());for(var observer:List.copyOf(observers))observer.accept(event.getPlayer());refreshViewer(event.getPlayer());}
    }
    @EventHandler public void quit(PlayerQuitEvent event){loaded.remove(event.getPlayer().getUniqueId());titles.remove(event.getPlayer().getUniqueId());}
    @EventHandler(ignoreCancelled=true) public void track(PlayerTrackEntityEvent event){if(event.getEntity() instanceof LivingEntity living)plugin.getServer().getScheduler().runTask(plugin,()->{if(event.getPlayer().isOnline()&&living.isValid())armor(event.getPlayer(),living,false);});}
    @EventHandler public void changed(PlayerArmorChangeEvent event){plugin.getServer().getScheduler().runTask(plugin,()->{Player entity=event.getPlayer();if(!entity.isOnline())return;armor(entity,entity,true);for(Player viewer:entity.getTrackedBy())armor(viewer,entity,true);});}
    @EventHandler public void respawn(PlayerRespawnEvent event){plugin.getServer().getScheduler().runTaskLater(plugin,()->refreshViewer(event.getPlayer()),2);}
    @EventHandler public void world(PlayerChangedWorldEvent event){plugin.getServer().getScheduler().runTaskLater(plugin,()->refreshViewer(event.getPlayer()),2);}
    private void refreshViewer(Player viewer){if(!viewer.isOnline())return;armor(viewer,viewer,true);for(Player entity:viewer.getWorld().getPlayers())if(entity!=viewer&&entity.getTrackedBy().contains(viewer))armor(viewer,entity,true);}
    private void refreshArmor(){var players=new ArrayList<>(plugin.getServer().getOnlinePlayers());if(players.isEmpty())return;int checks=Math.min(players.size(),16);for(int i=0;i<checks;i++){Player entity=players.get(Math.floorMod(offset++,players.size()));armor(entity,entity,false);for(Player viewer:entity.getTrackedBy())armor(viewer,entity,false);}}
    private void armor(Player viewer,LivingEntity entity,boolean reset){
        if(!viewer.isOnline()||!entity.isValid()||!viewer.getWorld().equals(entity.getWorld())||!viewer.canSee(entity))return;
        var equipment=entity.getEquipment();if(equipment==null)return;var map=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);boolean custom=false;
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
            ItemStack actual=equipment.getItem(slot);ItemStack displayed=actual.clone();String id=items.id(actual),tier=ExpansionItems.tier(id);
            if(ExpansionItems.isArmor(ExpansionItems.piece(id))&&!tier.isEmpty()){custom=true;if(loaded(viewer)){var meta=displayed.getItemMeta();var component=meta.getEquippable();component.setSlot(slot);
                    // The Wardenite helmet has a real 3D model: with no armor model set, Minecraft draws the item itself on the head.
                    component.setModel(tier.equals("wardenite")&&slot==EquipmentSlot.HEAD&&configs.apply("config").getBoolean("visuals.wardenite-3d-helmet",true)?null:new NamespacedKey("blastforge",tier));meta.setEquippable(component);displayed.setItemMeta(meta);}}
            map.put(slot,displayed);
        }
        if(custom||reset)viewer.sendEquipmentChange(entity,map);
    }
    @Override public void close(){if(task!=null){task.cancel();task=null;}loaded.clear();for(Player player:plugin.getServer().getOnlinePlayers())refreshViewer(player);titles.clear();observers.clear();HandlerList.unregisterAll(this);}
}
