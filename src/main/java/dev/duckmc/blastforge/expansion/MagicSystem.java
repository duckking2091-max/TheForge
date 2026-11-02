package dev.duckmc.blastforge.expansion;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.Comparator;
import java.util.function.Function;

/** All elemental and legendary weapon gameplay. The item catalog and scythe capacity stay external. */
public final class MagicSystem implements Listener,AutoCloseable {
    private final JavaPlugin plugin;private final ExpansionItems items;private final Function<String,FileConfiguration> configs;
    private final PersistentStore store;private final ExpansionProtection protection;private final MagicCooldowns cooldowns;
    private final MagicPassives passives;private final MagicFlights flights;private final MagicTerrain terrain;private final MagicSummons summons;
    /** Each player's running spell effects, touched only on that player's own thread (their region on Folia). */
    private final Map<UUID,List<Effect>> effects=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,Integer> dispatched=new java.util.concurrent.ConcurrentHashMap<>();private final Map<UUID,Long> feedback=new java.util.concurrent.ConcurrentHashMap<>();
    /** One repeating task per online player (follows them between regions), plus one global task. */
    private final Map<UUID,dev.duckmc.blastforge.Tasks.Handle> playerTasks=new java.util.concurrent.ConcurrentHashMap<>();
    private void addEffect(Effect effect){effects.computeIfAbsent(effect.owner(),k->new java.util.concurrent.CopyOnWriteArrayList<>()).add(effect);}
    private int effectCount(){int n=0;for(var list:effects.values())n+=list.size();return n;}
    private dev.duckmc.blastforge.Tasks.Handle task;private long ticks;private final dev.duckmc.blastforge.Reentry internalDamage=new dev.duckmc.blastforge.Reentry();private boolean closed;
    private interface Effect {UUID owner();boolean tick();default void close(){}}
    public MagicSystem(JavaPlugin plugin,ExpansionItems items,Function<String,FileConfiguration> configs,PersistentStore store,ExpansionProtection protection){
        this.plugin=plugin;this.items=items;this.configs=configs;this.store=store;this.protection=protection;
        cooldowns=new MagicCooldowns(store);passives=new MagicPassives(items);flights=new MagicFlights(store,configs);
        terrain=new MagicTerrain(plugin,configs,store,protection);summons=new MagicSummons(plugin,store,protection,passives,()->magic().getBoolean("pvp",true));
    }
    public void start(){if(task!=null)return;closed=false;var manager=plugin.getServer().getPluginManager();manager.registerEvents(this,plugin);manager.registerEvents(terrain,plugin);manager.registerEvents(summons,plugin);
        summons.cleanupLoaded();for(Player p:Bukkit.getOnlinePlayers()){flights.reconcile(p);passives.scan(p);}
        task=dev.duckmc.blastforge.Tasks.globalTimer(plugin,1,1,this::globalPump);
        for(Player p:Bukkit.getOnlinePlayers())startPlayer(p);
    }
    /** Starts the player's own tick (spells, flight, cooldown bar, passives) on the thread that owns the player. */
    private void startPlayer(Player p){
        if(closed)return;var old=playerTasks.remove(p.getUniqueId());if(old!=null)old.cancel();
        UUID id=p.getUniqueId();
        playerTasks.put(id,dev.duckmc.blastforge.Tasks.entityTimer(plugin,p,1,1,handle->{if(closed||!p.isOnline()){handle.cancel();return;}playerPump(p);},()->playerTasks.remove(id)));
    }
    private FileConfiguration magic(){return configs.apply("magic");}
    private String path(String item,int ability){if(item.equals("wind_book") && ability>0)ability=3-ability;   /* suffocation = "third", wind charge = "secondary" */
        return "books."+item.replace("_book","")+"."+List.of("primary","secondary","third").get(ability);}
    private String scythePath(int ability){return List.of("dash","flight","windmill").get(ability);}
    private double value(String item,int ability,String key,double fallback){return item.equals("legendary_scythe")?configs.apply("scythe").getDouble(scythePath(ability)+"."+key,fallback):magic().getDouble(path(item,ability)+"."+key,fallback);}
    private double bounded(double value,double low,double high){return Double.isFinite(value)?Math.max(low,Math.min(high,value)):low;}
    private boolean validScythe(ItemStack stack){String id=stack.getItemMeta().getPersistentDataContainer().get(ExpansionItems.key("scythe_uuid"),PersistentDataType.STRING);return id!=null && "ACTIVE".equals(store.scythes().get(id));}
    private String activeItem(Player p){String id=items.id(p.getInventory().getItemInMainHand());return switch(id){case "fire_book","water_book","earth_book","wind_book","darkness_book"->id;case "legendary_scythe"->validScythe(p.getInventory().getItemInMainHand())?id:"";default->"";};}
    private boolean supported(String id,int ability){return !id.isEmpty() && ability>=0 && ability<3 && !(id.equals("earth_book") && ability==2);}
    private boolean dispatch(Player p,int ability){String id=activeItem(p);if(!supported(id,ability))return false;
        int tick=dev.duckmc.blastforge.Tasks.tick();if(Objects.equals(dispatched.put(p.getUniqueId(),tick),tick))return true;
        activate(p,ability);return true;
    }
    /** One activation path for every control route; success alone commits its persistent cooldown. */
    boolean activate(Player p,int ability){
        String id=activeItem(p);if(closed || !magic().getBoolean("enabled",true) || !supported(id,ability) || p.isDead() || p.getGameMode()==GameMode.SPECTATOR || !p.hasPermission("blastforge.use") || !protection.canAffect(p,p.getLocation()))return false;
        long now=System.currentTimeMillis(),remaining=cooldowns.remaining(p.getUniqueId(),id,ability,now);
        if(remaining>0){if(feedback.getOrDefault(p.getUniqueId(),0L)<now){p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.4f,.7f);feedback.put(p.getUniqueId(),now+750);}hud(p,now);return false;}
        if(effectCount()>=128 || effects.getOrDefault(p.getUniqueId(),List.of()).size()>=8)return false;
        boolean result=switch(id){
            case "fire_book"->fire(p,ability);case "water_book"->water(p,ability);case "earth_book"->earth(p,ability);
            case "wind_book"->wind(p,ability);case "darkness_book"->darkness(p,ability);case "legendary_scythe"->scythe(p,ability);default->false;
        };
        if(result){double fallback=switch(id){case "fire_book"->new double[]{45,90,60}[ability];case "water_book"->new double[]{30,90,60}[ability];case "earth_book"->new double[]{0,60,0}[ability];case "wind_book"->new double[]{45,90,30}[ability];case "darkness_book"->new double[]{60,120,90}[ability];default->new double[]{30,90,30}[ability];};
            cooldowns.commit(p.getUniqueId(),id,ability,now,bounded(value(id,ability,"cooldown",fallback),0,86400));hud(p,now);sound(p.getLocation(),Sound.BLOCK_ENCHANTMENT_TABLE_USE,.6f,1.3f);
        }return result;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND || internalDamage.active())return;Action action=e.getAction();boolean right=action==Action.RIGHT_CLICK_AIR || action==Action.RIGHT_CLICK_BLOCK;
        boolean left=action==Action.LEFT_CLICK_AIR || action==Action.LEFT_CLICK_BLOCK;if(!right && !left || left && !e.getPlayer().isSneaking())return;
        if(e.hasBlock() && (e.useInteractedBlock()==Event.Result.DENY || e.useItemInHand()==Event.Result.DENY))return;
        int ability=right?(e.getPlayer().isSneaking()?1:0):2;if(dispatch(e.getPlayer(),ability)){e.setUseItemInHand(Event.Result.DENY);e.setUseInteractedBlock(Event.Result.DENY);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityInteract(PlayerInteractEntityEvent e){if(e.getHand()==EquipmentSlot.HAND && dispatch(e.getPlayer(),e.getPlayer().isSneaking()?1:0))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityInteractAt(PlayerInteractAtEntityEvent e){if(e.getHand()==EquipmentSlot.HAND && dispatch(e.getPlayer(),e.getPlayer().isSneaking()?1:0))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void swing(PlayerArmSwingEvent e){if(e.getHand()==EquipmentSlot.HAND && e.getPlayer().isSneaking() && dispatch(e.getPlayer(),2))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void attackControl(EntityDamageByEntityEvent e){if(!internalDamage.active() && e.getDamager() instanceof Player p && p.isSneaking() && dispatch(p,2))e.setCancelled(true);}

    private abstract class OwnedEffect implements Effect {
        final UUID owner,world;int age;final int duration;
        OwnedEffect(Player p,int duration){owner=p.getUniqueId();world=p.getWorld().getUID();this.duration=duration;}
        public UUID owner(){return owner;}
        Player caster(){Player p=Bukkit.getPlayer(owner);return p!=null && p.isOnline() && !p.isDead() && p.getWorld().getUID().equals(world)?p:null;}
        public final boolean tick(){Player p=caster();if(p==null || age++>=duration)return false;return step(p);}
        abstract boolean step(Player p);
    }
    private int duration(String id,int ability,int fallback){return (int)(bounded(value(id,ability,"duration",fallback),.1,300)*20);}
    private boolean enemy(Player caster,LivingEntity target){
        if(target.equals(caster) || target instanceof ArmorStand || target instanceof Tameable || target.getPersistentDataContainer().has(MagicSummons.MARK))return false;
        if(target instanceof Player && (!magic().getBoolean("pvp",true) || !caster.getWorld().getPVP()))return false;
        return protection.canAffect(caster,target.getLocation()) && protection.canDamage(caster,target) && caster.hasLineOfSight(target);
    }
    private List<LivingEntity> enemies(Player caster,Location at,double radius){return at.getWorld().getNearbyLivingEntities(at,radius,target->at.distanceSquared(target.getLocation())<=radius*radius && enemy(caster,target)).stream().limit(32).toList();}
    private LivingEntity target(Player caster,double range){RayTraceResult hit=caster.getWorld().rayTrace(caster.getEyeLocation(),caster.getEyeLocation().getDirection(),range,FluidCollisionMode.NEVER,true,.3,e->e instanceof LivingEntity l && enemy(caster,l));return hit!=null && hit.getHitEntity() instanceof LivingEntity living?living:null;}
    private void damage(Player caster,LivingEntity target,double amount){if(!enemy(caster,target))return;internalDamage.enter();try{target.damage(bounded(amount,0,200),DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(caster).withDirectEntity(caster).withDamageLocation(caster.getLocation()).build());}finally{internalDamage.exit();}}
    private boolean fire(Player p,int ability){String id="fire_book";
        if(ability==0){
            // Fire Field: real fire blocks (as from flint and steel) on the ground between 2 and 5 blocks around the caster.
            Location center=p.getLocation();double inner=bounded(value(id,0,"safe-radius",2),0,4),outer=bounded(value(id,0,"radius",5),inner+1,8);
            List<Block> spots=new ArrayList<>();int r=(int)Math.ceil(outer);
            for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
                double dx=center.getBlockX()+x+.5-center.getX(),dz=center.getBlockZ()+z+.5-center.getZ(),d=Math.sqrt(dx*dx+dz*dz);
                if(d<=inner || d>outer)continue;
                Block ground=surface(center.getBlock().getRelative(x,0,z));if(ground!=null)spots.add(ground);
            }
            if(!terrain.change(p,spots,Material.FIRE,bounded(value(id,0,"fire-seconds",5),1,30),true))return false;
            for(LivingEntity target:enemies(p,center,outer+.5)){double dx=target.getX()-center.getX(),dz=target.getZ()-center.getZ();if(dx*dx+dz*dz>inner*inner)damage(p,target,value(id,0,"damage",8));}
            sound(center,Sound.ITEM_FLINTANDSTEEL_USE,1f,.8f);sound(center,Sound.ENTITY_BLAZE_SHOOT,.8f,.7f);return true;
        }
        if(ability==1){
            // Fire Ring: a continuous circle of flames that follows the caster and marks the exact damage radius.
            double radius=bounded(value(id,1,"radius",3),1,8);int life=duration(id,1,5);Map<UUID,Integer> hit=new HashMap<>();Set<UUID> inside=new HashSet<>();
            addEffect(new OwnedEffect(p,life){boolean step(Player caster){Location center=caster.getLocation();if(!protection.canAffect(caster,center))return false;
                if(age%3==1)ring(center,radius,Particle.FLAME,.15);
                if(age%5==1){Set<UUID> now=new HashSet<>();
                    for(LivingEntity target:inside(caster,center,radius)){now.add(target.getUniqueId());if(hit.getOrDefault(target.getUniqueId(),-20)<=age-20){damage(caster,target,value(id,1,"damage",2));hit.put(target.getUniqueId(),age);}}
                    for(UUID left:inside)if(!now.contains(left))burnAfterLeaving(caster,left);   // left the ring: keep burning 3 seconds
                    inside.clear();inside.addAll(now);}
                return true;}
                public void close(){Player caster=caster();if(caster!=null)for(UUID left:inside)burnAfterLeaving(caster,left);}});
            sound(p.getLocation(),Sound.ITEM_FIRECHARGE_USE,.8f,.8f);return true;
        }
        // Fireball: a real Minecraft fireball (like a Ghast's), launched at once from just in front of the eyes.
        Location eye=p.getEyeLocation();Vector direction=eye.getDirection().normalize();Location spawn=eye.clone().add(direction.clone().multiply(1.2));
        if(!protection.canAffect(p,spawn) || !spawn.getBlock().isPassable())return false;
        LargeFireball fireball=p.getWorld().spawn(spawn,LargeFireball.class,f->{f.setShooter(p);f.setDirection(direction);f.setVelocity(direction.clone().multiply(bounded(value(id,2,"speed",1.2),.2,4)));
            f.setYield((float)bounded(value(id,2,"power",1),0,4));f.setIsIncendiary(magic().getBoolean("fireball-fire",false));
            f.getPersistentDataContainer().set(FIREBALL,PersistentDataType.STRING,p.getUniqueId().toString());});
        sound(eye,Sound.ENTITY_GHAST_SHOOT,.8f,1f);return fireball.isValid();
    }
    /** The lowest air block resting on solid ground in this column (3 blocks up to 3 down), or null. */
    private Block surface(Block column){
        for(int dy=2;dy>=-3;dy--){Block b=column.getRelative(0,dy,0),below=b.getRelative(BlockFace.DOWN);
            if(b.getType().isAir() && below.isSolid() && below.getType().isOccluding())return b;}
        return null;
    }
    /** An enemy who walks out of the Fire Ring keeps burning for 3 seconds (never shortening a longer fire). */
    private void burnAfterLeaving(Player caster,UUID id){if(Bukkit.getEntity(id) instanceof LivingEntity target && target.isValid() && enemy(caster,target) && target.getFireTicks()<60)target.setFireTicks(60);}
    /** Living enemies whose feet are inside a horizontal circle (the same circle the particles draw). */
    private List<LivingEntity> inside(Player caster,Location center,double radius){
        return center.getWorld().getNearbyLivingEntities(center,radius+.5,3,radius+.5,t->{double dx=t.getX()-center.getX(),dz=t.getZ()-center.getZ();return dx*dx+dz*dz<=radius*radius && Math.abs(t.getY()-center.getY())<=2.5 && enemy(caster,t);}).stream().limit(32).toList();
    }
    /** A continuous circle drawn exactly on the effect radius: about 3 particles per block of circumference. */
    private void ring(Location center,double radius,Particle particle,double height){
        double intensity=bounded(magic().getDouble("particle-intensity",1),0,2);if(intensity==0)return;
        int n=(int)Math.max(16,Math.min(96,Math.PI*2*radius*3*intensity));
        for(int i=0;i<n;i++){double a=Math.PI*2*i/n;center.getWorld().spawnParticle(particle,center.getX()+Math.cos(a)*radius,center.getY()+height,center.getZ()+Math.sin(a)*radius,1,0,0,0,0);}
    }
    private static final NamespacedKey FIREBALL=new NamespacedKey("blastforge","magic_fireball"),BREATH=new NamespacedKey("blastforge","darkness_breath");
    /**
     * Darkness Breath impact, done here so it always happens exactly once and exactly like the Ender Dragon's:
     * the hit is taken over from Paper, the ball is removed and one Dragon's Breath cloud is created
     * (radius 3 growing toward 7, 30 seconds, Harming, nearest entity within 4 blocks), with the vanilla impact sound.
     */
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void breathHit(ProjectileHitEvent e){
        if(!(e.getEntity() instanceof DragonFireball ball))return;String owner=ball.getPersistentDataContainer().get(BREATH,PersistentDataType.STRING);if(owner==null)return;
        e.setCancelled(true);   // our impact below replaces the vanilla one, so there can never be two clouds
        Player caster=Bukkit.getPlayer(UUID.fromString(owner));
        if(caster!=null && caster.equals(e.getHitEntity()))return;   // passes through its own caster
        Location at=ball.getLocation();ball.remove();
        if(caster==null || !protection.canAffect(caster,at))return;
        // like vanilla: if creatures are close to the impact, the cloud is centred on the nearest one
        LivingEntity nearest=at.getWorld().getNearbyLivingEntities(at,4,2,4,t->!t.equals(caster)).stream().min(Comparator.comparingDouble(t->t.getLocation().distanceSquared(at))).orElse(null);
        Location center=nearest!=null?nearest.getLocation():at;
        AreaEffectCloud cloud=at.getWorld().spawn(center,AreaEffectCloud.class,c->{c.setSource(caster);if(Particle.DRAGON_BREATH.getDataType()==Float.class)c.setParticle(Particle.DRAGON_BREATH,1f);else c.setParticle(Particle.DRAGON_BREATH);c.setRadius(3);c.setDuration(600);
            c.setRadiusPerTick((7f-3f)/600f);c.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE,1,1),true);
            c.getPersistentDataContainer().set(BREATH,PersistentDataType.STRING,caster.getUniqueId().toString());});
        at.getWorld().playEffect(at,org.bukkit.Effect.DRAGON_BREATH,0);
        if(!cloud.isValid())plugin.getLogger().warning("Darkness Breath cloud was blocked by another plugin at "+at.getBlockX()+" "+at.getBlockY()+" "+at.getBlockZ());
    }
    /** Mobs that Harming heals instead of hurting. */
    static boolean undead(LivingEntity t){return dev.duckmc.blastforge.Compat.undead(t);}
    private final Map<UUID,Map<UUID,Integer>> breathHits=new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Who the breath cloud may hurt: never its caster, players only with PvP, nothing inside protected regions.
     * Harming heals undead (zombies, skeletons, drowned...), so those take the same 6 damage directly instead,
     * once per second like everyone else.
     */
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void breathCloud(AreaEffectCloudApplyEvent e){
        String owner=e.getEntity().getPersistentDataContainer().get(BREATH,PersistentDataType.STRING);if(owner==null)return;
        Player caster=Bukkit.getPlayer(UUID.fromString(owner));if(caster==null){e.getAffectedEntities().clear();return;}
        e.getAffectedEntities().removeIf(target->target.equals(caster) || target instanceof Player && (!magic().getBoolean("pvp",true) || !caster.getWorld().getPVP()) || !protection.canDamage(caster,target) || !protection.canAffect(caster,target.getLocation()));
        var hits=breathHits.computeIfAbsent(e.getEntity().getUniqueId(),k->new java.util.concurrent.ConcurrentHashMap<>());int now=dev.duckmc.blastforge.Tasks.tick();
        for(var it=e.getAffectedEntities().iterator();it.hasNext();){LivingEntity target=it.next();
            if(!undead(target))continue;it.remove();
            if(hits.getOrDefault(target.getUniqueId(),-20)>now-20)continue;hits.put(target.getUniqueId(),now);
            internalDamage.enter();try{target.damage(6,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(caster).withDirectEntity(e.getEntity()).build());}finally{internalDamage.exit();}
        }
        if(breathHits.size()>64)breathHits.keySet().removeIf(id->Bukkit.getEntity(id)==null);
    }
    @FunctionalInterface private interface Impact {void hit(Player caster,Location at,LivingEntity target);}
    /** Simulated projectiles have no native explosion, ignition, loot or cloud side effects to leak past protection. */
    private boolean projectile(Player p,Particle particle,double range,double speed,double width,Impact impact){
        Location start=p.getEyeLocation().clone();Vector direction=start.getDirection().normalize();if(!protection.canAffect(p,start))return false;
        addEffect(new OwnedEffect(p,(int)Math.ceil(range/speed)+2){Location at=start;double travelled;
            boolean step(Player caster){double distance=Math.min(speed,range-travelled);if(distance<=0)return false;
                Location next=at.clone().add(direction.clone().multiply(distance));if(!protection.canAffect(caster,next))return false;
                RayTraceResult hit=at.getWorld().rayTrace(at,direction,distance,FluidCollisionMode.NEVER,true,width,e->e instanceof LivingEntity living && !e.equals(caster) && living.isValid());
                Location end=hit==null?next:hit.getHitPosition().toLocation(at.getWorld());trail(at,end,particle,6);
                if(hit!=null){if(protection.canAffect(caster,end))impact.hit(caster,end,hit.getHitEntity() instanceof LivingEntity l?l:null);return false;}
                at=next;travelled+=distance;return travelled<range;
            }});return true;
    }
    private void blastBlocks(Player caster,Location center){
        int changed=0;for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            if(changed>=8)return;Block b=center.getBlock().getRelative(x,y,z);if(!Set.of(Material.STONE,Material.DIRT,Material.NETHERRACK,Material.END_STONE).contains(b.getType()) || !protection.canAffect(caster,b.getLocation()))continue;
            // The optional blast is another journaled no-drop edit, preserving all temporary-world ownership rules.
            if(terrain.change(caster,List.of(b),Material.AIR,10))changed++;
        }
    }
    private boolean water(Player p,int ability){String id="water_book";
        if(ability==0){BoundingBox box=p.getBoundingBox().clone().shift(0,.6,0);if(p.wouldCollideUsing(box))return false;
            Location start=p.getLocation().clone();
            p.setVelocity(new Vector(p.getVelocity().getX(),bounded(value(id,0,"launch-velocity",1.1),.2,2),p.getVelocity().getZ()));
            // a burst where the jump starts, then a stream of bubbles that follows the player up until they stop rising
            bubbles(start,24,.6);sound(start,Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE,1f,1f);sound(start,Sound.ENTITY_PLAYER_SPLASH,.6f,1.2f);
            addEffect(new OwnedEffect(p,30){double lastY=start.getY();boolean step(Player caster){Location at=caster.getLocation();
                trail(at.clone().add(0,-.6,0),at.clone().add(0,.4,0),Particle.BUBBLE_POP,3);bubbles(at.clone().add(0,.2,0),3,.25);
                boolean rising=at.getY()>lastY+.02;lastY=at.getY();return rising || age<3;}});return true;}
        if(ability==1)return summons.summon(p,false,value(id,1,"duration",60),(int)value(id,1,"count",3));
        // Water Strike: pull one block of water from a source within 5 blocks. While Shift is held the water hovers
        // in front of the caster and follows their aim; releasing Shift hurls it where they look.
        Block source=waterSource(p);
        if(source==null){p.sendActionBar(Component.text("No water within 5 blocks.",NamedTextColor.AQUA));return false;}
        Location from=source.getLocation().add(.5,.5,.5);if(!protection.canAffect(p,from) || heldWater.containsKey(p.getUniqueId()))return false;
        ItemDisplay orb=from.getWorld().spawn(from,ItemDisplay.class,d->{ItemStack look=new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS);look.editMeta(m->dev.duckmc.blastforge.Compat.itemModel(m,new NamespacedKey("blastforge","water_orb")));
            d.setItemStack(look);d.setPersistent(false);d.setTeleportDuration(2);d.setInterpolationDuration(2);
            d.setTransformationMatrix(new org.joml.Matrix4f().scale(.8f));d.getPersistentDataContainer().set(ExpansionItems.key("magic_visual"),PersistentDataType.STRING,p.getUniqueId().toString());});
        particles(from,Particle.SPLASH,20,.4);sound(from,Sound.ITEM_BUCKET_FILL,1f,1f);
        if(p.isSneaking())p.sendActionBar(Component.text("Release Shift to throw the water.",NamedTextColor.AQUA));
        double range=24,speed=.9;int maxHold=200;
        boolean[] released={false};heldWater.put(p.getUniqueId(),()->released[0]=true);
        addEffect(new OwnedEffect(p,maxHold+12+(int)Math.ceil(range/speed)){Location at=from.clone();Vector dir;double travelled;int launchedAt=-1;
            boolean step(Player caster){if(!orb.isValid())return false;
                if(launchedAt<0){
                    // held: rise to (and stay) in front of the caster, following their aim
                    Location hold=caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.6));at=at.clone().add(hold.toVector().subtract(at.toVector()).multiply(.35));
                    move(at);if(age%3==0)particles(at,Particle.DRIPPING_WATER,2,.3);
                    boolean ready=age>=6;   // it has left the water and reached the caster
                    if(ready && (released[0] || !caster.isSneaking() || age>=maxHold)){
                        launchedAt=age;heldWater.remove(caster.getUniqueId());dir=caster.getEyeLocation().getDirection().normalize();at=hold;
                        sound(at,Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED,1f,1.2f);}
                    return true;}
                // released: flies straight on its own and no longer follows the caster
                double step=Math.min(speed,range-travelled);if(step<=0 || !protection.canAffect(caster,at))return false;
                RayTraceResult hit=at.getWorld().rayTrace(at,dir,step,FluidCollisionMode.NEVER,true,.5,e->e instanceof LivingEntity l && !e.equals(caster) && l.isValid());
                Location end=hit==null?at.clone().add(dir.clone().multiply(step)):hit.getHitPosition().toLocation(at.getWorld());
                trail(at,end,Particle.SPLASH,5);particles(end,Particle.FALLING_WATER,3,.25);move(end);
                if(hit!=null){particles(end,Particle.SPLASH,40,.6);sound(end,Sound.ENTITY_GENERIC_SPLASH,1f,1f);
                    if(hit.getHitEntity() instanceof LivingEntity target && enemy(caster,target))drown(caster,target);return false;}
                at=end;travelled+=step;return travelled<range;}
            void move(Location to){Location l=to.clone();l.setYaw(age*18f);orb.teleport(l);}
            public void close(){orb.remove();heldWater.remove(owner);}});
        return true;
    }
    /** Players currently holding Water Strike water -> release action. */
    private final Map<UUID,Runnable> heldWater=new java.util.concurrent.ConcurrentHashMap<>();
    @EventHandler(priority=EventPriority.MONITOR) public void releaseWater(PlayerToggleSneakEvent e){if(!e.isSneaking()){Runnable release=heldWater.remove(e.getPlayer().getUniqueId());if(release!=null)release.run();}}
    /** The Drowning effect: the target loses air and takes the configured damage every second for the duration. */
    private void drown(Player p,LivingEntity enemy){String id="water_book";UUID targetId=enemy.getUniqueId();
        addEffect(new OwnedEffect(p,duration(id,2,5)){boolean step(Player caster){if(!(Bukkit.getEntity(targetId) instanceof LivingEntity target) || !enemy(caster,target))return false;
            if(age%5==0)particles(target.getEyeLocation(),Particle.BUBBLE_POP,6,.3);target.setRemainingAir(Math.max(-20,target.getRemainingAir()-15));
            if((age-1)%20==0)damage(caster,target,value(id,2,"damage",2));return true;}});
    }
    /** Bubbles that are visible in air too (BUBBLE itself only shows underwater). */
    private void bubbles(Location at,int count,double spread){if(at.getBlock().isLiquid())particles(at,Particle.BUBBLE,count,spread);particles(at,Particle.BUBBLE_POP,count,spread);particles(at,Particle.SPLASH,count/2,spread);}
    /** The nearest water source block within 5 blocks. The water is only read: never moved, removed or copied. */
    private Block waterSource(Player p){Location center=p.getLocation();Block best=null;double bestD=Double.MAX_VALUE;
        for(int x=-5;x<=5;x++)for(int y=-5;y<=5;y++)for(int z=-5;z<=5;z++){
            Block b=center.getBlock().getRelative(x,y,z);double d=b.getLocation().add(.5,.5,.5).distanceSquared(center);if(d>25 || d>=bestD)continue;
            if(!b.getWorld().isChunkLoaded(b.getX()>>4,b.getZ()>>4))continue;
            boolean water=b.getType()==Material.WATER && b.getBlockData() instanceof Levelled level && level.getLevel()==0 || b.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged();
            if(water && protection.canAffect(p,b.getLocation())){best=b;bestD=d;}
        }return best;}
    private boolean earth(Player p,int ability){String id="earth_book";
        if(ability==0){RayTraceResult hit=p.rayTraceBlocks(8,FluidCollisionMode.NEVER);if(hit==null || hit.getHitBlock()==null || hit.getHitBlockFace()==null)return false;
            // A square (3x3 by default) on the face the player is looking at; it rebuilds itself after "duration" seconds.
            Block center=hit.getHitBlock();BlockFace face=hit.getHitBlockFace();int half=((int)bounded(value(id,0,"size",3),1,5))/2;List<Block> blocks=new ArrayList<>();
            for(int a=-half;a<=half;a++)for(int b=-half;b<=half;b++)blocks.add(switch(face){case UP,DOWN->center.getRelative(a,0,b);case NORTH,SOUTH->center.getRelative(a,b,0);default->center.getRelative(0,a,b);});
            blocks.sort(Comparator.comparingDouble(x->-x.getY()));
            boolean changed=terrain.change(p,blocks,Material.AIR,value(id,0,"duration",10));if(changed){particles(center.getLocation().add(.5,.5,.5),Particle.CLOUD,16,.7);sound(center.getLocation(),Sound.BLOCK_STONE_BREAK,.8f,.7f);}return changed;
        }
        if(ability!=1)return false;Vector forward=p.getLocation().getDirection().setY(0);if(forward.lengthSquared()<.001)forward=new Vector(0,0,1);forward.normalize();
        // Stone Wall: 9 wide x 7 tall, 1 block thick, in front of the caster and turned to where they look.
        // Works in every dimension: solid blocks already in the way are kept, soft blocks (grass, snow) are filled.
        boolean normalX=Math.abs(forward.getX())>Math.abs(forward.getZ());Block center=p.getLocation().clone().add(forward.clone().multiply(2.5)).getBlock();
        int width=(int)bounded(value(id,1,"wall-width",9),1,9),height=(int)bounded(value(id,1,"wall-height",7),1,9);
        for(int i=0;i<3 && MagicTerrain.soft(center.getRelative(BlockFace.DOWN));i++)center=center.getRelative(BlockFace.DOWN);   // stand on the ground, even on slopes
        List<Block> blocks=new ArrayList<>();int from=-(width/2),to=from+width-1;
        for(int w=from;w<=to;w++)for(int y=0;y<height;y++)blocks.add(normalX?center.getRelative(0,y,w):center.getRelative(w,y,0));
        Material stone=switch(p.getWorld().getEnvironment()){case NETHER->Material.BLACKSTONE;case THE_END->Material.END_STONE_BRICKS;default->Material.STONE;};
        Material chosen=Material.matchMaterial(magic().getString("books.earth.secondary.block",stone.name()));
        if(chosen==null || !chosen.isBlock() || !chosen.isSolid())chosen=stone;
        boolean changed=terrain.change(p,blocks,chosen,value(id,1,"duration",5),true);
        if(changed)sound(center.getLocation(),Sound.BLOCK_STONE_PLACE,1f,.6f);
        return changed;
    }
    private boolean wind(Player p,int ability){String id="wind_book";
        if(ability==0)return flights.grant(p,value(id,0,"duration",20));
        if(ability==2){
            // A real Wind Charge, like a Breeze's, fired where the player is looking.
            Location eye=p.getEyeLocation();if(!protection.canAffect(p,eye))return false;
            p.launchProjectile(WindCharge.class,eye.getDirection().normalize().multiply(bounded(value(id,2,"speed",1.5),.2,4)));
            sound(eye,Sound.ENTITY_WIND_CHARGE_THROW,.8f,1f);return true;
        }
        // Suffocation: a ring of gusts around the caster; only enemies inside it lose air and take damage.
        double radius=bounded(value(id,1,"radius",2),.5,6);int life=duration(id,1,5);double amount=value(id,1,"damage",2);Map<UUID,Integer> hits=new HashMap<>();
        addEffect(new OwnedEffect(p,life){boolean step(Player caster){Location center=caster.getLocation();if(!protection.canAffect(caster,center))return false;
            if(age%4==1){ring(center,radius,Particle.SMALL_GUST,.15);ring(center,radius,Particle.CLOUD,1.1);}
            if(age%5==1)for(LivingEntity target:inside(caster,center,radius)){
                target.setRemainingAir(Math.max(-20,target.getRemainingAir()-30));
                if(hits.getOrDefault(target.getUniqueId(),-20)<=age-20){damage(caster,target,amount);hits.put(target.getUniqueId(),age);particles(target.getEyeLocation(),Particle.SMALL_GUST,2,.2);}
            }return true;}});
        sound(p.getLocation(),Sound.ENTITY_BREEZE_WIND_BURST,.8f,.8f);return true;
    }
    private boolean darkness(Player p,int ability){String id="darkness_book";
        if(ability==0){List<LivingEntity> targets=enemies(p,p.getLocation(),bounded(value(id,0,"radius",3),.5,8));boolean applied=false;int duration=duration(id,0,30);
            for(LivingEntity target:targets){PotionEffect old=target.getPotionEffect(PotionEffectType.BLINDNESS);if(old!=null && old.getDuration()>=duration)continue;applied|=target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,duration,0,false,true,true));particles(target.getEyeLocation(),Particle.SOUL_FIRE_FLAME,10,.4);}return applied;}
        if(ability==1)return summons.summon(p,true,value(id,1,"duration",10),(int)value(id,1,"count",3));
        // Darkness Breath: a genuine Ender Dragon fireball, spawned just in front of the eyes with its shooter,
        // direction and speed set before it enters the world (no teleport after spawning).
        Location eye=p.getEyeLocation();Vector direction=eye.getDirection().normalize();Location spawn=eye.clone().add(direction.clone().multiply(1.5));
        if(!protection.canAffect(p,spawn) || !spawn.getBlock().isPassable())return false;
        DragonFireball ball=p.getWorld().spawn(spawn,DragonFireball.class,f->{f.setShooter(p);f.setDirection(direction);f.setVelocity(direction.clone().multiply(bounded(value(id,2,"speed",1),.2,3)));
            f.getPersistentDataContainer().set(BREATH,PersistentDataType.STRING,p.getUniqueId().toString());});
        sound(eye,Sound.ENTITY_ENDER_DRAGON_SHOOT,1f,1f);return ball.isValid();
    }
    private boolean scythe(Player p,int ability){String id="legendary_scythe";
        if(ability==1)return flights.grant(p,value(id,1,"duration",30));
        if(ability==2)return windmill(p,duration(id,2,5),bounded(value(id,2,"radius",3),1,5),value(id,2,"damage",4),(int)(bounded(value(id,2,"hit-interval",1),1,10)*20));
        if(p.isInsideVehicle() || p.isGliding())return false;Vector direction=p.getLocation().getDirection().normalize();double distance=bounded(value(id,0,"distance",5),1,5);
        // Validate a swept path before activation; an adjacent wall is a failed cast, not a charged cooldown.
        Location start=p.getLocation(),first=start.clone().add(direction.clone().multiply(.25));if(!dashSafe(p,first))return false;
        Set<UUID> hit=new HashSet<>();addEffect(new OwnedEffect(p,8){double travelled;
            boolean step(Player caster){String held=activeItem(caster);if(!held.equals(id))return false;Location from=caster.getLocation(),to=from.clone();double step=Math.min(1,distance-travelled);if(step<=0)return false;
                for(double offset=.25;offset<=step+.001;offset+=.25){Location candidate=from.clone().add(direction.clone().multiply(Math.min(offset,step)));if(!dashSafe(caster,candidate))break;to=candidate;}
                if(to.distanceSquared(from)<.001)return false;if(!caster.teleport(to,PlayerTeleportEvent.TeleportCause.PLUGIN))return false;
                travelled+=from.distance(to);trail(from.clone().add(0,1,0),to.clone().add(0,1,0),Particle.SOUL_FIRE_FLAME,12);
                BoundingBox swept=BoundingBox.of(from,to).expand(.8,1.8,.8);for(Entity e:caster.getWorld().getNearbyEntities(swept))if(e instanceof LivingEntity target && !hit.contains(e.getUniqueId()) && enemy(caster,target)){damage(caster,target,value(id,0,"damage",10));hit.add(e.getUniqueId());}
                return travelled<distance-.01;
            }});return true;
    }
    private boolean dashSafe(Player p,Location location){if(!protection.canAffect(p,location))return false;Vector delta=location.toVector().subtract(p.getLocation().toVector());BoundingBox box=p.getBoundingBox().clone().shift(delta);
        for(double x:new double[]{box.getMinX(),box.getMaxX()})for(double z:new double[]{box.getMinZ(),box.getMaxZ()})if(!location.getWorld().isChunkLoaded(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4) || !protection.canAffect(p,new Location(location.getWorld(),x,location.getY(),z)))return false;
        return !p.wouldCollideUsing(box);
    }
    /** Windmill orbit speed: 1.5x the original 0.35 rad per tick (about 20.1 degrees) = 0.525 rad (about 30.1 degrees) per tick. */
    static final double WINDMILL_ORBIT=0.35*1.5;
    /**
     * Windmill: the scythe circles the caster horizontally at the original radius and height (1 block up), standing
     * upright with its handle vertical and its blade facing the way it travels. It no longer tumbles. Damage follows the
     * weapon: an enemy is hit when the scythe sweeps past them, at most once per hit-interval.
     */
    private boolean windmill(Player p,int life,double radius,double amount,int interval){
        Map<UUID,Integer> hits=new HashMap<>();ItemDisplay model=null;
        if(magic().getDouble("particle-intensity",1)>0)model=p.getWorld().spawn(p.getLocation().add(radius,1,0),ItemDisplay.class,d->{d.setItemStack(p.getInventory().getItemInMainHand().clone());d.setPersistent(false);
            d.setTeleportDuration(1);d.setViewRange(.5f);
            // the scythe is drawn diagonally in its icon; turning it 45 degrees stands the handle upright. Set once: no tumbling.
            d.setTransformationMatrix(new org.joml.Matrix4f().rotateZ((float)(Math.PI/4)).scale(1.5f));
            d.getPersistentDataContainer().set(ExpansionItems.key("magic_visual"),PersistentDataType.STRING,p.getUniqueId().toString());});
        ItemDisplay display=model;
        addEffect(new OwnedEffect(p,life){double angle;
            boolean step(Player caster){if(!activeItem(caster).equals("legendary_scythe"))return false;Location center=caster.getLocation();if(!protection.canAffect(caster,center))return false;
                double from=angle;angle+=WINDMILL_ORBIT;
                Location blade=center.clone().add(Math.cos(angle)*radius,1,Math.sin(angle)*radius);
                // face along the orbit (tangent), so the flat side of the upright scythe cuts forward
                blade.setYaw((float)Math.toDegrees(angle));blade.setPitch(0);
                if(display!=null && display.isValid())display.teleport(blade);
                if(age%2==0)circle(center.clone().add(0,1,0),radius,Particle.SOUL_FIRE_FLAME,24);
                // hit what the scythe passed this tick: points on the arc between the old and new angle, 1.6 blocks tall
                Set<LivingEntity> touched=new HashSet<>();var nearby=caster.getWorld().getNearbyLivingEntities(center,radius+1.5,3,radius+1.5,t->enemy(caster,t));
                for(int s=0;s<=3 && !nearby.isEmpty();s++){double a=from+(angle-from)*s/3;
                    for(double h=.2;h<=1.8;h+=.4){Vector point=center.toVector().add(new Vector(Math.cos(a)*radius,h,Math.sin(a)*radius));
                        for(LivingEntity t:nearby)if(t.getBoundingBox().clone().expand(.6,.2,.6).contains(point))touched.add(t);}}
                for(LivingEntity target:touched)if(hits.getOrDefault(target.getUniqueId(),-interval)<=age-interval){damage(caster,target,amount);hits.put(target.getUniqueId(),age);}
                return true;}
            public void close(){if(display!=null)display.remove();}});return true;
    }
    private void particles(Location at,Particle particle,int count,double spread){double intensity=bounded(magic().getDouble("particle-intensity",1),0,2);if(intensity==0)return;at.getWorld().spawnParticle(particle,at,(int)Math.min(64,Math.ceil(count*intensity)),spread,spread,spread,.01,particle.getDataType()==Float.class?Float.valueOf(1):null);}
    private void circle(Location center,double radius,Particle particle,int points){double intensity=bounded(magic().getDouble("particle-intensity",1),0,2);int n=(int)Math.min(48,points*intensity);for(int i=0;i<n;i++){double angle=Math.PI*2*i/n;particles(center.clone().add(Math.cos(angle)*radius,.2,Math.sin(angle)*radius),particle,1,0);}}
    private void trail(Location from,Location to,Particle particle,int points){Vector line=to.toVector().subtract(from.toVector());for(int i=0;i<Math.min(24,points);i++)particles(from.clone().add(line.clone().multiply((double)i/points)),particle,1,.03);}
    private void sound(Location at,Sound sound,float volume,float pitch){at.getWorld().playSound(at,sound,volume,pitch);}
    private static final Map<String,String[]> ABILITY_NAMES=Map.of(
        "fire_book",new String[]{"Fire Field","Fire Ring","Fireball"},"water_book",new String[]{"Bubble Launch","Summon Drowned","Water Strike"},
        "earth_book",new String[]{"Excavation","Stone Wall",null},"wind_book",new String[]{"Flight","Suffocation","Wind Charge"},
        "darkness_book",new String[]{"Blindness","Wither Skeletons","Darkness Breath"},"legendary_scythe",new String[]{"Dash","Flight","Windmill"});
    /** Cooldown bar above the health and hunger bars while a book or the scythe is held: "1. Fireball 12s | 2. Fire Ring Ready | ...". */
    private void hud(Player p,long now){
        String id=activeItem(p);String[] names=ABILITY_NAMES.get(id);if(names==null||!magic().getBoolean("cooldown-display",true))return;
        var line=Component.text();boolean first=true;
        for(int ability=0;ability<3;ability++){if(names[ability]==null)continue;
            if(!first)line.append(Component.text("  |  ",NamedTextColor.DARK_GRAY));first=false;
            long left=cooldowns.remaining(p.getUniqueId(),id,ability,now);
            line.append(Component.text((ability+1)+". "+names[ability]+" ",NamedTextColor.WHITE));
            line.append(left>0?Component.text(time(left),NamedTextColor.RED):Component.text("Ready",NamedTextColor.GREEN));
        }
        p.sendActionBar(line.build());
    }
    private static String time(long millis){long seconds=(millis+999)/1000;return seconds>=60?(seconds/60)+"m "+(seconds%60)+"s":seconds+"s";}
    /** World-independent upkeep: temporary-block restores (each handed to its own region), summons, bookkeeping. */
    private void globalPump(){ticks++;long now=System.currentTimeMillis();
        terrain.pump(now,false);if(ticks%10==0)summons.tick(now);
        if(ticks%20==0){dispatched.clear();feedback.entrySet().removeIf(e->e.getValue()<now-60_000);}
    }
    /** One player's tick, on that player's thread. */
    private void playerPump(Player p){long now=System.currentTimeMillis();int tick=dev.duckmc.blastforge.Tasks.tick();
        var list=effects.get(p.getUniqueId());
        if(list!=null)for(Effect e:list){try{if(!e.tick()){e.close();list.remove(e);}}catch(RuntimeException failure){e.close();list.remove(e);plugin.getLogger().warning("Magic effect stopped safely: "+failure.getMessage());}}
        if(list!=null&&list.isEmpty())effects.remove(p.getUniqueId(),list);
        flights.hold(p);
        if(tick%10==0){flights.tick(p,now);hud(p,now);}
        if(tick%20==0)passives.tick(p);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void passiveDamage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player p))return;int books=passives.scan(p);boolean cancel=switch(e.getCause()){
        case FIRE,FIRE_TICK,LAVA,HOT_FLOOR->(books&MagicPassives.FIRE)!=0;case DROWNING->(books&MagicPassives.WATER)!=0;case FALL->(books&MagicPassives.WIND)!=0 || flights.safeFall(p);default->false;};if(cancel)e.setCancelled(true);
    }
    private static final Map<Material,Material> COOKED=Map.of(Material.BEEF,Material.COOKED_BEEF,Material.PORKCHOP,Material.COOKED_PORKCHOP,Material.CHICKEN,Material.COOKED_CHICKEN,Material.MUTTON,Material.COOKED_MUTTON,Material.RABBIT,Material.COOKED_RABBIT,Material.COD,Material.COOKED_COD,Material.SALMON,Material.COOKED_SALMON);
    @EventHandler(priority=EventPriority.HIGHEST) public void cookedDrops(EntityDeathEvent e){if(summons.marked(e.getEntity()) || !(e.getEntity() instanceof Animals || e.getEntity() instanceof Fish))return;Player killer=e.getEntity().getKiller();if(killer==null && e.getDamageSource().getCausingEntity() instanceof Player p)killer=p;
        if(killer==null || (passives.scan(killer)&MagicPassives.FIRE)==0)return;
        for(ItemStack drop:e.getDrops()){Material cooked=COOKED.get(drop.getType());if(cooked!=null && items.id(drop).isEmpty())drop.setType(cooked);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void potion(EntityPotionEffectEvent e){passives.changed(e);}
    @EventHandler public void inventory(PlayerInventorySlotChangeEvent e){passives.dirty(e.getPlayer());}
    @EventHandler public void held(PlayerItemHeldEvent e){passives.dirty(e.getPlayer());}
    @EventHandler public void swap(PlayerSwapHandItemsEvent e){passives.dirty(e.getPlayer());}
    @EventHandler public void join(PlayerJoinEvent e){flights.reconcile(e.getPlayer());passives.scan(e.getPlayer());startPlayer(e.getPlayer());}
    private void cancel(Player p){var list=effects.remove(p.getUniqueId());if(list!=null)for(Effect effect:list)try{effect.close();}catch(RuntimeException ignored){}summons.removeOwner(p.getUniqueId());flights.restore(p);passives.remove(p);dispatched.remove(p.getUniqueId());feedback.remove(p.getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){cancel(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){cancel(e.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent e){cancel(e.getPlayer());}
    @EventHandler public void mode(PlayerGameModeChangeEvent e){if(!e.isCancelled())dev.duckmc.blastforge.Tasks.entity(plugin,e.getPlayer(),()->flights.restore(e.getPlayer()));}
    @EventHandler public void unload(WorldUnloadEvent e){for(Player p:e.getWorld().getPlayers())cancel(p);terrain.pump(Long.MAX_VALUE,true);}
    /** Region/edit plugins can call this before direct block changes that do not emit Bukkit events. */
    public void invalidateTemporaryBlock(Block block){terrain.invalidate(block);}
    @Override public void close(){if(closed)return;closed=true;if(task!=null){task.cancel();task=null;}for(var handle:playerTasks.values())handle.cancel();playerTasks.clear();for(var list:effects.values())for(Effect effect:list)try{effect.close();}catch(RuntimeException ignored){}effects.clear();summons.close();flights.close();for(Player p:Bukkit.getOnlinePlayers())passives.remove(p);terrain.close();HandlerList.unregisterAll(this);}
}
