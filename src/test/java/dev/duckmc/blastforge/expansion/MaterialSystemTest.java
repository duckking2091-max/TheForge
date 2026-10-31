package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MaterialSystemTest {
    @TempDir Path directory;
    ServerMock server; ExpansionItems items; MaterialSystem system; PersistentStore state; YamlConfiguration settings;
    @BeforeEach void setup(){server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();settings=new YamlConfiguration();settings.set("generation.siftie.min-y",0);settings.set("generation.siftie.max-y",0);settings.set("generation.siftie.attempts",1);settings.set("generation.siftie.chance",1);settings.set("generation.siftie.vein-size",1);settings.set("ore-visuals.enabled",false);items=new ExpansionItems(plugin,n->settings);state=new PersistentStore(directory.resolve("materials.sqlite"));system=new MaterialSystem(plugin,items,n->settings,state);system.start();}
    @AfterEach void teardown(){system.close();state.close();MockBukkit.unmock();}
    @Test void oldChunksNeverGenerateAndNewGenerationPersistsWithoutRepeating(){
        var world=server.addSimpleWorld("overworld");var chunk=world.getChunkAt(0,0);for(int x=0;x<16;x++)for(int z=0;z<16;z++)world.getBlockAt(x,0,z).setType(Material.DEEPSLATE);
        system.chunkLoaded(new ChunkLoadEvent(chunk,false));server.getScheduler().performTicks(2);assertEquals(0,count(world));
        system.chunkLoaded(new ChunkLoadEvent(chunk,true));server.getScheduler().performTicks(2);assertEquals(1,count(world));
        system.chunkLoaded(new ChunkLoadEvent(chunk,true));server.getScheduler().performTicks(2);assertEquals(1,count(world));
        system.close();system=new MaterialSystem(MockBukkit.createMockPlugin("Other"),items,n->settings,state);assertEquals(1,count(world));
    }
    @Test void placedCustomOreOwnsOnlyItsCoordinateAndEnforcesTier(){
        var world=server.addSimpleWorld("world");var block=world.getBlockAt(2,10,2);block.setType(Material.ANCIENT_DEBRIS);var player=server.addPlayer();
        BlockPlaceEvent place=new BlockPlaceEvent(block,block.getState(),block.getRelative(0,-1,0),items.create("enderite_ore"),player,true,org.bukkit.inventory.EquipmentSlot.HAND);
        system.place(place);assertTrue(system.isOre(block));assertFalse(system.canMine(block,new ItemStack(Material.DIAMOND_PICKAXE)));assertTrue(system.canMine(block,new ItemStack(Material.NETHERITE_PICKAXE)));assertTrue(system.canMine(block,items.create("siftie_pickaxe")));
        var wrong=new BlockBreakEvent(block,player);system.guard(wrong);assertFalse(wrong.isCancelled(),"a weak pickaxe breaks the ore but gets nothing");
        block.setType(Material.STONE);assertFalse(system.isOre(block));block.setType(Material.ANCIENT_DEBRIS);assertFalse(system.isOre(block));
    }
    @Test void ordinaryCarrierHasNoCustomIdentity(){var world=server.addSimpleWorld("world");var block=world.getBlockAt(0,20,0);block.setType(Material.DEEPSLATE_DIAMOND_ORE);assertFalse(system.isOre(block));assertTrue(system.canMine(block,new ItemStack(Material.WOODEN_PICKAXE)));}
    @Test void wardenScrapsAreGrantedOnlyOnceEvenWhenDeathEventRepeats(){
        var world=server.addSimpleWorld("world");var warden=world.spawn(new Location(world,1,20,1),org.bukkit.entity.Warden.class);
        var drops=new java.util.ArrayList<ItemStack>();var event=new org.bukkit.event.entity.EntityDeathEvent(warden,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),drops);system.warden(event);assertEquals(1,drops.size());assertEquals("wardenite_scrap",items.id(drops.getFirst()));assertEquals(1,drops.getFirst().getAmount());system.warden(event);assertEquals(1,drops.size());
    }
    @Test void armorPerkAppliesOnceAndSupportsInheritanceAndFullSetConfiguration(){
        var player=server.addPlayer();player.getInventory().setHelmet(items.create("wardenite_helmet"));assertTrue(system.perk(player,"enderite"));assertTrue(system.perk(player,"wardenite"));
        settings.set("armor-perks.inherit",false);assertFalse(system.perk(player,"enderite"));settings.set("armor-perks.activation","full-set");assertFalse(system.perk(player,"wardenite"));
        player.getInventory().setChestplate(items.create("wardenite_chestplate"));player.getInventory().setLeggings(items.create("wardenite_leggings"));player.getInventory().setBoots(items.create("wardenite_boots"));assertTrue(system.perk(player,"wardenite"));
    }
    @Test void cancelledBreakDoesNotConsumeOreOrIssueDrops(){
        var world=server.addSimpleWorld("world");var player=server.addPlayer();var block=world.getBlockAt(2,10,2);block.setType(Material.DEEPSLATE_DIAMOND_ORE);
        system.place(new BlockPlaceEvent(block,block.getState(),block.getRelative(0,-1,0),items.create("siftie_ore"),player,true,org.bukkit.inventory.EquipmentSlot.HAND));
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));var event=new BlockBreakEvent(block,player);system.breakOre(event);event.setCancelled(true);server.getScheduler().performTicks(2);assertTrue(system.isOre(block));assertTrue(world.getEntitiesByClass(org.bukkit.entity.Item.class).isEmpty());
    }
    @Test void silkTouchHarvestDropsOneOwnedOreAfterTheActualBreak(){
        var world=server.addSimpleWorld("world");var player=server.addPlayer();var block=world.getBlockAt(2,10,2);block.setType(Material.DEEPSLATE_DIAMOND_ORE);
        system.place(new BlockPlaceEvent(block,block.getState(),block.getRelative(0,-1,0),items.create("siftie_ore"),player,true,org.bukkit.inventory.EquipmentSlot.HAND));
        var pick=new ItemStack(Material.DIAMOND_PICKAXE);pick.addEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH,1);player.getInventory().setItemInMainHand(pick);
        var event=new BlockBreakEvent(block,player);system.breakOre(event);block.setType(Material.AIR);server.getScheduler().performTicks(2);assertFalse(system.isOre(block));
        var drops=world.getEntitiesByClass(org.bukkit.entity.Item.class);assertEquals(1,drops.size());assertEquals("siftie_ore",items.id(drops.iterator().next().getItemStack()));
    }    int count(org.bukkit.World world){int result=0;for(int x=0;x<16;x++)for(int z=0;z<16;z++)if(system.isOre(world.getBlockAt(x,0,z)))result++;return result;}
    @Test void miningMultipliersGiveTheRequestedTimes(){
        var player=server.addPlayer();
        // ticks = 1 / (speed * multiplier / hardness / 30); netherite + Efficiency V speed is 35.
        double siftie=system.multiplier("siftie",player,new ItemStack(Material.NETHERITE_PICKAXE));assertEquals(200,Math.round(4.5*30/(35*siftie)));
        double enderite=system.multiplier("enderite",player,new ItemStack(Material.NETHERITE_PICKAXE));assertEquals(400,Math.round(30*30/(35*enderite)));
    }
    @Test void newEnderiteSitsOnEndStoneAndOldAncientDebrisStillCounts(){
        var world=server.addSimpleWorld("end2");var player=server.addPlayer();
        for(var type:new org.bukkit.Material[]{Material.END_STONE,Material.ANCIENT_DEBRIS}){
            var block=world.getBlockAt(4,10,4);block.setType(type);
            system.place(new BlockPlaceEvent(block,block.getState(),block.getRelative(0,-1,0),items.create("enderite_ore"),player,true,org.bukkit.inventory.EquipmentSlot.HAND));
            assertTrue(system.isOre(block),type+" carrier");block.setType(Material.STONE);assertFalse(system.isOre(block));
        }
        // End stone (hardness 3) gets its own multiplier so it still takes 20 s with netherite Efficiency V.
        double m=system.multiplier("enderite",player,new ItemStack(Material.NETHERITE_PICKAXE),3);assertEquals(400,Math.round(3*30/(35*m)));
    }
}
