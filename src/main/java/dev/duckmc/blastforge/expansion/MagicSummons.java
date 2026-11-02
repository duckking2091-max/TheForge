package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Timed marked summons. Target restrictions and reward suppression apply for their entire lifetime. */
final class MagicSummons implements Listener,AutoCloseable {
    static final NamespacedKey MARK=ExpansionItems.key("magic_summon");
    private static final NamespacedKey EXPIRY=ExpansionItems.key("magic_summon_expires"),KIND=ExpansionItems.key("magic_summon_kind");
    private record Record(UUID owner,long expiry,boolean darkness){}
    private final JavaPlugin plugin;private final PersistentStore store;private final ExpansionProtection protection;private final MagicPassives passives;private final BooleanSupplier pvp;
    private final Map<UUID,Record> records=new java.util.concurrent.ConcurrentHashMap<>();
    /** Summons spawned this session, so the global loop can reach them on Folia (see Tasks.find). */
    private final Map<UUID,Entity> live=new java.util.concurrent.ConcurrentHashMap<>();
    MagicSummons(JavaPlugin plugin,PersistentStore store,ExpansionProtection protection,MagicPassives passives,BooleanSupplier pvp){this.plugin=plugin;this.store=store;this.protection=protection;this.passives=passives;this.pvp=pvp;
        for(var e:store.entries("magic.summons").entrySet())try{var p=e.getValue().split("\\|");records.put(UUID.fromString(e.getKey()),new Record(UUID.fromString(p[0]),Long.parseLong(p[1]),Boolean.parseBoolean(p[2])));}catch(RuntimeException bad){store.remove("magic.summons",e.getKey());}
    }
    boolean marked(Entity e){return e.getPersistentDataContainer().has(MARK,PersistentDataType.STRING);}
    private Record record(Entity e){Record r=records.get(e.getUniqueId());if(r!=null)return r;try{var pdc=e.getPersistentDataContainer();String owner=pdc.get(MARK,PersistentDataType.STRING);Long expiry=pdc.get(EXPIRY,PersistentDataType.LONG);return owner==null || expiry==null?null:new Record(UUID.fromString(owner),expiry,"darkness".equals(pdc.get(KIND,PersistentDataType.STRING)));}catch(IllegalArgumentException malformed){return null;}}
    boolean summon(Player player,boolean darkness,double duration,int count){
        count=Math.max(1,Math.min(3,count));
        if(player.getWorld().getDifficulty()==Difficulty.PEACEFUL)return fail(player,"Summons cannot appear on Peaceful difficulty.");
        if(records.size()+count>96 || records.values().stream().filter(r->r.owner.equals(player.getUniqueId())).count()+count>12)return fail(player,"Too many summons are active already.");
        // Look for free spots all around the caster (2 rings x 12 directions), not just 3 fixed ones.
        List<Location> locations=new ArrayList<>();
        search:for(double dist:new double[]{2.5,1.6})for(int k=0;k<12;k++){
            double angle=player.getLocation().getYaw()*Math.PI/180+k*Math.PI*2/12;
            Location base=player.getLocation().clone().add(Math.cos(angle)*dist,0,Math.sin(angle)*dist);
            for(int dy=2;dy>=-2;dy--){Location at=base.clone().add(0,dy,0);at.setY(Math.floor(at.getY()));World w=at.getWorld();if(!w.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))continue;
                if(at.getBlock().isPassable() && at.clone().add(0,1,0).getBlock().isPassable() && at.clone().add(0,-1,0).getBlock().isSolid() && protection.canSpawn(at) && protection.canAffect(player,at)
                   && locations.stream().noneMatch(o->o.distanceSquared(at)<1)){locations.add(at);break;}}
            if(locations.size()>=count)break search;
        }
        if(locations.size()<count)return fail(player,"No room to summon here: they need 2 free blocks on solid ground around you.");
        long expiry=System.currentTimeMillis()+(long)(Math.min(120,Math.max(1,duration))*1000);List<Mob> spawned=new ArrayList<>();
        try{
            for(Location loc:locations){
                Mob mob=darkness?loc.getWorld().spawn(loc,WitherSkeleton.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,m->configure(m,player,true,expiry)):
                    loc.getWorld().spawn(loc,Drowned.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,m->configure(m,player,false,expiry));
                if(!mob.isValid() || mob.isDead())throw new IllegalStateException("Summon denied by spawn rules");spawned.add(mob);
                store.put("magic.summons",mob.getUniqueId().toString(),player.getUniqueId()+"|"+expiry+"|"+darkness);live.put(mob.getUniqueId(),mob);records.put(mob.getUniqueId(),new Record(player.getUniqueId(),expiry,darkness));
            }
            return true;
        }catch(RuntimeException denied){for(Mob mob:spawned)remove(mob);plugin.getLogger().info("Summon for "+player.getName()+" was blocked: "+denied.getMessage()+" (another plugin or a region cancelled the spawn)");
            return fail(player,"The summon was blocked by the server (spawn protection, a region, or another plugin).");}
    }
    private boolean fail(Player player,String why){player.sendActionBar(net.kyori.adventure.text.Component.text(why,net.kyori.adventure.text.format.NamedTextColor.RED));return false;}
    private void configure(Mob mob,Player owner,boolean darkness,long expiry){
        var pdc=mob.getPersistentDataContainer();pdc.set(MARK,PersistentDataType.STRING,owner.getUniqueId().toString());pdc.set(EXPIRY,PersistentDataType.LONG,expiry);pdc.set(KIND,PersistentDataType.STRING,darkness?"darkness":"water");
        mob.setPersistent(false);mob.setRemoveWhenFarAway(true);mob.setCanPickupItems(false);mob.setLootTable(null);
        EntityEquipment equipment=mob.getEquipment();if(equipment!=null){equipment.clear();if(darkness){ItemStack sword=new ItemStack(Material.STONE_SWORD);sword.addEnchantment(Enchantment.SHARPNESS,2);equipment.setItemInMainHand(sword);}else equipment.setHelmet(new ItemStack(Material.IRON_HELMET));for(EquipmentSlot slot:List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))equipment.setDropChance(slot,0);}
        if(mob instanceof Drowned drowned){drowned.setShouldBurnInDay(false);drowned.setCanBreakDoors(false);}
    }
    private boolean allowed(Record r,LivingEntity target){
        if(marked(target))return false;
        Player owner=Bukkit.getPlayer(r.owner);if(owner==null || !owner.isOnline() || owner.isDead() || !owner.getWorld().equals(target.getWorld()) || !protection.canAffect(owner,target.getLocation()))return false;
        // Summons never turn on their own summoner, whatever the game mode.
        if(target.equals(owner) || target instanceof ArmorStand || target instanceof Tameable t && owner.equals(t.getOwner()))return false;
        if(target instanceof Player tp && (tp.getGameMode()==GameMode.CREATIVE || tp.getGameMode()==GameMode.SPECTATOR))return false;
        // Drowned only fight hostile mobs and players; villagers, animals and golems are left alone.
        if(!r.darkness && !(target instanceof Enemy || target instanceof Player))return false;
        if(target instanceof Player && (!pvp.getAsBoolean() || !owner.getWorld().getPVP()))return false;
        return !(target instanceof Player p && (passives.books(p)&MagicPassives.DARKNESS)!=0) && protection.canDamage(owner,target);
    }
    /** Called from the global tick; each summon is then checked on its own thread (its region on Folia). */
    void tick(long now){for(var item:new ArrayList<>(records.entrySet())){
        Entity entity=dev.duckmc.blastforge.Tasks.find(live,item.getKey());Record r=item.getValue();
        if(entity==null){if(r.expiry<=now){store.remove("magic.summons",item.getKey().toString());records.remove(item.getKey());}continue;}
        dev.duckmc.blastforge.Tasks.now(plugin,entity,()->check(entity,r,now));
    }}
    private void check(Entity entity,Record r,long now){
        Player owner=Bukkit.getPlayer(r.owner);
        if(!(entity instanceof Mob mob) || r.expiry<=now || owner==null || !owner.isOnline() || owner.isDead() || !owner.getWorld().equals(entity.getWorld())){remove(entity);return;}

        if(mob.getTarget()==null || !mob.getTarget().isValid() || !allowed(r,mob.getTarget())){
            // nearest valid enemy; without one they stop attacking and walk back to their owner
            LivingEntity target=entity.getWorld().getNearbyLivingEntities(entity.getLocation(),12,t->!t.equals(entity) && t.isValid() && !t.isDead() && allowed(r,t)).stream().min(Comparator.comparingDouble(t->t.getLocation().distanceSquared(entity.getLocation()))).orElse(null);
            mob.setTarget(target);
            if(target==null && mob.getLocation().distanceSquared(owner.getLocation())>36)mob.getPathfinder().moveTo(owner.getLocation(),1.1);
        }
    
    }
    void remove(Entity e){live.remove(e.getUniqueId());store.remove("magic.summons",e.getUniqueId().toString());records.remove(e.getUniqueId());e.remove();}
    void removeOwner(UUID owner){for(var e:new ArrayList<>(records.entrySet()))if(e.getValue().owner.equals(owner)){Entity mob=Bukkit.getEntity(e.getKey());if(mob!=null)remove(mob);else{store.remove("magic.summons",e.getKey().toString());records.remove(e.getKey());}}}
    /** Removes leftover summons in loaded chunks. Not possible on Folia (no thread may see every region): there they are removed as their chunks load. */
    void cleanupLoaded(){if(dev.duckmc.blastforge.Tasks.folia())return;for(World world:Bukkit.getWorlds())for(Entity e:world.getEntities())if(marked(e))remove(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){if(!marked(e.getEntity()))return;Record r=record(e.getEntity());if(e.getTarget()!=null && (r==null || !allowed(r,e.getTarget())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){Entity source=e.getDamager();if(source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)source=shooter;if(!marked(source))return;Record r=record(source);if(!(e.getEntity() instanceof LivingEntity target) || r==null || !allowed(r,target))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void death(EntityDeathEvent e){if(marked(e.getEntity())){e.getDrops().clear();e.setDroppedExp(0);store.remove("magic.summons",e.getEntity().getUniqueId().toString());records.remove(e.getEntity().getUniqueId());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(marked(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(EntityDropItemEvent e){if(marked(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void transform(EntityTransformEvent e){if(marked(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void block(EntityChangeBlockEvent e){if(marked(e.getEntity()))e.setCancelled(true);}
    @EventHandler public void load(EntitiesLoadEvent e){for(Entity entity:e.getEntities())if(marked(entity))remove(entity);}
    @EventHandler public void unload(EntitiesUnloadEvent e){for(Entity entity:e.getEntities())if(marked(entity))remove(entity);}
    @Override public void close(){cleanupLoaded();for(UUID id:new ArrayList<>(records.keySet())){store.remove("magic.summons",id.toString());records.remove(id);}HandlerList.unregisterAll(this);}
}
