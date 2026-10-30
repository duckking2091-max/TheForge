package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import static org.junit.jupiter.api.Assertions.*;

class StationVisualsTest {
    ServerMock server;StationVisuals system;
    @BeforeEach void setup(){server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();var cfg=new YamlConfiguration();system=new StationVisuals(plugin,new ExpansionItems(plugin,n->cfg),n->cfg);}
    @AfterEach void teardown(){system.close();MockBukkit.unmock();}
    @Test void onlyMarkedAnvilsProduceTwoIdempotentPartsAndRemovalCleansThem(){
        var world=new VisualTestWorld(server,"world");var block=world.getBlockAt(1,64,2);block.setType(Material.ANVIL);system.placed(block);assertEquals(0,world.getEntitiesByClass(ItemDisplay.class).size());
        block.getChunk().getPersistentDataContainer().set(NamespacedKey.fromString("blastforge:station_1_64_2"),PersistentDataType.BYTE,(byte)1);
        system.placed(block);system.placed(block);assertEquals(2,world.getEntitiesByClass(ItemDisplay.class).size());
        for(var entity:world.getEntitiesByClass(ItemDisplay.class))assertFalse(entity.isPersistent());
        system.removed(block);assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
    }
    @Test void hearthBrightensWhileForgingFlashesAndReturnsToIdle(){
        var world=new VisualTestWorld(server,"heat");var block=world.getBlockAt(1,64,2);block.setType(Material.ANVIL);
        block.getChunk().getPersistentDataContainer().set(NamespacedKey.fromString("blastforge:station_1_64_2"),PersistentDataType.BYTE,(byte)1);
        system.placed(block);system.start();
        ItemDisplay core=world.getEntitiesByClass(ItemDisplay.class).stream().filter(d->d.getBrightness()!=null).findFirst().orElseThrow();
        assertEquals(6,core.getBrightness().getBlockLight());
        system.heat(block.getLocation(),60);server.getScheduler().performTicks(55);assertTrue(core.getBrightness().getBlockLight()>=12,"hot near the end");
        system.heat(block.getLocation(),0);server.getScheduler().performTicks(2);assertEquals(15,core.getBrightness().getBlockLight());
        server.getScheduler().performTicks(12);assertEquals(6,core.getBrightness().getBlockLight());
        system.heat(block.getLocation(),60);server.getScheduler().performTicks(10);system.heat(block.getLocation(),-1);assertEquals(6,core.getBrightness().getBlockLight());
    }
    @Test void queuedPlacementCannotCreateOrphanDisplaysAfterClose(){
        var world=new VisualTestWorld(server,"late");var block=world.getBlockAt(1,64,2);block.setType(Material.ANVIL);
        block.getChunk().getPersistentDataContainer().set(NamespacedKey.fromString("blastforge:station_1_64_2"),PersistentDataType.BYTE,(byte)1);
        var player=server.addPlayer();var event=new org.bukkit.event.block.BlockPlaceEvent(block,block.getState(),block.getRelative(0,-1,0),new org.bukkit.inventory.ItemStack(Material.ANVIL),player,true,org.bukkit.inventory.EquipmentSlot.HAND);
        system.place(event);system.close();server.getScheduler().performTicks(2);assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
    }
}
