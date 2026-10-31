package dev.duckmc.blastforge.expansion;

import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/** Plugin-owned professions/merchants. All mutation is confined to the server thread. */
public final class VillagerSystem implements Listener,AutoCloseable {
    public static final NamespacedKey KIND=new NamespacedKey("blastforge","villager_kind");
    private static final NamespacedKey TOKEN=new NamespacedKey("blastforge","merchant_token");
    private final JavaPlugin plugin;private final ExpansionItems items;private final Function<String,FileConfiguration> configs;private final PersistentStore store;private final ExpansionProtection protection;
    private final Map<UUID,State> states=new LinkedHashMap<>();private final Map<String,UUID> claims=new HashMap<>();
    private final Map<UUID,VillagerRules.Stability> stable=new HashMap<>();private final LinkedHashSet<String> tables=new LinkedHashSet<>();
    private final Map<UUID,Long> pendingTrades=new HashMap<>();private BukkitTask task;private long tick,nextSpawn;private PackVisuals pack;private MerchantAppearance appearance;
    private static final class State {
        UUID id,token;String kind,site,area;int level=1,xp,restocks;long expires,nextRestock,day,seed;boolean locked;List<VillagerOffers.Offer> offers=new ArrayList<>();
    }
    public VillagerSystem(JavaPlugin plugin,ExpansionItems items,Function<String,FileConfiguration> configs,PersistentStore store,ExpansionProtection protection){
        this.plugin=plugin;this.items=items;this.configs=configs;this.store=store;this.protection=protection;
        for(var entry:store.entries("villagers").entrySet())try{State state=decode(entry.getValue());if(!state.id.toString().equals(entry.getKey()))throw new IllegalArgumentException("UUID mismatch");states.put(state.id,state);}catch(RuntimeException error){plugin.getLogger().warning("Quarantined malformed merchant record "+entry.getKey()+": "+error.getMessage());}
        for(var entry:store.entries("villager_claims").entrySet())try{UUID id=UUID.fromString(entry.getValue());if(states.containsKey(id))claims.put(entry.getKey(),id);else plugin.getLogger().warning("Orphan job claim retained for review: "+entry.getKey());}catch(IllegalArgumentException error){plugin.getLogger().warning("Invalid merchant claim retained: "+entry.getKey());}
    }
    public void visuals(PackVisuals value){pack=value;if(appearance!=null)appearance.close();appearance=new MerchantAppearance(plugin,items,configs,value);}
    private FileConfiguration cfg(){return configs.apply("villagers");}
    public void start(){
        if(task!=null)return;plugin.getServer().getPluginManager().registerEvents(this,plugin);if(appearance==null)appearance=new MerchantAppearance(plugin,items,configs,pack);
        for(World world:plugin.getServer().getWorlds())for(Chunk chunk:world.getLoadedChunks()){tableChunk(chunk);for(Entity entity:chunk.getEntities())if(entity instanceof AbstractVillager merchant)reconcile(merchant);}
        nextSpawn=System.currentTimeMillis()+seconds("miner.interval-seconds",1200);task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::pump,1,20);
    }
    public String kind(AbstractVillager merchant){return merchant.getPersistentDataContainer().getOrDefault(KIND,PersistentDataType.STRING,"");}
    boolean locked(UUID id){State state=states.get(id);return state!=null&&state.locked;}
    static String site(Block block){return block.getWorld().getUID()+":"+block.getX()+":"+block.getY()+":"+block.getZ();}
    private Location location(String site){if(site==null||site.isEmpty())return null;try{String[] p=site.split(":");World world=plugin.getServer().getWorld(UUID.fromString(p[0]));return world==null?null:new Location(world,Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));}catch(RuntimeException error){return null;}}
    private boolean loaded(Location location){return location!=null&&location.getWorld().isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4);}
    private boolean eligible(Villager villager){return villager.isValid()&&!villager.isDead()&&villager.isAdult()&&kind(villager).isEmpty()&&villager.getProfession()==Villager.Profession.NONE&&villager.getVillagerExperience()==0&&villager.getTrader()==null&&!states.containsKey(villager.getUniqueId())&&!claims.containsValue(villager.getUniqueId());}
    /** Called only after a loaded candidate and permitted table have been selected. */
    boolean claim(Villager villager,Block table){
        String site=site(table);if(table.getType()!=Material.ENCHANTING_TABLE||!eligible(villager)||claims.containsKey(site)||store.get("villager_claims",site)!=null||states.values().stream().filter(s->s.kind.equals("magician")).count()>=Math.clamp(cfg().getInt("magician.maximum-claims",256),1,4096))return false;
        State state=new State();state.id=villager.getUniqueId();state.token=UUID.randomUUID();state.kind="magician";state.site=site;state.area="";state.nextRestock=System.currentTimeMillis()+seconds("magician.restock-seconds",600);state.day=villager.getWorld().getFullTime()/24000;state.seed=new Random().nextLong();state.offers=new VillagerOffers(items,cfg()).magician(1,state.seed);
        // Publish both ownership records atomically before exposing custom trades.
        store.batch(()->{store.put("villagers",state.id.toString(),encode(state));store.put("villager_claims",site,state.id.toString());});states.put(state.id,state);claims.put(site,state.id);tag(villager,state);villager.setProfession(Villager.Profession.LIBRARIAN);villager.setVillagerLevel(1);apply(villager,state);return true;
    }
    private void tag(AbstractVillager merchant,State state){merchant.getPersistentDataContainer().set(KIND,PersistentDataType.STRING,state.kind);merchant.getPersistentDataContainer().set(TOKEN,PersistentDataType.STRING,state.token.toString());merchant.setPersistent(true);merchant.setRemoveWhenFarAway(false);merchant.customName(Component.text(state.kind.equals("magician")?"Magician":"Wandering Miner",state.kind.equals("magician")?NamedTextColor.LIGHT_PURPLE:NamedTextColor.GOLD));merchant.setCustomNameVisible(true);}
    private void apply(AbstractVillager merchant,State state){merchant.setRecipes(state.offers.stream().map(VillagerOffers.Offer::recipe).toList());if(merchant instanceof Villager v){v.setVillagerLevel(Math.clamp(state.level,1,5));v.setVillagerExperience(state.xp);}}
    void reconcile(AbstractVillager merchant){
        State state=states.get(merchant.getUniqueId());if(state==null){if(!kind(merchant).isEmpty()){merchant.setRecipes(List.of());plugin.getLogger().warning("Marked merchant has no trusted state; offers disabled: "+merchant.getUniqueId());}return;}
        if(state.kind.equals("miner")&&state.expires<=System.currentTimeMillis()){expire(merchant,state);return;}
        String token=merchant.getPersistentDataContainer().get(TOKEN,PersistentDataType.STRING);if(token!=null&&!token.equals(state.token.toString())){merchant.setRecipes(List.of());plugin.getLogger().warning("Merchant ownership token mismatch: "+state.id);return;}
        tag(merchant,state);apply(merchant,state);if(appearance!=null)appearance.track(merchant,state.kind);
    }
    private void tableChunk(Chunk chunk){for(BlockState state:chunk.getTileEntities())if(state.getType()==Material.ENCHANTING_TABLE)tables.add(site(state.getBlock()));}
    private void searchClaims(){
        if(!cfg().getBoolean("magician.enabled",true))return;int budget=Math.clamp(cfg().getInt("magician.claim-max-checks",16),1,128);var candidates=new ArrayList<>(tables);
        for(String key:candidates){if(budget--<=0)break;tables.remove(key);Location location=location(key);if(!loaded(location))continue;if(location.getBlock().getType()!=Material.ENCHANTING_TABLE)continue;tables.add(key);if(claims.containsKey(key)||store.get("villager_claims",key)!=null)continue;
            Player owner=location.getWorld().getPlayers().stream().filter(p->p.getGameMode()!=GameMode.SPECTATOR&&p.getLocation().distanceSquared(location)<=64*64&&protection.canAffect(p,location)).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(location))).orElse(null);if(owner==null)continue;
            double radius=Math.clamp(cfg().getDouble("magician.claim-radius",8),1,32);var nearby=location.getWorld().getNearbyEntities(location,radius,radius,radius).stream().filter(e->e instanceof Villager v&&eligible(v)).map(e->(Villager)e).sorted(Comparator.comparingDouble(v->v.getLocation().distanceSquared(location))).limit(8).toList();
            for(Villager villager:nearby){if(villager.getLocation().distanceSquared(location)>radius*radius||!protection.canAffect(owner,villager.getLocation()))continue;Location stand=standBeside(location.getBlock());if(stand==null||villager.getPathfinder().findPath(stand)==null)continue;if(claim(villager,location.getBlock())){villager.getPathfinder().moveTo(stand,.65);break;}}
        }
    }
    private Location standBeside(Block table){for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){Location point=table.getLocation().add(d[0]+.5,0,d[1]+.5);if(safe(point)&&protection.canSpawn(point))return point;}return null;}
    private void pump(){
        long now=System.currentTimeMillis();tick++;
        if(tick%Math.max(1,cfg().getInt("magician.claim-interval",10))==0){for(Player player:plugin.getServer().getOnlinePlayers())tableChunk(player.getLocation().getChunk());searchClaims();}
        if(tick%5==0)for(State state:new ArrayList<>(states.values())){
            Entity entity=plugin.getServer().getEntity(state.id);
            // Expired unloaded records are tombstones, not active population/area reservations.
            // Keep the record until entity load so reconciliation can remove that entity safely.
            if(state.kind.equals("miner")&&now>=state.expires){if(entity instanceof AbstractVillager merchant&&merchant.isValid())expire(merchant,state);else if(appearance!=null)appearance.remove(state.id);continue;}
            if(!(entity instanceof AbstractVillager merchant)||!merchant.isValid())continue;
            if(!kind(merchant).equals(state.kind)||!state.token.toString().equals(merchant.getPersistentDataContainer().get(TOKEN,PersistentDataType.STRING)))continue;
            if(state.kind.equals("magician"))work((Villager)merchant,state,now);else restock(merchant,state,now,true);
            if(appearance!=null)appearance.track(merchant,state.kind);
        }
        if(tick%30==0){updateStability(now);for(var entry:store.entries("miner_reservations").entrySet())try{if(Long.parseLong(entry.getValue())<=now)store.remove("miner_reservations",entry.getKey());}catch(NumberFormatException ignored){}}
        if(now>=nextSpawn){nextSpawn=now+seconds("miner.interval-seconds",1200);naturalSpawn(now);}
        if(appearance!=null)appearance.tick();
    }
    private void work(Villager merchant,State state,long now){
        Location table=location(state.site);boolean hasSite=loaded(table)&&table.getWorld().equals(merchant.getWorld())&&table.getBlock().getType()==Material.ENCHANTING_TABLE&&state.id.equals(claims.get(state.site));
        if(loaded(table)&&table.getBlock().getType()!=Material.ENCHANTING_TABLE){
            if(!state.locked&&merchant.getVillagerExperience()==0){release(merchant,state);return;}
            claims.remove(state.site,state.id);store.remove("villager_claims",state.site);state.site="";save(state);
        }
        if(state.site.isEmpty()&&state.locked){for(String candidate:tables){Location next=location(candidate);if(!claims.containsKey(candidate)&&store.get("villager_claims",candidate)==null&&loaded(next)&&next.getBlock().getType()==Material.ENCHANTING_TABLE&&next.getWorld().equals(merchant.getWorld())&&next.distanceSquared(merchant.getLocation())<=64&&protection.canSpawn(next)){
            state.site=candidate;claims.put(candidate,state.id);store.batch(()->{store.put("villager_claims",candidate,state.id.toString());save(state);});break;}}
        }
        if(!hasSite)return;double radius=Math.clamp(cfg().getDouble("magician.work-radius",4),1,16);boolean near=merchant.getLocation().distanceSquared(table)<=radius*radius;
        if(!near&&merchant.getTrader()==null){Location stand=standBeside(table.getBlock());if(stand!=null)merchant.getPathfinder().moveTo(stand,.6);}
        restock(merchant,state,now,near);if(near&&merchant.getTrader()==null&&configs.apply("config").getBoolean("visuals.enabled",true))merchant.getWorld().spawnParticle(Particle.ENCHANT,merchant.getLocation().add(0,1.5,0),2,.2,.1,.2,0);
    }
    private void restock(AbstractVillager merchant,State state,long now,boolean canWork){
        long day=merchant.getWorld().getFullTime()/24000;if(day>state.day){state.day=day;state.restocks=0;save(state);}
        if(!canWork||merchant.getTrader()!=null||now<state.nextRestock||state.restocks>=Math.clamp(cfg().getInt(state.kind+".restocks-per-day",2),0,16))return;
        var updated=new ArrayList<VillagerOffers.Offer>();for(int i=0;i<state.offers.size();i++){var existing=state.offers.get(i);MerchantRecipe recipe=i<merchant.getRecipeCount()?merchant.getRecipe(i):existing.recipe();var event=new VillagerReplenishTradeEvent(merchant,VillagerOffers.copy(recipe));restocking=true;try{plugin.getServer().getPluginManager().callEvent(event);}finally{restocking=false;}if(!event.isCancelled()){recipe=event.getRecipe();recipe.setUses(0);}updated.add(new VillagerOffers.Offer(existing.id(),existing.level(),VillagerOffers.copy(recipe)));}
        state.offers=updated;state.restocks++;state.nextRestock=now+seconds(state.kind+".restock-seconds",600);save(state);apply(merchant,state);
    }
    private void release(Villager merchant,State state){closeTrader(merchant);store.batch(()->{store.remove("villager_claims",state.site);store.remove("villagers",state.id.toString());});claims.remove(state.site,state.id);states.remove(state.id);merchant.setRecipes(List.of());merchant.getPersistentDataContainer().remove(KIND);merchant.getPersistentDataContainer().remove(TOKEN);merchant.customName(null);merchant.setCustomNameVisible(false);merchant.setProfession(Villager.Profession.NONE);if(appearance!=null)appearance.strip(merchant);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void acquire(VillagerAcquireTradeEvent event){if(!kind(event.getEntity()).isEmpty())event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void career(VillagerCareerChangeEvent event){if(!kind(event.getEntity()).isEmpty())event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void naturalRestock(VillagerReplenishTradeEvent event){if(!kind(event.getEntity()).isEmpty()&&!restocking)event.setCancelled(true);}
    private boolean restocking;
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void guardTrade(PlayerTradeEvent event){
        if(kind(event.getVillager()).isEmpty())return;State state=states.get(event.getVillager().getUniqueId());if(state==null||state.kind.equals("miner")&&state.expires<=System.currentTimeMillis()){event.setCancelled(true);return;}
        if(!(event.getPlayer().getOpenInventory().getTopInventory() instanceof MerchantInventory inventory)){event.setCancelled(true);return;}
        if(!ordinaryInputs(inventory,event.getTrade())){event.setCancelled(true);event.getPlayer().sendMessage(Component.text("This merchant accepts ordinary items only.",NamedTextColor.YELLOW));}
    }
    private boolean ordinaryInputs(MerchantInventory inventory,MerchantRecipe trade){
        for(int i=0;i<2;i++){ItemStack stack=inventory.getItem(i);if(stack!=null&&!stack.getType().isAir()&&!VillagerOffers.ordinary(stack,items))return false;}
        // Validate current selected result as well; foreign replacements cannot mint a custom output.
        State state=states.get(((AbstractVillager)inventory.getMerchant()).getUniqueId());if(state==null)return false;
        return state.offers.stream().anyMatch(o->o.recipe().getResult().isSimilar(trade.getResult())&&o.recipe().getIngredients().equals(trade.getIngredients()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void traded(PlayerTradeEvent event){
        State state=states.get(event.getVillager().getUniqueId());if(state==null||event.isCancelled())return;long marker=pendingTrades.merge(state.id,1L,Long::sum);
        plugin.getServer().getScheduler().runTask(plugin,()->{if(event.isCancelled()||!event.getVillager().isValid()||!states.containsKey(state.id)||pendingTrades.getOrDefault(state.id,0L)!=marker)return;
            AbstractVillager merchant=event.getVillager();boolean used=false;var changed=new ArrayList<VillagerOffers.Offer>();for(int i=0;i<state.offers.size();i++){var offer=state.offers.get(i);MerchantRecipe recipe=i<merchant.getRecipeCount()?merchant.getRecipe(i):offer.recipe();if(recipe.getUses()>offer.recipe().getUses())used=true;changed.add(new VillagerOffers.Offer(offer.id(),offer.level(),VillagerOffers.copy(recipe)));}
            int experience=merchant instanceof Villager v?v.getVillagerExperience():state.xp;if(experience>state.xp)used=true;if(!used)return;state.offers=changed;state.locked=true;state.xp=experience;
            if(merchant instanceof Villager v&&state.kind.equals("magician")){int level=1;int[] defaults={10,70,150,250};List<Integer> thresholds=cfg().getIntegerList("magician.level-xp");for(int i=0;i<4;i++)if(state.xp>=(thresholds.size()>i?thresholds.get(i):defaults[i]))level=i+2;
                if(level!=state.level){state.level=level;var merged=new ArrayList<VillagerOffers.Offer>(state.offers);for(var offer:new VillagerOffers(items,cfg()).magician(level,state.seed))if(merged.stream().noneMatch(old->old.id().equals(offer.id())))merged.add(offer);state.offers=merged;   /* level-ups only add offers */closeTrader(merchant);apply(v,state);}}
            save(state);
        });
    }
    private long seconds(String path,long fallback){return Math.clamp(cfg().getLong(path,fallback),1,86400)*1000;}
    private void updateStability(long now){Set<UUID> online=new HashSet<>();for(Player player:plugin.getServer().getOnlinePlayers()){online.add(player.getUniqueId());Location l=player.getLocation();var old=stable.get(player.getUniqueId());double radius=Math.clamp(cfg().getDouble("miner.stability-radius",64),1,1024);if(old==null||!old.contains(l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),radius)||now<old.since())stable.put(player.getUniqueId(),new VillagerRules.Stability(l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),now));}stable.keySet().retainAll(online);}
    private void naturalSpawn(long now){
        if(!cfg().getBoolean("miner.enabled",true))return;double radius=Math.clamp(cfg().getDouble("miner.stability-radius",64),1,1024);var candidates=new ArrayList<Player>();for(Player p:plugin.getServer().getOnlinePlayers()){var s=stable.get(p.getUniqueId());Location l=p.getLocation();if(p.getGameMode()!=GameMode.SPECTATOR&&s!=null&&s.ready(l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),now,seconds("miner.stability-seconds",600),radius))candidates.add(p);}if(candidates.isEmpty())return;
        Player player=candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));double populationRadius=Math.clamp(cfg().getDouble("miner.population-radius",32),1,128);long population=player.getWorld().getNearbyEntities(player.getLocation(),populationRadius,populationRadius,populationRadius).stream().filter(e->e instanceof Villager v&&!v.isDead()&&!kind(v).equals("miner")&&v.getLocation().distanceSquared(player.getLocation())<=populationRadius*populationRadius).count();
        if(ThreadLocalRandom.current().nextDouble()<VillagerRules.chance(cfg().getDouble("miner.base-chance",.05),cfg().getDouble("miner.villager-bonus",.05),(int)population))spawnMiner(player);
    }
    public boolean spawnMiner(Player player){
        long now=System.currentTimeMillis();if(!player.isOnline()||states.values().stream().filter(s->s.kind.equals("miner")&&s.expires>now).count()>=Math.clamp(cfg().getInt("miner.max-active",16),1,256))return false;
        Location anchor=player.getLocation();double duplicate=Math.clamp(cfg().getDouble("miner.duplicate-radius",128),16,1024);for(State state:states.values())if(state.kind.equals("miner")&&state.expires>now){Location area=location(state.area);if(area!=null&&area.getWorld().equals(anchor.getWorld())&&area.distanceSquared(anchor)<duplicate*duplicate)return false;}
        for(var reserved:store.entries("miner_reservations").entrySet()){try{if(Long.parseLong(reserved.getValue())<=now){store.remove("miner_reservations",reserved.getKey());continue;}}catch(NumberFormatException ignored){}Location area=location(reserved.getKey());if(area!=null&&area.getWorld().equals(anchor.getWorld())&&area.distanceSquared(anchor)<duplicate*duplicate)return false;}
        double radius=Math.clamp(cfg().getDouble("miner.spawn-radius",24),4,128),minimum=Math.clamp(cfg().getDouble("miner.min-spawn-distance",12),2,radius);Location candidate=null;var random=ThreadLocalRandom.current();
        for(int i=0;i<Math.clamp(cfg().getInt("miner.attempts",16),1,64);i++){double angle=random.nextDouble()*Math.PI*2,distance=minimum+random.nextDouble()*(radius-minimum);int x=(int)Math.floor(anchor.getX()+Math.cos(angle)*distance),z=(int)Math.floor(anchor.getZ()+Math.sin(angle)*distance);if(!anchor.getWorld().isChunkLoaded(x>>4,z>>4))continue;
            int surface=anchor.getWorld().getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;Location point=new Location(anchor.getWorld(),x+.5,surface,z+.5);if(Math.abs(point.getY()-anchor.getY())>24||!safe(point)||!protection.canSpawn(point))continue;candidate=point;break;}
        if(candidate==null)return false;String area=site(anchor.getBlock());long deadline=System.currentTimeMillis()+seconds("miner.despawn-seconds",1800);store.put("miner_reservations",area,Long.toString(deadline));State state=new State();state.kind="miner";state.site="";state.area=area;state.token=UUID.randomUUID();state.expires=deadline;state.nextRestock=System.currentTimeMillis()+seconds("miner.restock-seconds",600);state.day=anchor.getWorld().getFullTime()/24000;state.offers=new VillagerOffers(items,cfg()).miner(random);
        Villager merchant=null;try{if(!safe(candidate)||!protection.canSpawn(candidate))return false;merchant=candidate.getWorld().spawn(candidate,Villager.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,entity->{state.id=entity.getUniqueId();save(state);tag(entity,state);entity.setAdult();entity.setProfession(Villager.Profession.TOOLSMITH);entity.setVillagerExperience(1);apply(entity,state);});
            if(!merchant.isValid()||merchant.isDead()){store.remove("villagers",state.id.toString());return false;}states.put(state.id,state);if(appearance!=null)appearance.track(merchant,"miner");return true;
        }catch(RuntimeException error){if(merchant!=null)merchant.remove();if(state.id!=null)store.remove("villagers",state.id.toString());plugin.getLogger().warning("Miner spawn failed safely: "+error.getMessage());return false;}finally{store.remove("miner_reservations",area);}
    }
    static boolean safe(Location point){
        World world=point.getWorld();if(world==null||point.getY()<=world.getMinHeight()||point.getY()+2>=world.getMaxHeight()||!world.isChunkLoaded(point.getBlockX()>>4,point.getBlockZ()>>4)||!world.getWorldBorder().isInside(point))return false;
        Block floor=point.clone().add(0,-1,0).getBlock();if(!floor.getType().isSolid()||floor.isLiquid()||hazard(floor.getType()))return false;
        for(int y=0;y<2;y++){Block block=point.clone().add(0,y,0).getBlock();if(!block.isPassable()||block.isLiquid()||hazard(block.getType()))return false;}
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=1;y++){Block block=world.getBlockAt(point.getBlockX()+x,point.getBlockY()+y,point.getBlockZ()+z);if(hazard(block.getType()))return false;}
        return world.getNearbyEntities(new BoundingBox(point.getX()-.35,point.getY(),point.getZ()-.35,point.getX()+.35,point.getY()+1.95,point.getZ()+.35),e->e instanceof LivingEntity).isEmpty();
    }
    private static boolean hazard(Material m){return Set.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH,Material.WITHER_ROSE).contains(m);}
    private void expire(AbstractVillager merchant,State state){closeTrader(merchant);if(appearance!=null)appearance.remove(state.id);merchant.remove();store.remove("villagers",state.id.toString());states.remove(state.id);}
    private void closeTrader(AbstractVillager merchant){if(merchant.getTrader()!=null)merchant.getTrader().closeInventory();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent event){if(event.getBlockPlaced().getType()==Material.ENCHANTING_TABLE)plugin.getServer().getScheduler().runTask(plugin,()->{if(!event.isCancelled()&&event.getBlockPlaced().getType()==Material.ENCHANTING_TABLE)tables.add(site(event.getBlockPlaced()));});}
    @EventHandler public void load(ChunkLoadEvent event){tableChunk(event.getChunk());}
    @EventHandler public void load(EntitiesLoadEvent event){for(Entity entity:event.getEntities())if(entity instanceof AbstractVillager merchant)reconcile(merchant);}
    @EventHandler public void unload(EntitiesUnloadEvent event){for(Entity entity:event.getEntities())if(entity instanceof AbstractVillager merchant){State state=states.get(entity.getUniqueId());if(state!=null)capture(merchant,state);if(appearance!=null)appearance.remove(entity.getUniqueId());}}
    @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent event){State state=states.remove(event.getEntity().getUniqueId());if(state==null)return;event.getDrops().removeIf(i->!items.id(i).isEmpty()||i.getType()==Material.IRON_PICKAXE);if(appearance!=null)appearance.remove(state.id);store.batch(()->{store.remove("villagers",state.id.toString());if(!state.site.isEmpty())store.remove("villager_claims",state.site);});claims.remove(state.site,state.id);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void transform(EntityTransformEvent event){if(event.getEntity() instanceof AbstractVillager merchant&&!kind(merchant).isEmpty())event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){stable.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void teleport(PlayerTeleportEvent event){if(event.getTo()!=null&&(event.getFrom().getWorld()!=event.getTo().getWorld()||event.getFrom().distanceSquared(event.getTo())>4096))stable.remove(event.getPlayer().getUniqueId());}
    private void capture(AbstractVillager merchant,State state){var captured=new ArrayList<VillagerOffers.Offer>();for(int i=0;i<state.offers.size();i++){var offer=state.offers.get(i);captured.add(new VillagerOffers.Offer(offer.id(),offer.level(),i<merchant.getRecipeCount()?VillagerOffers.copy(merchant.getRecipe(i)):offer.recipe()));}state.offers=captured;if(merchant instanceof Villager v){state.xp=v.getVillagerExperience();state.locked|=state.xp>0&&state.kind.equals("magician");}save(state);}
    private void save(State state){store.put("villagers",state.id.toString(),encode(state));}
    private static String encode(State s){var data=new YamlConfiguration();data.set("id",s.id.toString());data.set("token",s.token.toString());data.set("kind",s.kind);data.set("site",s.site);data.set("area",s.area);data.set("level",s.level);data.set("xp",s.xp);data.set("locked",s.locked);data.set("expires",s.expires);data.set("restock",s.nextRestock);data.set("day",s.day);data.set("restocks",s.restocks);data.set("seed",s.seed);data.set("offers",VillagerOffers.encode(s.offers));return data.saveToString();}
    private static State decode(String encoded){var data=YamlConfiguration.loadConfiguration(new java.io.StringReader(encoded));State s=new State();s.id=UUID.fromString(data.getString("id"));s.token=UUID.fromString(data.getString("token"));s.kind=data.getString("kind");if(!Set.of("magician","miner").contains(s.kind))throw new IllegalArgumentException("kind");s.site=data.getString("site","");s.area=data.getString("area","");s.level=Math.clamp(data.getInt("level",1),1,5);s.xp=Math.max(0,data.getInt("xp"));s.locked=data.getBoolean("locked");s.expires=data.getLong("expires");s.nextRestock=data.getLong("restock");s.day=data.getLong("day");s.restocks=data.getInt("restocks");s.seed=data.contains("seed")?data.getLong("seed"):s.id.getMostSignificantBits()^s.id.getLeastSignificantBits();s.offers=VillagerOffers.decode(data.getString("offers",""));return s;}
    @Override public void close(){if(task!=null){task.cancel();task=null;}for(State state:states.values()){Entity entity=plugin.getServer().getEntity(state.id);if(entity instanceof AbstractVillager merchant&&merchant.isValid()){closeTrader(merchant);capture(merchant,state);}}if(appearance!=null)appearance.close();stable.clear();pendingTrades.clear();tables.clear();HandlerList.unregisterAll(this);}
}
