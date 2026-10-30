package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MagicSystemTest {
    @TempDir Path directory;
    ServerMock server;PersistentStore store;ExpansionItems items;MagicSystem system;
    org.bukkit.plugin.java.JavaPlugin plugin;YamlConfiguration magic,scythe;
    @BeforeEach void setup(){
        server=MockBukkit.mock();plugin=MockBukkit.createMockPlugin();magic=new YamlConfiguration();scythe=new YamlConfiguration();
        magic.set("particle-intensity",0);items=new ExpansionItems(plugin,n->n.equals("scythe")?scythe:magic);
        store=new PersistentStore(directory.resolve("magic.sqlite"));
        system=new MagicSystem(plugin,items,n->n.equals("scythe")?scythe:magic,store,new ExpansionProtection(plugin));
    }
    @AfterEach void close(){system.close();store.close();MockBukkit.unmock();}
    @Test void inventoryBooksProtectFromFireDrowningAndFallButNestedAndOffhandRulesAreExact(){
        var p=server.addPlayer();p.getInventory().setItem(15,items.create("darkness_book"));
        for(var cause:new EntityDamageEvent.DamageCause[]{EntityDamageEvent.DamageCause.FIRE,EntityDamageEvent.DamageCause.LAVA,EntityDamageEvent.DamageCause.DROWNING,EntityDamageEvent.DamageCause.FALL}){
            var event=new EntityDamageEvent(p,cause,5);system.passiveDamage(event);assertTrue(event.isCancelled(),cause.name());
        }
        p.getInventory().clear();var event=new EntityDamageEvent(p,EntityDamageEvent.DamageCause.LAVA,5);system.passiveDamage(event);assertFalse(event.isCancelled());
        p.getInventory().setItemInOffHand(items.create("fire_book"));event=new EntityDamageEvent(p,EntityDamageEvent.DamageCause.FIRE,5);system.passiveDamage(event);assertTrue(event.isCancelled());
        event=new EntityDamageEvent(p,EntityDamageEvent.DamageCause.FALL,5);system.passiveDamage(event);assertFalse(event.isCancelled());
    }
    @Test void earthThirdAndOffhandDoNotChargeCooldownOrSuppressOrdinaryInteraction(){
        var p=server.addPlayer();p.setSneaking(true);p.getInventory().setItemInMainHand(items.create("earth_book"));
        var event=new PlayerInteractEvent(p,Action.LEFT_CLICK_AIR,p.getInventory().getItemInMainHand(),null,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
        system.interact(event);assertEquals(0,store.cooldown(p.getUniqueId(),"magic.earth_book.0"));
        p.getInventory().setItemInMainHand(new ItemStack(Material.STICK));p.getInventory().setItemInOffHand(items.create("wind_book"));
        event=new PlayerInteractEvent(p,Action.RIGHT_CLICK_AIR,p.getInventory().getItemInOffHand(),null,org.bukkit.block.BlockFace.UP,EquipmentSlot.OFF_HAND);
        system.interact(event);assertFalse(p.getAllowFlight());assertEquals(0,store.cooldown(p.getUniqueId(),"magic.wind_book.0"));
    }
    @Test void windFlightHasDurableLeaseAndCloseRestoresOriginalPermission(){
        var p=server.addPlayer();p.setOp(true);p.getWorld().getChunkAt(p.getLocation());p.getInventory().setItemInMainHand(items.create("wind_book"));
        assertTrue(p.hasPermission("blastforge.use"),"permission fixture");assertTrue(new ExpansionProtection(plugin).canAffect(p,p.getLocation()),"loaded authorized location fixture");
        assertTrue(system.activate(p,0));assertTrue(p.getAllowFlight());assertFalse(store.entries("magic.flight").isEmpty());
        assertFalse(system.activate(p,0)); // the same ability is on cooldown
        system.close();assertFalse(p.getAllowFlight());assertTrue(store.entries("magic.flight").isEmpty());
    }
    @Test void inactiveScytheCannotActivateAndPreviouslyAllowedFlightIsPreserved(){
        var p=server.addPlayer();p.setOp(true);p.getWorld().getChunkAt(p.getLocation());p.setAllowFlight(true);UUID id=UUID.randomUUID();var weapon=items.create("legendary_scythe");
        var meta=weapon.getItemMeta();meta.getPersistentDataContainer().set(ExpansionItems.key("scythe_uuid"),PersistentDataType.STRING,id.toString());weapon.setItemMeta(meta);
        p.getInventory().setItemInMainHand(weapon);assertFalse(system.activate(p,1));
        assertTrue(store.reserveScythe(id,1));store.activateScythe(id);assertTrue(new ExpansionProtection(plugin).canAffect(p,p.getLocation()),"loaded authorized location fixture");assertTrue(system.activate(p,1));
        system.close();assertTrue(p.getAllowFlight());assertFalse(p.isFlying());
    }
    @Test void temporaryTerrainRestoresOnlyOwnedBlocksAndSkipsCustomOreAndContainers(){
        var world=server.addSimpleWorld("earth");world.getChunkAt(0,0);var p=server.addPlayer();p.teleport(new Location(world,5,65,5));
        var block=world.getBlockAt(2,64,2);block.setType(Material.STONE);
        var terrain=new MagicTerrain(plugin,n->magic,store,new ExpansionProtection(plugin));
        assertTrue(new ExpansionProtection(plugin).canAffect(p,block.getLocation()),"loaded authorized location fixture");
        assertTrue(terrain.change(p,java.util.List.of(block),Material.AIR,0));assertEquals(Material.AIR,block.getType());
        terrain.pump(System.currentTimeMillis()+1_000,false);assertEquals(Material.STONE,block.getType());
        assertTrue(terrain.change(p,java.util.List.of(block),Material.AIR,0));terrain.invalidate(block);block.setType(Material.AIR);
        terrain.pump(System.currentTimeMillis()+1_000,false);assertEquals(Material.AIR,block.getType());
        block.setType(Material.CHEST);assertFalse(terrain.change(p,java.util.List.of(block),Material.AIR,0));
        block.setType(Material.STONE);store.put("material_ore:"+world.getUID()+":0:0","2:64:2","siftie");assertFalse(terrain.change(p,java.util.List.of(block),Material.AIR,0));
        terrain.close();
    }
    @Test void wallCooldownBlocksASecondWallAndTemporaryStoneCannotBeHarvested(){
        var world=server.addSimpleWorld("wall");world.getChunkAt(0,0);var p=server.addPlayer();p.setOp(true);p.teleport(new Location(world,8,65,8));p.getInventory().setItemInMainHand(items.create("earth_book"));
        assertTrue(system.activate(p,1));assertFalse(system.activate(p,1));assertTrue(store.cooldown(p.getUniqueId(),"magic.earth_book.1")>System.currentTimeMillis());
        BlockBreakEvent breakEvent=null;
        for(int x=5;x<=11;x++)for(int z=5;z<=11;z++){var block=world.getBlockAt(x,p.getLocation().getBlockY(),z);if(block.getType()==Material.STONE){breakEvent=new BlockBreakEvent(block,p);server.getPluginManager().callEvent(breakEvent);}}
        // Direct terrain instances expose their guard independently of global listener registration.
        var terrain=new MagicTerrain(plugin,n->magic,store,new ExpansionProtection(plugin));
        assertNotNull(breakEvent);terrain.broken(breakEvent);assertTrue(breakEvent.isCancelled());assertFalse(breakEvent.isDropItems());terrain.close();
    }
    @Test void missingChunkOwnershipTokenQuarantinesTerrainInsteadOfOverwritingIt(){
        var world=server.addSimpleWorld("recovery");world.getChunkAt(0,0);var p=server.addPlayer();p.teleport(new Location(world,5,65,5));var b=world.getBlockAt(2,64,2);b.setType(Material.STONE);
        var terrain=new MagicTerrain(plugin,n->magic,store,new ExpansionProtection(plugin));assertTrue(terrain.change(p,java.util.List.of(b),Material.AIR,0));
        b.getChunk().getPersistentDataContainer().remove(ExpansionItems.key("magic_terrain_2_64_2"));terrain.pump(Long.MAX_VALUE,false);
        assertEquals(Material.AIR,b.getType());assertFalse(store.entries("magic.terrain-quarantine").isEmpty());terrain.close();
    }
    @Test void terrainWorkBudgetIsSharedAcrossCastsInTheSameServerTick(){
        magic.set("temporary-blocks.work-per-tick",2);var world=server.addSimpleWorld("budget");world.getChunkAt(0,0);var p=server.addPlayer();p.teleport(new Location(world,8,65,8));
        var a=world.getBlockAt(2,64,2);var b=world.getBlockAt(3,64,2);var c=world.getBlockAt(4,64,2);a.setType(Material.STONE);b.setType(Material.STONE);c.setType(Material.STONE);
        var terrain=new MagicTerrain(plugin,n->magic,store,new ExpansionProtection(plugin));assertTrue(terrain.change(p,java.util.List.of(a,b,c),Material.AIR,10));
        assertEquals(Material.STONE,c.getType());assertFalse(terrain.change(p,java.util.List.of(c),Material.AIR,10));assertEquals(Material.STONE,c.getType());terrain.close();
    }
}
