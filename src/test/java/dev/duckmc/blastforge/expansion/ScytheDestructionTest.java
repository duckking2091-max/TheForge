package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScytheDestructionTest {
    @TempDir Path directory;ServerMock server;PersistentStore store;ScytheLifecycle scythes;World world;YamlConfiguration scythe=new YamlConfiguration();
    @BeforeEach void setup(){server=MockBukkit.mock();world=server.addSimpleWorld("w");var plugin=MockBukkit.createMockPlugin();store=new PersistentStore(directory.resolve("s.db"));
        scythe.set("maximum-scythes",1);var items=new ExpansionItems(plugin,n->new YamlConfiguration());scythes=new ScytheLifecycle(plugin,items,n->n.equals("scythe")?scythe:new YamlConfiguration(),store);}
    @AfterEach void teardown(){store.close();MockBukkit.unmock();}
    Item dropped(){var stack=scythes.issue();assertNotNull(stack);assertFalse(scythes.available(),"one scythe locks the ritual");return world.dropItem(new Location(world,0,64,0),stack);}
    @Test void voidLavaFireCactusExplosionAndDespawnFreeTheLimit(){
        for(var cause:List.of(EntityRemoveEvent.Cause.OUT_OF_WORLD,EntityRemoveEvent.Cause.DEATH,EntityRemoveEvent.Cause.EXPLODE,EntityRemoveEvent.Cause.DESPAWN)){
            Item item=dropped();scythes.removed(new EntityRemoveEvent(item,cause));assertTrue(scythes.available(),cause+" destroys it and reopens the ritual");}
    }
    @Test void unloadPickupQuitAndHoppersKeepItCounted(){
        Item item=dropped();
        for(var cause:List.of(EntityRemoveEvent.Cause.UNLOAD,EntityRemoveEvent.Cause.PICKUP,EntityRemoveEvent.Cause.PLAYER_QUIT,EntityRemoveEvent.Cause.ENTER_BLOCK,EntityRemoveEvent.Cause.MERGE,EntityRemoveEvent.Cause.PLUGIN,EntityRemoveEvent.Cause.DISCARD)){
            scythes.removed(new EntityRemoveEvent(item,cause));assertFalse(scythes.available(),cause+" must not free the limit");}
    }
    @Test void pluginRemovalCountsOnlyWhenAllowed(){scythe.set("plugin-removal-destroys",true);Item item=dropped();scythes.removed(new EntityRemoveEvent(item,EntityRemoveEvent.Cause.PLUGIN));assertTrue(scythes.available());}
    @Test void registrySurvivesRestartAndAdminCanDestroyAndRestore(){
        var stack=scythes.issue();UUID id=scythes.identity(stack);store.close();store=new PersistentStore(directory.resolve("s.db"));
        var plugin=MockBukkit.createMockPlugin();scythes=new ScytheLifecycle(plugin,new ExpansionItems(plugin,n->new YamlConfiguration()),n->n.equals("scythe")?scythe:new YamlConfiguration(),store);
        assertFalse(scythes.available(),"still counted after a restart");
        assertTrue(scythes.destroy(id,"admin"));assertTrue(scythes.available());assertFalse(scythes.destroy(id,"again"),"no double destruction");
        assertTrue(scythes.restore(id));assertFalse(scythes.available());
    }
    @Test void onlyOneOfTwoSimultaneousRitualsGetsTheSlot(){
        assertTrue(store.reserveScythe(UUID.randomUUID(),1));assertFalse(store.reserveScythe(UUID.randomUUID(),1),"the second ritual is refused");
    }
    @Test void aReplacementCanBeMadeOnlyAfterDestruction(){
        Item item=dropped();assertNull(scythes.issue(),"no second scythe while the first exists");
        scythes.removed(new EntityRemoveEvent(item,EntityRemoveEvent.Cause.OUT_OF_WORLD));assertNotNull(scythes.issue(),"a new ritual works after the void");
    }
}
