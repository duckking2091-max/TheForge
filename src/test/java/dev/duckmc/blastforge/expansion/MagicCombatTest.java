package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.damage.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MagicCombatTest {
    @TempDir Path directory;ServerMock server;PersistentStore store;ExpansionItems items;MagicSystem system;org.bukkit.plugin.java.JavaPlugin plugin;
    @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.createMockPlugin();items=new ExpansionItems(plugin,n->new YamlConfiguration());store=new PersistentStore(directory.resolve("state.db"));system=new MagicSystem(plugin,items,n->new YamlConfiguration(),store,new ExpansionProtection(plugin));}
    @AfterEach void close(){system.close();store.close();MockBukkit.unmock();}
    @Test void fireCookedDropsReplaceActualAnimalDropsOnceAndPreserveCounts(){
        var p=server.addPlayer();p.getInventory().setItem(30,items.create("fire_book"));var cow=p.getWorld().spawn(p.getLocation(),Cow.class);
        var drops=new ArrayList<ItemStack>(List.of(new ItemStack(Material.BEEF,3),new ItemStack(Material.LEATHER,2)));var source=DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(p).withDirectEntity(p).build();
        var e=new EntityDeathEvent(cow,source,drops,4);system.cookedDrops(e);system.cookedDrops(e);
        assertEquals(2,drops.size());assertEquals(Material.COOKED_BEEF,drops.getFirst().getType());assertEquals(3,drops.getFirst().getAmount());assertEquals(Material.LEATHER,drops.getLast().getType());
    }
    @Test void summonedMobsHaveNoDropsOrExperienceAndNeverTriggerCookedReward(){
        var p=server.addPlayer();p.getInventory().setItem(20,items.create("darkness_book"));var mob=p.getWorld().spawn(p.getLocation(),Drowned.class);
        mob.getPersistentDataContainer().set(MagicSummons.MARK,PersistentDataType.STRING,p.getUniqueId().toString());
        var drops=new ArrayList<ItemStack>(List.of(new ItemStack(Material.IRON_HELMET),new ItemStack(Material.ROTTEN_FLESH,3)));
        var e=new EntityDeathEvent(mob,DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(p).build(),drops,10);
        var summons=new MagicSummons(plugin,store,new ExpansionProtection(plugin),new MagicPassives(items),()->true);summons.death(e);system.cookedDrops(e);
        assertTrue(drops.isEmpty());assertEquals(0,e.getDroppedExp());summons.close();
    }
}
