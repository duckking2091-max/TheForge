package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.Function;

/** Reconstructed cosmetic parts; chunk station markers remain the authority. */
public final class StationVisuals implements Listener,AutoCloseable {
    private static final NamespacedKey OWNER=new NamespacedKey("blastforge","visual_owner");
    private final JavaPlugin plugin;private final ExpansionItems items;private final Function<String,FileConfiguration> configs;
    private final Map<String,Parts> stations=new LinkedHashMap<>();private final LinkedHashSet<String> pending=new LinkedHashSet<>();
    private final Map<String,Integer> strikes=new HashMap<>();
    /* Rune hearth animation per station: [tick, duration]; duration 0 = completion flash. */
    private final Map<String,int[]> heat=new HashMap<>();
    private static final int IDLE_LIGHT=6,FLASH_TICKS=8;private PackVisuals pack;private BukkitTask task;private boolean closed;private long generation;
    private record Parts(Location location,float yaw,List<ItemDisplay> entities){}
    public StationVisuals(JavaPlugin plugin,ExpansionItems items,Function<String,FileConfiguration> configs){this.plugin=plugin;this.items=items;this.configs=configs;}
    public void pack(PackVisuals value){pack=value;if(value!=null)value.onStatusChange(this::visibility);}
    public void start(){if(task!=null)return;closed=false;generation++;plugin.getServer().getPluginManager().registerEvents(this,plugin);for(World world:plugin.getServer().getWorlds())for(Chunk chunk:world.getLoadedChunks())enqueue(chunk);task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);}
    private static String key(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    static boolean marked(Block block){return Tag.ANVIL.isTagged(block.getType())&&block.getChunk().getPersistentDataContainer().has(new NamespacedKey("blastforge","station_"+(block.getX()&15)+"_"+block.getY()+"_"+(block.getZ()&15)),PersistentDataType.BYTE);}
    public void placed(Block block){
        if(closed||!configs.apply("config").getBoolean("visuals.enabled",true)||!marked(block))return;String key=key(block);if(stations.containsKey(key))return;
        if(stations.size()*2+2>Math.clamp(configs.apply("config").getInt("visuals.max-station-displays",128),0,4096)){pending.add(key);return;}
        float yaw=block.getBlockData() instanceof Directional d?switch(d.getFacing()){case EAST->90;case SOUTH->180;case WEST->270;default->0;}:0;
        Location center=block.getLocation().add(.5,0,.5);var parts=new ArrayList<ItemDisplay>();
        try{for(String id:List.of("enchanting_anvil","anvil_hammer")){ /* Rune Hearth Anvil (with its hammer and tongs) + the glowing hearth disc (anvil_hammer model slot) */
            ItemDisplay display=block.getWorld().spawn(center,ItemDisplay.class,e->{e.setPersistent(false);e.setGravity(false);e.setVisibleByDefault(false);e.setInvulnerable(true);e.setItemStack(items.create(id));e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setBillboard(Display.Billboard.FIXED);e.setShadowRadius(0);e.setViewRange(.75f);e.setDisplayWidth(2);e.setDisplayHeight(2);e.setInterpolationDuration(3);e.setTeleportDuration(2);e.getPersistentDataContainer().set(OWNER,PersistentDataType.STRING,"station:"+key);if(id.equals("anvil_hammer"))e.setBrightness(new Display.Brightness(IDLE_LIGHT,IDLE_LIGHT));});parts.add(display);
        }}catch(RuntimeException error){parts.forEach(Entity::remove);throw error;}
        Parts station=new Parts(center,yaw,parts);stations.put(key,station);pose(station,0);for(Player player:block.getWorld().getPlayers())show(player,station);pending.remove(key);
    }
    public void removed(Block block){remove(key(block));}
    private void remove(String key){Parts parts=stations.remove(key);if(parts!=null)parts.entities().forEach(Entity::remove);strikes.remove(key);heat.remove(key);pending.remove(key);}
    /** ticks>0: forging started and lasts that long; 0: finished (flash); negative: cancelled (back to idle). */
    public void heat(Location location,int ticks){
        if(closed||location==null||location.getWorld()==null)return;String key=key(location.getBlock());Parts part=stations.get(key);
        if(ticks<0){heat.remove(key);if(part!=null)glow(part,IDLE_LIGHT,1f);return;}
        heat.put(key,new int[]{0,ticks});
        if(ticks==0){Location top=location.getBlock().getLocation().add(.5,1.15,.5);var world=top.getWorld();
            world.spawnParticle(Particle.LAVA,top,10,.2,.05,.2,0);world.spawnParticle(Particle.FLAME,top,24,.18,.1,.18,.06);world.spawnParticle(Particle.END_ROD,top,6,.1,.1,.1,.08);
            world.playSound(top,Sound.BLOCK_ANVIL_USE,.8f,1.3f);world.playSound(top,Sound.BLOCK_SMITHING_TABLE_USE,1f,.9f);}
    }
    private void glow(Parts part,int light,float scale){
        if(part.entities().size()<2)return;ItemDisplay core=part.entities().get(1);int l=Math.clamp(light,0,15);core.setBrightness(new Display.Brightness(l,l));
        float radians=(float)Math.toRadians(part.yaw()+90);
        /* scale the disc around its own centre (model x/z 8, y 16.55 = 0.534 above the entity's origin) */
        Vector3f t=new Vector3f(0,.5f+(1-scale)*(16.55f-8)/16f,0);
        core.setInterpolationDelay(0);core.setTransformation(new Transformation(t,new Quaternionf().rotateY(radians),new Vector3f(scale,scale,scale),new Quaternionf()));
    }
    public void strike(Location location,String tier){
        if(closed||location==null||location.getWorld()==null)return;Block block=location.getBlock();Parts part=stations.get(key(block));if(part!=null)strikes.put(key(block),0);
        Color color=Color.fromRGB(255,140,40); /* ember sparks from the rune hearth, whatever the material */
        Location impact=block.getLocation().add(.5,1,.5);for(Player player:impact.getWorld().getPlayers())if(player.getLocation().distanceSquared(impact)<=32*32){player.spawnParticle(Particle.DUST,impact,8,.2,.12,.2,new Particle.DustOptions(color,1));player.playSound(impact,Sound.BLOCK_ANVIL_HIT,.6f,1.1f);}
    }
    private void pose(Parts parts,float angle){
        float radians=(float)Math.toRadians(parts.yaw()+90); /* +90 lines the model up with the vanilla anvil it covers */for(int i=0;i<parts.entities().size();i++){
            // The anvil model sits exactly on the block (y .5 centres a 0-16 model) and encloses the vanilla
            // anvil underneath; the hammer model lies on the anvil's top face (model y 8 = 1.025 blocks).
            Quaternionf rotation=new Quaternionf().rotateY(radians);Vector3f translation=new Vector3f(0,.5f,0);
            parts.entities().get(i).setInterpolationDelay(0);parts.entities().get(i).setTransformation(new Transformation(translation,rotation,new Vector3f(1),new Quaternionf()));
        }
    }
    private void tick(){
        if(closed)return;
        int budget=16;for(Iterator<String> it=pending.iterator();it.hasNext()&&budget-->0;){if(stations.size()*3+3>configs.apply("config").getInt("visuals.max-station-displays",128))break;String next=it.next();it.remove();Location loc=location(next);if(loc!=null&&loc.getWorld().isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4))placed(loc.getBlock());}
        strikes.clear();
        for(var entry:new ArrayList<>(heat.entrySet())){
            Parts part=stations.get(entry.getKey());if(part==null){heat.remove(entry.getKey());continue;}int[] h=entry.getValue();int tick=h[0]++;
            if(h[1]==0){ /* completion flash, then back to idle */
                if(tick<FLASH_TICKS)glow(part,15,1f+.14f*(FLASH_TICKS-tick)/FLASH_TICKS);else{glow(part,IDLE_LIGHT,1f);heat.remove(entry.getKey());}continue;}
            double p=Math.min(1,tick/(double)h[1]);
            /* gradually brighter, with a pulse that quickens as it heats */
            int light=(int)Math.round(IDLE_LIGHT+(15-IDLE_LIGHT)*p+Math.sin(tick*(.35+.5*p))*2*(1-p*.5));
            glow(part,light,1f+.03f*(float)Math.sin(tick*.5));
            Location top=part.location().clone().add(0,1.15,0);
            if(tick%3==0)top.getWorld().spawnParticle(Particle.SMALL_FLAME,top,1+(int)(p*3),.12,.02,.12,.02);
            if(tick%5==0)top.getWorld().spawnParticle(Particle.LAVA,top,1,.1,0,.1,0);
            if(tick%2==0&&p>.5)top.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,top.clone().add(0,.1,0),2,.15,.1,.15,.05);
            if(tick>=h[1])heat.remove(entry.getKey()); /* completion arrives separately as heat(location,0) */
        }
    }
    private Location location(String key){try{String[] p=key.split(":");World world=plugin.getServer().getWorld(UUID.fromString(p[0]));return world==null?null:new Location(world,Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));}catch(IllegalArgumentException e){return null;}}
    private void show(Player player,Parts parts){for(Entity entity:parts.entities())if(pack!=null&&pack.loaded(player))player.showEntity(plugin,entity);else player.hideEntity(plugin,entity);}
    private void visibility(Player player){if(closed)return;for(Parts parts:stations.values())if(parts.location().getWorld().equals(player.getWorld()))show(player,parts);}
    private void enqueue(Chunk chunk){
        if(closed)return;Set<UUID> owned=new HashSet<>();for(Parts part:stations.values())for(Entity entity:part.entities())owned.add(entity.getUniqueId());
        for(Entity entity:chunk.getEntities())if(entity.getPersistentDataContainer().getOrDefault(OWNER,PersistentDataType.STRING,"").startsWith("station:")&&!owned.contains(entity.getUniqueId()))entity.remove();
        for(NamespacedKey key:chunk.getPersistentDataContainer().getKeys())if(key.getNamespace().equals("blastforge")&&key.getKey().startsWith("station_")){
            try{String[] bits=key.getKey().split("_");if(bits.length!=4)continue;int x=Integer.parseInt(bits[1]),y=Integer.parseInt(bits[2]),z=Integer.parseInt(bits[3]);if(x<0||x>15||z<0||z>15||y<chunk.getWorld().getMinHeight()||y>=chunk.getWorld().getMaxHeight())continue;pending.add(chunk.getWorld().getUID()+":"+((chunk.getX()<<4)+x)+":"+y+":"+((chunk.getZ()<<4)+z));}catch(NumberFormatException ignored){}
        }
    }
    @EventHandler public void load(ChunkLoadEvent event){enqueue(event.getChunk());}
    @EventHandler public void unload(ChunkUnloadEvent event){Chunk chunk=event.getChunk();for(var entry:new ArrayList<>(stations.entrySet())){Location l=entry.getValue().location();if(l.getWorld().equals(chunk.getWorld())&&(l.getBlockX()>>4)==chunk.getX()&&(l.getBlockZ()>>4)==chunk.getZ())remove(entry.getKey());}}
    @EventHandler public void unload(WorldUnloadEvent event){for(var e:new ArrayList<>(stations.entrySet()))if(e.getValue().location().getWorld().equals(event.getWorld()))remove(e.getKey());}
    @EventHandler public void join(PlayerJoinEvent event){visibility(event.getPlayer());}
    private void later(Runnable action){long expected=generation;plugin.getServer().getScheduler().runTask(plugin,()->{if(!closed&&generation==expected)action.run();});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent event){later(()->{if(!event.isCancelled())placed(event.getBlockPlaced());});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void breakBlock(BlockBreakEvent event){later(()->{if(!event.isCancelled()&&!marked(event.getBlock()))removed(event.getBlock());});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explode(EntityExplodeEvent event){for(Block block:List.copyOf(event.blockList()))later(()->{if(!event.isCancelled()&&!marked(block))removed(block);});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explode(BlockExplodeEvent event){for(Block block:List.copyOf(event.blockList()))later(()->{if(!event.isCancelled()&&!marked(block))removed(block);});}
    @Override public void close(){closed=true;generation++;if(task!=null){task.cancel();task=null;}stations.values().forEach(s->s.entities().forEach(Entity::remove));stations.clear();pending.clear();strikes.clear();heat.clear();HandlerList.unregisterAll(this);}
}
