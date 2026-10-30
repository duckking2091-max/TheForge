package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import java.util.*;
import java.util.function.Function;

/** Conservative, bounded temporary edits. Journal and chunk tokens jointly authorize restoration. */
final class MagicTerrain implements Listener,AutoCloseable {
    private static final Set<Material> HOSTS=EnumSet.of(Material.STONE,Material.DEEPSLATE,Material.TUFF,Material.DIRT,
        Material.GRASS_BLOCK,Material.COARSE_DIRT,Material.ROOTED_DIRT,Material.ANDESITE,Material.DIORITE,Material.GRANITE,
        Material.NETHERRACK,Material.END_STONE,Material.BASALT,Material.SMOOTH_BASALT,Material.BLACKSTONE,Material.CALCITE,
        Material.SAND,Material.RED_SAND,Material.SANDSTONE,Material.RED_SANDSTONE,Material.GRAVEL,Material.CLAY,Material.COBBLESTONE,Material.MOSSY_COBBLESTONE,
        Material.COBBLED_DEEPSLATE,Material.PODZOL,Material.MYCELIUM,Material.MUD,Material.PACKED_MUD,Material.MOSS_BLOCK,Material.SOUL_SAND,Material.SOUL_SOIL,
        Material.SNOW_BLOCK,Material.TERRACOTTA,Material.DRIPSTONE_BLOCK,Material.DIRT_PATH,Material.FARMLAND);
    private final JavaPlugin plugin;private final Function<String,FileConfiguration> configs;private final PersistentStore store;
    private final ExpansionProtection protection;private final MagicTerrainJournal journal;
    private int workTick=Integer.MIN_VALUE,spent;
    private final Map<MagicTerrainJournal.Position,Integer> pendingPlacements=new HashMap<>();
    private final Set<Runnable> placementDecisions=new HashSet<>();
    MagicTerrain(JavaPlugin plugin,Function<String,FileConfiguration> configs,PersistentStore store,ExpansionProtection protection){
        this.plugin=plugin;this.configs=configs;this.store=store;this.protection=protection;this.journal=new MagicTerrainJournal(store);
    }
    private MagicTerrainJournal.Position pos(Block b){return new MagicTerrainJournal.Position(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}
    private NamespacedKey token(Block b){return ExpansionItems.key("magic_terrain_"+(b.getX()&15)+"_"+b.getY()+"_"+(b.getZ()&15));}
    private String tokenValue(Block b){return b.getChunk().getPersistentDataContainer().get(token(b),PersistentDataType.STRING);}
    private boolean owned(Block b){var e=journal.records().get(pos(b));return e!=null && !e.phase().equals("INVALIDATED");}
    private void removeToken(Block b){b.getChunk().getPersistentDataContainer().remove(token(b));}
    private boolean custom(Block b){
        if(b.getState() instanceof TileState)return true;
        String scope="material_ore:"+b.getWorld().getUID()+":"+(b.getX()>>4)+":"+(b.getZ()>>4);
        if(store.get(scope,b.getX()+":"+b.getY()+":"+b.getZ())!=null)return true;
        return b.getChunk().getPersistentDataContainer().has(ExpansionItems.key("station_"+(b.getX()&15)+"_"+b.getY()+"_"+(b.getZ()&15)),PersistentDataType.BYTE);
    }
    private boolean excavationSafe(Block b){
        if(!HOSTS.contains(b.getType()))return false;
        // Removing support must not indirectly break containers, plants, redstone or falling blocks.
        for(BlockFace face:List.of(BlockFace.UP,BlockFace.DOWN,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST)){
            Block n=b.getRelative(face);
            if(n.getY()<b.getWorld().getMinHeight() || n.getY()>=b.getWorld().getMaxHeight())continue;
            if(!n.getWorld().isChunkLoaded(n.getX()>>4,n.getZ()>>4))return false;
            // Sand or gravel resting on top would fall into the hole; grass and flowers on top may simply pop off.
            if(face==BlockFace.UP && n.getType().hasGravity())return false;
            if(!n.getType().isAir() && !HOSTS.contains(n.getType()) && !n.isReplaceable())return false;
        }
        return true;
    }
    private int remainingWork(){int tick=Bukkit.getCurrentTick();if(tick!=workTick){workTick=tick;spent=0;}return Math.max(0,Math.max(1,Math.min(256,configs.apply("magic").getInt("temporary-blocks.work-per-tick",128)))-spent);}
    boolean change(Player player,List<Block> requested,Material temporary,double seconds){return change(player,requested,temporary,seconds,false);}
    /** Air or soft blocks (grass, snow layers, flowers) a temporary block may stand in; never fluids. */
    static boolean soft(Block b){return b.getType().isAir() || b.isReplaceable() && !b.isLiquid() && b.getType()!=Material.FIRE && b.getType()!=Material.SOUL_FIRE;}
    /**
     * @param partial when true, blocks that cannot be changed are skipped instead of cancelling the whole edit
     *                (fire fields and walls on uneven Nether/End terrain).
     */
    boolean change(Player player,List<Block> requested,Material temporary,double seconds,boolean partial){
        int max=Math.max(1,Math.min(4096,configs.apply("magic").getInt("temporary-blocks.max-per-player",512)));
        int budget=Math.max(1,Math.min(256,configs.apply("magic").getInt("temporary-blocks.work-per-tick",128)));
        int global=Math.max(max,Math.min(8192,configs.apply("magic").getInt("temporary-blocks.max-global",4096)));
        int available=Math.min(remainingWork(),Math.min(budget,Math.min(max-journal.count(player.getUniqueId()),global-journal.records().size())));
        if(available<=0)return false;
        UUID operation=UUID.randomUUID();String expected=temporary.createBlockData().getAsString();long expiry=System.currentTimeMillis()+(long)(Math.max(0,Math.min(60,seconds))*1000);
        List<Block> accepted=new ArrayList<>();
        for(Block b:requested){
            if(accepted.size()>=available)break;
            if(b.getY()<=b.getWorld().getMinHeight() || b.getY()>=b.getWorld().getMaxHeight()-1 || !protection.canAffect(player,b.getLocation()) || owned(b) || custom(b))continue;
            if(temporary==Material.AIR?!excavationSafe(b):temporary==Material.FIRE?!b.getType().isAir():!soft(b))continue;
            if(temporary==Material.FIRE && !temporary.createBlockData().isSupported(b))continue;
            if(temporary!=Material.AIR && temporary!=Material.FIRE && !b.getWorld().getNearbyEntities(new BoundingBox(b.getX(),b.getY(),b.getZ(),b.getX()+1,b.getY()+1,b.getZ()+1),e->e instanceof LivingEntity).isEmpty())continue;
            accepted.add(b);
        }
        // Wall construction is all-or-nothing to avoid consuming its cooldown on an unusable fragment.
        if(accepted.isEmpty() || !partial && temporary!=Material.AIR && accepted.size()!=requested.size())return false;
        List<Block> reserved=new ArrayList<>(),changed=new ArrayList<>();
        try{
            // One durable commit for the complete reservation set. No world/chunk mutations in the transaction.
            Map<Block,String> originals=new LinkedHashMap<>();for(Block b:accepted)originals.put(b,b.getBlockData().getAsString());
            journal.batch(()->{for(Block b:accepted)if(journal.reserve(pos(b),operation,player.getUniqueId(),originals.get(b),expected,expiry))reserved.add(b);});
            for(Block b:reserved){
                b.getChunk().getPersistentDataContainer().set(token(b),PersistentDataType.STRING,operation.toString());
                b.setBlockData(temporary.createBlockData(),false);changed.add(b);spent++;
            }
            journal.batch(()->{for(Block b:changed)journal.applied(pos(b),operation);});
        }catch(RuntimeException failure){
            // Preserve journals if rollback cannot be durably completed; never continue unjournaled edits.
            List<MagicTerrainJournal.Position> restored=new ArrayList<>();
            for(Block b:reserved){var e=journal.records().get(pos(b));if(e!=null && operation.toString().equals(tokenValue(b)) && e.expected().equals(b.getBlockData().getAsString())){b.setBlockData(Bukkit.createBlockData(e.original()),false);removeToken(b);restored.add(pos(b));}}
            journal.batch(()->restored.forEach(journal::forget));
            plugin.getLogger().warning("Temporary terrain operation aborted: "+failure.getMessage());return false;
        }
        return !changed.isEmpty();
    }
    /** Public integration callers must invalidate before directly changing an owned coordinate. */
    void invalidate(Block block){if(journal.records().containsKey(pos(block))){journal.invalidate(pos(block));removeToken(block);}}
    void pump(long now,boolean force){
        int budget=force?Math.max(1,Math.min(256,configs.apply("magic").getInt("temporary-blocks.work-per-tick",128))):remainingWork(),writes=0;List<Runnable> acknowledgments=new ArrayList<>();
        for(var item:new ArrayList<>(journal.records().entrySet())){
            if(writes>=budget)break;
            var position=item.getKey();var entry=item.getValue();World world=Bukkit.getWorld(position.world());
            if(pendingPlacements.containsKey(position))continue;
            if(world==null || !world.isChunkLoaded(position.x()>>4,position.z()>>4))continue;
            Block block=world.getBlockAt(position.x(),position.y(),position.z());
            if(entry.phase().equals("INVALIDATED")){removeToken(block);acknowledgments.add(()->journal.forget(position));writes++;continue;}
            if(!entry.phase().equals("APPLIED")){
                // DB/region writes cannot be committed atomically; uncertain crash windows are quarantined.
                if(block.getBlockData().getAsString().equals(entry.original())){removeToken(block);acknowledgments.add(()->journal.forget(position));}
                else acknowledgments.add(()->journal.quarantine(position));
                writes++;continue;
            }
            if(!entry.operation().toString().equals(tokenValue(block)) || !journal.mayRestore(position,entry.operation(),block.getBlockData().getAsString())){
                acknowledgments.add(()->journal.quarantine(position));removeToken(block);writes++;continue;
            }
            if(!force && entry.expiry()>now)continue;
            if(!Bukkit.createBlockData(entry.original()).getMaterial().isAir() && !world.getNearbyEntities(new BoundingBox(position.x(),position.y(),position.z(),position.x()+1,position.y()+1,position.z()+1),e->e instanceof LivingEntity).isEmpty())continue;
            block.setBlockData(Bukkit.createBlockData(entry.original()),false);removeToken(block);acknowledgments.add(()->journal.forget(position));writes++;
        }
        // A failed acknowledgment leaves recoverable records; restored blocks are never changed again blindly.
        if(!force)spent+=writes;
        if(!acknowledgments.isEmpty())journal.batch(()->acknowledgments.forEach(Runnable::run));
    }
    private boolean touching(Block b){
        if(owned(b))return true;
        for(BlockFace face:List.of(BlockFace.UP,BlockFace.DOWN,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST)){
            Block n=b.getRelative(face);if(n.getWorld().isChunkLoaded(n.getX()>>4,n.getZ()>>4) && owned(n))return true;
        }return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){
        if(!e.canBuild())return;
        List<Block> blocks=e instanceof BlockMultiPlaceEvent multi?multi.getReplacedBlockStates().stream().map(BlockState::getBlock).toList():List.of(e.getBlockPlaced());
        Map<MagicTerrainJournal.Position,UUID> operations=new LinkedHashMap<>();
        for(Block block:blocks){var position=pos(block);var entry=journal.records().get(position);if(entry!=null && !entry.phase().equals("INVALIDATED")){operations.put(position,entry.operation());pendingPlacements.merge(position,1,Integer::sum);}}
        if(operations.isEmpty())return;
        Runnable decision=new Runnable(){boolean resolved;
            @Override public void run(){if(resolved)return;resolved=true;placementDecisions.remove(this);
                try{
                    if(e.isCancelled() || !e.canBuild())return;
                    List<MagicTerrainJournal.Position> accepted=operations.entrySet().stream().filter(item->{var current=journal.records().get(item.getKey());return current!=null && current.operation().equals(item.getValue());}).map(Map.Entry::getKey).toList();
                    try{journal.batch(()->accepted.forEach(journal::invalidate));}
                    finally{
                        // Placement already happened. Missing tokens keep restoration conservative if its DB acknowledgment fails.
                        for(var position:accepted){World world=Bukkit.getWorld(position.world());if(world!=null && world.isChunkLoaded(position.x()>>4,position.z()>>4))removeToken(world.getBlockAt(position.x(),position.y(),position.z()));}
                    }
                }finally{for(var position:operations.keySet())pendingPlacements.computeIfPresent(position,(key,count)->count==1?null:count-1);}
            }
        };
        placementDecisions.add(decision);plugin.getServer().getScheduler().runTask(plugin,decision);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void broken(BlockBreakEvent e){if(owned(e.getBlock())){e.setCancelled(true);e.setDropItems(false);e.setExpToDrop(0);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(touching(e.getBlock()) || e.getBlocks().stream().anyMatch(b->touching(b)||touching(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){if(touching(e.getBlock()) || e.getBlocks().stream().anyMatch(b->touching(b)||touching(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fluid(BlockFromToEvent e){if(touching(e.getBlock()) || touching(e.getToBlock()))e.setCancelled(true);}
    private boolean explosionTouches(Location location,List<Block> blocks){
        if(blocks.stream().anyMatch(this::touching))return true;
        // Excavated air is absent from an explosion's block list; include its bounded blast neighborhood.
        for(var p:journal.records().keySet())if(p.world().equals(location.getWorld().getUID()) && Math.abs(p.x()-location.getX())<12 && Math.abs(p.y()-location.getY())<12 && Math.abs(p.z()-location.getZ())<12)return true;
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){if(explosionTouches(e.getLocation(),e.blockList()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(BlockExplodeEvent e){if(explosionTouches(e.getBlock().getLocation(),e.blockList()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void physics(BlockPhysicsEvent e){if(touching(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityBlock(EntityChangeBlockEvent e){if(touching(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(touching(e.getBlock()) || e.getIgnitingBlock()!=null && owned(e.getIgnitingBlock()))e.setCancelled(true);}
    /** Spell fire looks and burns like flint-and-steel fire, but never spreads to the world around it. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(e.getCause()==BlockIgniteEvent.IgniteCause.SPREAD && e.getIgnitingBlock()!=null && owned(e.getIgnitingBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fade(BlockFadeEvent e){if(touching(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void grow(BlockGrowEvent e){if(touching(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spread(BlockSpreadEvent e){if(touching(e.getBlock()) || touching(e.getSource()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void form(BlockFormEvent e){if(touching(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void structure(StructureGrowEvent e){if(e.getBlocks().stream().anyMatch(b->touching(b.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fertilize(BlockFertilizeEvent e){if(e.getBlocks().stream().anyMatch(b->touching(b.getBlock())))e.setCancelled(true);}
    @EventHandler public void chunk(ChunkLoadEvent e){/* Global bounded pump picks up loaded journal coordinates next tick. */}
    @Override public void close(){for(Runnable decision:new ArrayList<>(placementDecisions))decision.run();for(int i=0;i<64 && !journal.records().isEmpty();i++)pump(Long.MAX_VALUE,true);HandlerList.unregisterAll(this);}
}
