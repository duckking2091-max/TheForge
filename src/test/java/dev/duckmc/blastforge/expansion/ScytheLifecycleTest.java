package dev.duckmc.blastforge.expansion;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.damage.*;
import org.bukkit.inventory.ItemStack;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScytheLifecycleTest {
    @TempDir Path folder;ServerMock server;PersistentStore store;ExpansionItems items;ScytheLifecycle system;World world;
    @BeforeEach void setup(){server=MockBukkit.mock();world=server.addSimpleWorld("world");var plugin=MockBukkit.createMockPlugin();var config=new YamlConfiguration();config.set("drops.bat-wing",1);config.set("drops.warden-heart",1);items=new ExpansionItems(plugin,n->config);store=new PersistentStore(folder.resolve("scythes.db"));system=new ScytheLifecycle(plugin,items,n->config,store);}
    @AfterEach void close(){system.close();store.close();MockBukkit.unmock();}
    EntityDeathEvent death(LivingEntity entity){return new EntityDeathEvent(entity,DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>());}
    @Test void durableDeathMarkerPreventsRewardAfterEntityMarkerRollsBack(){
        var bat=world.spawn(new Location(world,0,10,0),Bat.class);var event=death(bat);system.drops(event);assertEquals(1,event.getDrops().size());
        bat.getPersistentDataContainer().remove(new NamespacedKey("blastforge","rare_death_processed"));system.drops(event);assertEquals(1,event.getDrops().size());assertEquals("bat_wing",items.id(event.getDrops().getFirst()));
    }
    @Test void reservedCapacityBlocksDropsAndPermanentBreakReopensItExactlyOnce(){
        var scythe=system.issue();assertNotNull(scythe);assertNull(system.issue());
        var blocked=death(world.spawn(new Location(world,1,10,1),Warden.class));system.drops(blocked);assertTrue(blocked.getDrops().isEmpty());
        var player=server.addPlayer();var broken=new PlayerItemBreakEvent(player,scythe);system.broken(broken);system.broken(broken);assertEquals(0,store.occupiedScythes());
        var available=death(world.spawn(new Location(world,2,10,2),Warden.class));system.drops(available);assertEquals(1,available.getDrops().size());assertEquals("warden_heart",items.id(available.getDrops().getFirst()));
    }
    @Test void passiveTargetOptionNeverTargetsTamedPetsOrVillagers(){
        var player=server.addPlayer();var protection=new ExpansionProtection((org.bukkit.plugin.java.JavaPlugin)server.getPluginManager().getPlugins()[0]);
        var cow=world.spawn(new Location(world,0,10,0),Cow.class);assertFalse(protection.canDamage(player,cow));protection.passiveTargets(()->true);assertTrue(protection.canDamage(player,cow));
        var wolf=world.spawn(new Location(world,1,10,0),Wolf.class);wolf.setOwner(player);assertFalse(protection.canDamage(player,wolf));
        assertFalse(protection.canDamage(player,world.spawn(new Location(world,2,10,0),Villager.class)));
    }
}
