package dev.duckmc.blastforge.expansion;

import java.util.*;
import org.junit.jupiter.api.*;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class VillagerSystemTest {
    @TempDir Path directory;ServerMock server;PersistentStore store;VillagerSystem system;ExpansionItems items;YamlConfiguration cfg;
    @BeforeEach void setup(){server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();cfg=new YamlConfiguration();cfg.set("visuals.enabled",false);items=new ExpansionItems(plugin,n->cfg);store=new PersistentStore(directory.resolve("v.db"));system=new VillagerSystem(plugin,items,n->cfg,store,new ExpansionProtection(plugin));}
    @AfterEach void teardown(){system.close();store.close();MockBukkit.unmock();}
    @Test void tableClaimIsExclusiveAndSurvivesServiceRestart(){
        var world=new VisualTestWorld(server,"world");var table=world.getBlockAt(0,64,0);table.setType(Material.ENCHANTING_TABLE);
        var one=world.spawn(new Location(world,1,64,0),Villager.class);var two=world.spawn(new Location(world,2,64,0),Villager.class);one.setAdult();two.setAdult();
        assertTrue(system.claim(one,table));assertFalse(system.claim(two,table));assertEquals("magician",system.kind(one));
        assertEquals(2,one.getRecipes().size(),"a new Magician has exactly 2 trades");
        assertEquals(1,one.getRecipes().stream().filter(r->VillagerOffers.BOOKS.contains(items.id(r.getResult()))).count(),"one of them an elemental book");
        var before=one.getRecipes().stream().map(r->r.getResult().getType()+"x"+r.getResult().getAmount()+":"+items.id(r.getResult())).toList();
        system.close();var plugin=MockBukkit.createMockPlugin("Again");system=new VillagerSystem(plugin,items,n->cfg,store,new ExpansionProtection(plugin));assertFalse(system.claim(two,table));
        assertTrue(store.get("villagers",one.getUniqueId().toString()).contains("seed:"),"the plan is saved with the villager");
        one.setRecipes(List.of());system.reconcile(one);
        assertEquals(before,one.getRecipes().stream().map(r->r.getResult().getType()+"x"+r.getResult().getAmount()+":"+items.id(r.getResult())).toList(),"same trades after a restart");
    }
    @Test void tradedOrProfessionalVillagersCannotBeCaptured(){
        var world=new VisualTestWorld(server,"world");var table=world.getBlockAt(0,64,0);table.setType(Material.ENCHANTING_TABLE);var villager=world.spawn(new Location(world,1,64,0),Villager.class);villager.setAdult();villager.setProfession(Villager.Profession.FARMER);assertFalse(system.claim(villager,table));
        villager.setProfession(Villager.Profession.NONE);villager.setVillagerExperience(1);assertFalse(system.claim(villager,table));
    }
    @Test void onlyMarkedVillagersHaveVanillaTradeAcquisitionCancelled(){
        var world=new VisualTestWorld(server,"world");var table=world.getBlockAt(0,64,0);table.setType(Material.ENCHANTING_TABLE);var villager=world.spawn(new Location(world,1,64,0),Villager.class);villager.setAdult();
        var recipe=new org.bukkit.inventory.MerchantRecipe(new org.bukkit.inventory.ItemStack(Material.BREAD),10);
        var ordinary=new VillagerAcquireTradeEvent(villager,recipe);system.acquire(ordinary);assertFalse(ordinary.isCancelled());system.claim(villager,table);
        var marked=new VillagerAcquireTradeEvent(villager,recipe);system.acquire(marked);assertTrue(marked.isCancelled());
    }
    @Test void cancelledTradeDoesNotLockUntradedMagicianOrChangeUses(){
        var world=new VisualTestWorld(server,"world");var table=world.getBlockAt(0,64,0);table.setType(Material.ENCHANTING_TABLE);var villager=world.spawn(new Location(world,1,64,0),Villager.class);villager.setAdult();system.claim(villager,table);
        var player=server.addPlayer();var event=new io.papermc.paper.event.player.PlayerTradeEvent(player,villager,villager.getRecipe(0),true,true);event.setCancelled(true);system.traded(event);server.getScheduler().performTicks(2);
        assertFalse(system.locked(villager.getUniqueId()));assertEquals(0,villager.getRecipe(0).getUses());
    }
    @Test void expiredUnloadedMinerAllowsNewSafeSpawnProbe(){
        var world=new VisualTestWorld(server,"expiry");for(int cx=-1;cx<=0;cx++)for(int cz=-1;cz<=0;cz++)world.loadChunk(cx,cz);world.stopAtSurface=true;
        var player=server.addPlayer();player.teleport(new Location(world,0,64,0));cfg.set("miner.max-active",1);cfg.set("miner.spawn-radius",4);cfg.set("miner.min-spawn-distance",4);
        java.util.UUID expired=java.util.UUID.randomUUID();var state=new YamlConfiguration();state.set("id",expired.toString());state.set("token",java.util.UUID.randomUUID().toString());state.set("kind","miner");state.set("expires",1L);state.set("area",world.getUID()+":0:64:0");state.set("offers",VillagerOffers.encode(new VillagerOffers(items,cfg).miner(new java.util.Random(1))));store.put("villagers",expired.toString(),state.saveToString());
        system.close();var plugin=MockBukkit.createMockPlugin("Expiry");system=new VillagerSystem(plugin,items,n->cfg,store,new ExpansionProtection(plugin));
        assertThrows(VisualTestWorld.SurfaceProbe.class,()->system.spawnMiner(player),"Expired capacity and area must allow safety probing; terrain itself is a native boundary");assertNotNull(store.get("villagers",expired.toString()),"Keep expired ownership until its entity loads, so it can still be removed");
    }
    @Test void portalMovedMagicianDoesNotThrowOrRestockAcrossWorlds() throws Exception {
        var world=new VisualTestWorld(server,"home");world.loadChunk(0,0);var other=new VisualTestWorld(server,"other");var table=world.getBlockAt(0,64,0);table.setType(Material.ENCHANTING_TABLE);var merchant=world.spawn(new Location(world,1,64,0),Villager.class);merchant.setAdult();assertTrue(system.claim(merchant,table));assertTrue(merchant.teleport(new Location(other,1,64,0)));assertEquals(other,merchant.getWorld());assertTrue(world.isChunkLoaded(0,0));
        var field=VillagerSystem.class.getDeclaredField("states");field.setAccessible(true);Object state=((java.util.Map<?,?>)field.get(system)).get(merchant.getUniqueId());var method=VillagerSystem.class.getDeclaredMethod("work",Villager.class,state.getClass(),long.class);method.setAccessible(true);
        try{method.invoke(system,merchant,state,System.currentTimeMillis());}catch(java.lang.reflect.InvocationTargetException error){throw (Exception)error.getCause();}
        assertEquals("magician",system.kind(merchant));assertEquals(0,merchant.getRecipe(0).getUses());
    }
    @Test void nativeRecipeGetterStateSurvivesCaptureInsteadOfResettingUses(){
        var world=new VisualTestWorld(server,"nativeoffers");var table=world.getBlockAt(0,64,0);table.setType(Material.ENCHANTING_TABLE);var merchant=world.spawn(new Location(world,1,64,0),Villager.class);merchant.setAdult();assertTrue(system.claim(merchant,table));
        var base=merchant.getRecipe(0);var nativeRecipe=new org.bukkit.inventory.MerchantRecipe(base.getResult(),0,3,true,5,0){
            @Override public int getUses(){return 3;}
            @Override public int getMaxUses(){return 17;}
            @Override public int getDemand(){return 7;}
            @Override public int getSpecialPrice(){return -2;}
            @Override public int getVillagerExperience(){return 9;}
            @Override public float getPriceMultiplier(){return .25f;}
            @Override public boolean hasExperienceReward(){return false;}
            @Override public boolean shouldIgnoreDiscounts(){return false;}
        };nativeRecipe.setIngredients(base.getIngredients());merchant.setRecipe(0,nativeRecipe);
        system.unload(new org.bukkit.event.world.EntitiesUnloadEvent(world.getChunkAt(0,0),java.util.List.of(merchant)));
        var data=YamlConfiguration.loadConfiguration(new java.io.StringReader(store.get("villagers",merchant.getUniqueId().toString())));var restored=VillagerOffers.decode(data.getString("offers")).getFirst().recipe();
        assertEquals(3,restored.getUses());assertEquals(17,restored.getMaxUses());assertEquals(7,restored.getDemand());assertEquals(-2,restored.getSpecialPrice());assertEquals(9,restored.getVillagerExperience());assertEquals(.25f,restored.getPriceMultiplier());assertFalse(restored.hasExperienceReward());assertFalse(restored.shouldIgnoreDiscounts());
    }
}
