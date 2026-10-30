package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MagicReviewRegressionTest {
    @TempDir Path directory;
    ServerMock server;org.bukkit.plugin.java.JavaPlugin plugin;PersistentStore store;YamlConfiguration magic;ExpansionItems items;
    org.mockbukkit.mockbukkit.world.WorldMock world;org.mockbukkit.mockbukkit.entity.PlayerMock owner;
    List<AutoCloseable> services=new ArrayList<>();
    @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.createMockPlugin();world=server.addSimpleWorld("review");world.getChunkAt(0,0);world.setPVP(true);owner=server.addPlayer();owner.setOp(true);owner.teleport(new Location(world,8,65,8));magic=new YamlConfiguration();magic.set("particle-intensity",0);items=new ExpansionItems(plugin,n->magic);store=new PersistentStore(directory.resolve("magic.db"));}
    @AfterEach void close() throws Exception {for(AutoCloseable service:services)service.close();store.close();MockBukkit.unmock();}
    WitherSkeleton markedSkeleton(double x){var mob=world.spawn(new Location(world,x,65,8),WitherSkeleton.class);mob.getPersistentDataContainer().set(MagicSummons.MARK,PersistentDataType.STRING,owner.getUniqueId().toString());mob.getPersistentDataContainer().set(ExpansionItems.key("magic_summon_kind"),PersistentDataType.STRING,"darkness");mob.getPersistentDataContainer().set(ExpansionItems.key("magic_summon_expires"),PersistentDataType.LONG,System.currentTimeMillis()+60_000);return mob;}
    @Test void threeDarknessSummonsChooseEnemyRatherThanThemselvesOrEachOther(){
        var first=markedSkeleton(8.5);var second=markedSkeleton(9);var third=markedSkeleton(9.5);var enemy=world.spawn(new Location(world,11,65,8),Zombie.class);
        for(var mob:List.of(first,second,third))store.put("magic.summons",mob.getUniqueId().toString(),owner.getUniqueId()+"|"+(System.currentTimeMillis()+60_000)+"|true");
        var summons=new MagicSummons(plugin,store,new ExpansionProtection(plugin),new MagicPassives(items),()->magic.getBoolean("pvp",true));services.add(summons);summons.tick(System.currentTimeMillis());
        for(var mob:List.of(first,second,third)){assertEquals(enemy,mob.getTarget());var event=new EntityTargetLivingEntityEvent(mob,first,EntityTargetEvent.TargetReason.CLOSEST_ENTITY);summons.target(event);assertTrue(event.isCancelled());var damage=new EntityDamageByEntityEvent(mob,first,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);summons.damage(damage);assertTrue(damage.isCancelled());}
    }
    @Test void disabledMagicPvpBlocksDarknessTargetAndDamageEvenWithWorldPvpEnabled(){
        magic.set("pvp",false);var system=new MagicSystem(plugin,items,n->magic,store,new ExpansionProtection(plugin));services.add(system);system.start();var mob=markedSkeleton(9);var victim=server.addPlayer();victim.teleport(new Location(world,10,65,8));
        var target=new EntityTargetLivingEntityEvent(mob,victim,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);server.getPluginManager().callEvent(target);assertTrue(target.isCancelled());
        var damage=new EntityDamageByEntityEvent(mob,victim,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);server.getPluginManager().callEvent(damage);assertTrue(damage.isCancelled());
    }
    @Test void existingDarknessPlayerTargetIsDroppedWhenLiveMagicPvpTurnsOff(){
        var mob=markedSkeleton(9);var victim=server.addPlayer();victim.teleport(new Location(world,10,65,8));store.put("magic.summons",mob.getUniqueId().toString(),owner.getUniqueId()+"|"+(System.currentTimeMillis()+60_000)+"|true");
        var summons=new MagicSummons(plugin,store,new ExpansionProtection(plugin),new MagicPassives(items),()->magic.getBoolean("pvp",true));services.add(summons);summons.tick(System.currentTimeMillis());assertEquals(victim,mob.getTarget());
        magic.set("pvp",false);summons.tick(System.currentTimeMillis());assertNull(mob.getTarget());
    }
    @Test void summonedDrownedNeverTargetOrHurtTheirSurvivalSummoner(){
        owner.setGameMode(org.bukkit.GameMode.SURVIVAL);var mob=world.spawn(new Location(world,9,65,8),Drowned.class);mob.getPersistentDataContainer().set(MagicSummons.MARK,PersistentDataType.STRING,owner.getUniqueId().toString());mob.getPersistentDataContainer().set(ExpansionItems.key("magic_summon_expires"),PersistentDataType.LONG,System.currentTimeMillis()+60_000);mob.getPersistentDataContainer().set(ExpansionItems.key("magic_summon_kind"),PersistentDataType.STRING,"water");
        var summons=new MagicSummons(plugin,store,new ExpansionProtection(plugin),new MagicPassives(items),()->magic.getBoolean("pvp",true));services.add(summons);
        var target=new EntityTargetLivingEntityEvent(mob,owner,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);summons.target(target);assertTrue(target.isCancelled(),"no targeting the owner");
        var retaliate=new EntityTargetLivingEntityEvent(mob,owner,EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);summons.target(retaliate);assertTrue(retaliate.isCancelled(),"no retaliation against the owner");
        var damage=new EntityDamageByEntityEvent(mob,owner,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);summons.damage(damage);assertTrue(damage.isCancelled(),"no damage to the owner");
        var zombie=world.spawn(new Location(world,10,65,9),org.bukkit.entity.Zombie.class);
        var hostile=new EntityTargetLivingEntityEvent(mob,zombie,EntityTargetEvent.TargetReason.CLOSEST_ENTITY);summons.target(hostile);assertFalse(hostile.isCancelled(),"hostile mobs are valid targets");
        var cow=world.spawn(new Location(world,10,65,10),org.bukkit.entity.Cow.class);
        var passive=new EntityTargetLivingEntityEvent(mob,cow,EntityTargetEvent.TargetReason.CLOSEST_ENTITY);summons.target(passive);assertTrue(passive.isCancelled(),"animals are left alone");
    }
    @Test void wallsFillAroundObstaclesAndRestoreExactly(){
        world.getChunkAt(1,0);var terrain=terrain();List<Block> wall=new ArrayList<>();
        for(int w=0;w<6;w++)for(int y=0;y<7;y++){Block b=world.getBlockAt(20+w,70+y,5);b.setType(Material.AIR);wall.add(b);}
        wall.get(0).setType(Material.SHORT_GRASS);wall.get(8).setType(Material.NETHERRACK);wall.get(15).setType(Material.WATER);
        assertTrue(terrain.change(owner,wall,Material.BLACKSTONE,0,true));
        assertEquals(Material.BLACKSTONE,wall.get(0).getType(),"grass is filled");
        assertEquals(Material.NETHERRACK,wall.get(8).getType(),"existing solid blocks are kept, not replaced");
        assertEquals(Material.WATER,wall.get(15).getType(),"fluids are never replaced");
        assertEquals(40,wall.stream().filter(b->b.getType()==Material.BLACKSTONE).count(),"6x7 wall minus the two kept blocks");
        terrain.pump(Long.MAX_VALUE,true);
        assertEquals(Material.SHORT_GRASS,wall.get(0).getType());assertEquals(Material.AIR,wall.get(1).getType());assertEquals(Material.NETHERRACK,wall.get(8).getType());
    }
    @Test void spellFireIsRemovedEvenAfterItAges(){
        assertTrue(MagicTerrainJournal.same("minecraft:fire[age=0,east=false,north=false,south=false,up=false,west=false]","minecraft:fire[age=7,east=false,north=false,south=false,up=false,west=false]"));
        assertFalse(MagicTerrainJournal.same("minecraft:fire[age=0]","minecraft:stone"));
        assertFalse(MagicTerrainJournal.same("minecraft:stone","minecraft:cobblestone"));
    }
    MagicTerrain terrain(){var terrain=new MagicTerrain(plugin,n->magic,store,new ExpansionProtection(plugin));services.add(terrain);return terrain;}
    Block excavate(MagicTerrain terrain,int x){var block=world.getBlockAt(x,64,3);block.setType(Material.STONE);assertTrue(terrain.change(owner,List.of(block),Material.AIR,0));return block;}
    BlockPlaceEvent place(Block block,BlockState replaced,boolean canBuild){return new BlockPlaceEvent(block,replaced,block.getRelative(BlockFace.DOWN),new ItemStack(Material.STONE),owner,canBuild,EquipmentSlot.HAND);}
    @Test void placementCancelledAfterOurListenerRetainsRestoration(){
        var terrain=terrain();var block=excavate(terrain,3);BlockState air=block.getState();block.setType(Material.STONE);var event=place(block,air,true);terrain.place(event);terrain.pump(Long.MAX_VALUE,false);assertTrue(store.entries("magic.terrain-quarantine").isEmpty());event.setCancelled(true);block.setType(Material.AIR);server.getScheduler().performTicks(1);terrain.pump(Long.MAX_VALUE,false);assertEquals(Material.STONE,block.getType());
    }
    @Test void placementWithCanBuildFalseRetainsRestoration(){
        var terrain=terrain();var block=excavate(terrain,3);BlockState air=block.getState();block.setType(Material.STONE);var event=place(block,air,false);terrain.place(event);block.setType(Material.AIR);server.getScheduler().performTicks(1);terrain.pump(Long.MAX_VALUE,false);assertEquals(Material.STONE,block.getType());
    }
    @Test void successfulSameStatePlacementRevokesRestorationOwnership(){
        var terrain=terrain();var block=excavate(terrain,3);var event=place(block,block.getState(),true);terrain.place(event);server.getScheduler().performTicks(1);terrain.pump(Long.MAX_VALUE,false);assertEquals(Material.AIR,block.getType());
    }
    @Test void successfulMultiPlacementRevokesAllOwnedCells(){
        var terrain=terrain();var first=excavate(terrain,3);var second=excavate(terrain,4);var event=new BlockMultiPlaceEvent(List.of(first.getState(),second.getState()),first.getRelative(BlockFace.DOWN),new ItemStack(Material.STONE),owner,true,EquipmentSlot.HAND);terrain.place(event);server.getScheduler().performTicks(1);terrain.pump(Long.MAX_VALUE,false);assertEquals(Material.AIR,first.getType());assertEquals(Material.AIR,second.getType());
    }
    @Test void creativeFlightDoesNotBecomePermanentInSurvivalOrAdventure(){
        var flights=new MagicFlights(store,n->magic);for(GameMode target:List.of(GameMode.SURVIVAL,GameMode.ADVENTURE)){
            owner.setGameMode(GameMode.CREATIVE);owner.setAllowFlight(true);assertTrue(flights.grant(owner,20));owner.setGameMode(target);flights.restore(owner);assertFalse(owner.getAllowFlight(),target.name());assertFalse(owner.isFlying());
            // A stale player save must not resurrect the creative baseline in a survival mode.
            owner.setAllowFlight(true);new MagicFlights(store,n->magic).reconcile(owner);assertFalse(owner.getAllowFlight(),"restart "+target.name());
        }
    }
    @Test void currentNativeOrExternalFlightAuthorizationSurvivesCreativeBaselineCleanup(){
        var flights=new MagicFlights(store,n->magic);for(GameMode mode:List.of(GameMode.CREATIVE,GameMode.SPECTATOR)){owner.setGameMode(GameMode.SURVIVAL);owner.setAllowFlight(false);assertTrue(flights.grant(owner,20));owner.setGameMode(mode);flights.restore(owner);assertTrue(owner.getAllowFlight(),mode.name());}
        owner.setGameMode(GameMode.CREATIVE);owner.setAllowFlight(true);assertTrue(flights.grant(owner,20));owner.setGameMode(GameMode.SURVIVAL);owner.addAttachment(plugin,"essentials.fly",true);flights.restore(owner);assertTrue(owner.getAllowFlight());
    }
}
