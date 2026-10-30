package dev.duckmc.blastforge;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.enchantments.Enchantment;
import static org.junit.jupiter.api.Assertions.*;

class ForgeStationsTest {
    ServerMock server;
    PlayerMock player;
    ForgeItems items;
    ForgeStations stations;
    Block block;
    @BeforeEach void setup() {
        server=MockBukkit.mock();
        var plugin=MockBukkit.createMockPlugin();
        player=server.addPlayer();
        block=server.addSimpleWorld("test").getBlockAt(10,70,10);
        items=new ForgeItems(plugin,Enchantment.EFFICIENCY);
        stations=new ForgeStations(plugin,items,null);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    BlockPlaceEvent place(ItemStack item) {
        var old=block.getState();
        block.setType(Material.ANVIL);
        var event=new BlockPlaceEvent(block,old,block.getRelative(0,-1,0),item,player,true,EquipmentSlot.HAND);
        stations.place(event);
        return event;
    }
    @Test void replacingBrokenStationWithOrdinaryAnvilNeverInheritsMarker() {
        place(items.anvil()); server.getScheduler().performOneTick();
        assertTrue(stations.isStation(block));
        stations.broken(new BlockBreakEvent(block,player));
        block.setType(Material.AIR);
        place(new ItemStack(Material.ANVIL));
        server.getScheduler().performOneTick();
        assertFalse(stations.isStation(block));
    }
    @Test void replacementCannotReceivePendingCustomPlacementMarker() {
        place(items.anvil());
        place(new ItemStack(Material.ANVIL));
        server.getScheduler().performOneTick();
        assertFalse(stations.isStation(block));
    }
    @Test void cancelledBreakRestoresStation() {
        place(items.anvil()); server.getScheduler().performOneTick();
        var event=new BlockBreakEvent(block,player);
        stations.broken(event); event.setCancelled(true);
        server.getScheduler().performOneTick();
        assertTrue(stations.isStation(block));
    }
    @Test void cancelledPlacementLeavesNoMarker() {
        var event=place(items.anvil()); event.setCancelled(true);
        block.setType(Material.AIR);
        server.getScheduler().performOneTick();
        block.setType(Material.ANVIL);
        assertFalse(stations.isStation(block));
    }
    @Test void sneakInteractionCannotOpenVanillaAnvil() {
        place(items.anvil()); server.getScheduler().performOneTick();
        player.setSneaking(true);
        var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,block,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
        stations.interact(event);
        assertEquals(Event.Result.DENY,event.useInteractedBlock());
    }
    @Test void recipeRequiresRealMarkedBookAndThreeDiamonds() {
        items.registerRecipe();
        var recipes=server.getRecipesFor(items.anvil());
        assertEquals(1,recipes.size());
        var recipe=(ShapedRecipe)recipes.getFirst();
        assertArrayEquals(new String[]{"IBI"," A ","DDD"},recipe.getShape());
        assertTrue(recipe.getChoiceMap().get('B').test(items.book()));
        ItemStack fake=new ItemStack(Material.ENCHANTED_BOOK);
        fake.editMeta(meta -> meta.displayName(items.book().getItemMeta().displayName()));
        assertFalse(recipe.getChoiceMap().get('B').test(fake));
    }
}
