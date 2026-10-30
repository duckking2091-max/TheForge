package dev.duckmc.blastforge;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionMenuTest {
    ServerMock server; PlayerMock player; ExtractionMenu menu; ItemStack sword;org.bukkit.plugin.java.JavaPlugin plugin;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder;
    @BeforeEach void setup() {
        server=MockBukkit.mock(); player=server.addPlayer();
        plugin=MockBukkit.createMockPlugin(); menu=new ExtractionMenu(plugin,b->true);
        server.getPluginManager().registerEvents(menu,plugin);
        sword=new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS,5); sword.addEnchantment(Enchantment.LOOTING,3);
        sword.addEnchantment(Enchantment.UNBREAKING,3);
    }
    @AfterEach void cleanup(){menu.shutdown();MockBukkit.unmock();}
    void open(int books){menu.open(player,player.getLocation(),sword,new ItemStack(Material.BOOK,books));}
    void click(int slot){
        var e=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        menu.click(e); assertTrue(e.isCancelled());
    }
    int count(Material material){
        int n=0;for(var item:player.getInventory().getContents())if(item!=null&&item.getType()==material)n+=item.getAmount();return n;
    }
    @Test void closingReturnsBothInputsOnceWithoutExtraction(){
        open(4); click(0); player.closeInventory(); menu.shutdown();
        assertEquals(1,count(Material.DIAMOND_SWORD)); assertEquals(4,count(Material.BOOK));
        assertEquals(0,count(Material.ENCHANTED_BOOK));
        assertTrue(player.getInventory().containsAtLeast(sword,1));
    }
    @Test void selectAllAndConfirmConsumesOnlyOneBookAndCannotDoubleClaim(){
        open(4);click(45);click(53);click(53);server.getScheduler().performOneTick();
        assertEquals(1,count(Material.DIAMOND_SWORD));assertEquals(3,count(Material.BOOK));
        assertEquals(1,count(Material.ENCHANTED_BOOK));
        for(var item:player.getInventory().getContents())if(item!=null){
            if(item.getType()==Material.DIAMOND_SWORD)assertTrue(item.getEnchantments().isEmpty());
            if(item.getType()==Material.ENCHANTED_BOOK)assertEquals(3,((EnchantmentStorageMeta)item.getItemMeta()).getStoredEnchants().size());
        }
        assertEquals(0,player.getTotalExperience());
    }
    @Test void noSelectionCannotConsumeInputs(){
        open(1);click(53);server.getScheduler().performOneTick();player.closeInventory();
        assertEquals(1,count(Material.BOOK));assertTrue(player.getInventory().containsAtLeast(sword,1));
        assertEquals(0,count(Material.ENCHANTED_BOOK));
    }
    @Test void closingBeforeScheduledConfirmCancelsIt(){
        open(1);click(45);click(53);player.closeInventory();server.getScheduler().performOneTick();
        assertEquals(1,count(Material.BOOK));assertTrue(player.getInventory().containsAtLeast(sword,1));
        assertEquals(0,count(Material.ENCHANTED_BOOK));
    }
    @Test void selectionCanBeClearedAndIndividualEnchantmentsToggled(){
        open(1);click(45);click(46);click(0);click(0);click(1);click(53);
        server.getScheduler().performOneTick();
        for(var item:player.getInventory().getContents())if(item!=null){
            if(item.getType()==Material.DIAMOND_SWORD)assertEquals(2,item.getEnchantments().size());
            if(item.getType()==Material.ENCHANTED_BOOK)assertEquals(1,((EnchantmentStorageMeta)item.getItemMeta()).getStoredEnchants().size());
        }
        assertEquals(1,count(Material.ENCHANTED_BOOK));
    }
    @Test void shiftClicksAndNumberKeysCannotTakeDisplayBooks(){
        open(1);
        for(var click:new ClickType[]{ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.DROP}){
            var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,click,InventoryAction.PICKUP_ALL);
            menu.click(event);assertTrue(event.isCancelled());
        }
        player.closeInventory();assertEquals(0,count(Material.ENCHANTED_BOOK));
        assertTrue(player.getInventory().containsAtLeast(sword,1));
    }
    @Test void configuredExtractionCostIsChargedOnceOnConfirmation(){
        menu.experienceCost(()->3);player.setLevel(10);
        open(1);click(45);click(53);click(53);server.getScheduler().performOneTick();
        assertEquals(7,player.getLevel());assertEquals(1,count(Material.ENCHANTED_BOOK));
    }
    @Test void insufficientExtractionExperienceKeepsInputsAndSelection(){
        menu.experienceCost(()->3);player.setLevel(2);
        open(1);click(45);click(53);server.getScheduler().performOneTick();
        assertEquals(2,player.getLevel());assertEquals(0,count(Material.ENCHANTED_BOOK));
        player.closeInventory();assertTrue(player.getInventory().containsAtLeast(sword,1));assertEquals(1,count(Material.BOOK));
    }
    @Test void shutdownBeforeHandoffScreenOpensReturnsInputsExactlyOnce(){
        menu.handoff(player,player.getLocation(),sword,new ItemStack(Material.BOOK,3),true,()->{});
        menu.shutdown();server.getScheduler().performOneTick();menu.shutdown();
        assertEquals(1,count(Material.DIAMOND_SWORD));assertEquals(3,count(Material.BOOK));assertEquals(0,count(Material.ENCHANTED_BOOK));
    }
    @Test void secondPendingSelectorCannotReplaceFirstEscrow(){
        menu.handoff(player,player.getLocation(),sword,new ItemStack(Material.BOOK),true,()->{});
        assertThrows(IllegalStateException.class,()->menu.handoff(player,player.getLocation(),sword,new ItemStack(Material.BOOK),true,()->fail("Must not release a second source")));
        menu.shutdown();server.getScheduler().performOneTick();assertEquals(1,count(Material.DIAMOND_SWORD));assertEquals(1,count(Material.BOOK));
    }
    @Test void failedSaveClosesAndClearsPreviewAndRetainsUncertainEscrow(){
        try(var store=new dev.duckmc.blastforge.expansion.PersistentStore(folder.resolve("extraction.db"))){
            menu.persistentReturns(new dev.duckmc.blastforge.expansion.Mailbox(plugin,store,p->{throw new IllegalStateException("save unavailable");}),store);
            menu.experienceCost(()->3);player.setLevel(10);open(1);click(45);click(53);click(53);server.getScheduler().performOneTick();
            assertEquals(7,player.getLevel());assertEquals(0,count(Material.ENCHANTED_BOOK));assertEquals(0,count(Material.DIAMOND_SWORD));
            assertTrue(store.get("extraction_session",player.getUniqueId().toString()).startsWith("U:3\n"));assertFalse(store.entries("quarantine").isEmpty());
            assertNotEquals("Choose enchantments to extract",player.getOpenInventory().getTitle());
            String first=store.entries("quarantine").values().iterator().next();
            open(1);click(45);click(53);server.getScheduler().performOneTick();
            assertEquals(4,player.getLevel());assertEquals(2,store.entries("quarantine").size());assertTrue(store.entries("quarantine").containsValue(first));menu.shutdown();
        }
    }
}
