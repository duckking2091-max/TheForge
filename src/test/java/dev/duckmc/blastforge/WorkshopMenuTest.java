package dev.duckmc.blastforge;
import dev.duckmc.blastforge.expansion.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopMenuTest {
    static final class SavingPlayer extends PlayerMock {
        SavingPlayer(ServerMock server){super(server,"ForgingTester",java.util.UUID.randomUUID());}
        @Override public void saveData(){}
    }
    @TempDir Path folder;
    ServerMock server;PlayerMock player;PersistentStore store;WorkshopMenu menu;YamlConfiguration config;
    @BeforeEach void setup(){
        server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();player=new SavingPlayer(server);server.addPlayer(player);player.addAttachment(plugin,"blastforge.use",true);
        config=new YamlConfiguration();store=new PersistentStore(folder.resolve("state.db"));var items=new ExpansionItems(plugin,n->config);
        menu=new WorkshopMenu(plugin,items,n->config,store,new Mailbox(plugin,store,org.bukkit.entity.Player::saveData),null,(p,l)->{},l->true);menu.start();menu.open(player,null);
    }
    @AfterEach void cleanup(){menu.shutdown();store.close();MockBukkit.unmock();}
    void click(int slot){var e=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);menu.click(e);assertTrue(e.isCancelled());}
    @Test void tabChangesKeepOneViewAndPreserveInputs(){
        var original=player.getOpenInventory().getTopInventory();player.setItemOnCursor(new ItemStack(Material.DIAMOND_SWORD));click(20);
        click(46);assertSame(original,player.getOpenInventory().getTopInventory());click(45);
        assertEquals(Material.DIAMOND_SWORD,original.getItem(20).getType());
    }
    @Test void insufficientXpDoesNotConsumeEquipment(){
        var base=new ItemStack(Material.NETHERITE_SWORD);var donor=new ItemStack(Material.IRON_SWORD);donor.addEnchantment(Enchantment.SMITE,4);
        player.setItemOnCursor(base);click(20);player.setItemOnCursor(donor);click(22);click(25);
        assertEquals(base,player.getOpenInventory().getTopInventory().getItem(20));assertEquals(donor,player.getOpenInventory().getTopInventory().getItem(22));
    }
    @Test void closeQueuesOrReturnsAllInputsAndCursorWithoutDuplication(){
        player.setItemOnCursor(new ItemStack(Material.DIAMOND_SWORD));click(20);player.setItemOnCursor(new ItemStack(Material.BOOK,3));
        player.closeInventory();
        int swords=0,books=0;for(var i:player.getInventory().getContents())if(i!=null){if(i.getType()==Material.DIAMOND_SWORD)swords+=i.getAmount();if(i.getType()==Material.BOOK)books+=i.getAmount();}
        assertEquals(1,swords);assertEquals(3,books);assertTrue(player.getItemOnCursor().getType().isAir());
    }
    @Test void repeatedConfirmProducesOneResultAndOneXpCharge(){
        var base=new ItemStack(Material.NETHERITE_SWORD);var donor=new ItemStack(Material.IRON_SWORD);donor.addEnchantment(Enchantment.SMITE,4);player.setLevel(10);
        player.setItemOnCursor(base);click(20);player.setItemOnCursor(donor);click(22);click(25);click(25);server.getScheduler().performTicks(3);
        int outputs=0;for(var item:player.getInventory().getContents())if(item!=null&&item.getType()==Material.NETHERITE_SWORD){outputs+=item.getAmount();assertEquals(4,item.getEnchantmentLevel(Enchantment.SMITE));}
        assertEquals(1,outputs);assertEquals(6,player.getLevel());
    }
    @Test void closingDuringUpgradeRefundsExactInputsAndXp(){
        var plugin=server.getPluginManager().getPlugins()[0];var items=new ExpansionItems((org.bukkit.plugin.java.JavaPlugin)plugin,n->new YamlConfiguration());
        player.setLevel(20);click(46);player.setItemOnCursor(new ItemStack(Material.DIAMOND_PICKAXE));click(21);player.setItemOnCursor(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));click(20);player.setItemOnCursor(new ItemStack(Material.NETHERITE_INGOT));click(22);click(25);
        assertEquals(15,player.getLevel());player.closeInventory();server.getScheduler().performTicks(2);assertEquals(20,player.getLevel());
        int equipment=0,templates=0,ingots=0;for(var item:player.getInventory().getContents())if(item!=null){if(item.getType()==Material.DIAMOND_PICKAXE){equipment++;assertEquals("",items.id(item));}if(item.getType()==Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)templates+=item.getAmount();if(item.getType()==Material.NETHERITE_INGOT)ingots+=item.getAmount();}
        assertEquals(1,equipment);assertEquals(1,templates);assertEquals(1,ingots);
    }
    @Test void startupReleasesNeverIssuedOrphanButKeepsActiveScythe(){
        var orphan=java.util.UUID.randomUUID();var active=java.util.UUID.randomUUID();
        assertTrue(store.reserveScythe(orphan,2));assertTrue(store.reserveScythe(active,2));store.activateScythe(active);
        menu.shutdown();menu.start();assertEquals(1,store.occupiedScythes());assertEquals("ACTIVE",store.scythes().get(active.toString()));assertFalse(store.scythes().containsKey(orphan.toString()));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={2,3})
    void successfulUpgradeHasExactlyThreeHammerStrikes(int seconds){
        config.set("forging.animation-seconds",seconds);var strikes=new java.util.concurrent.atomic.AtomicInteger();menu.strikeHandler((location,tier)->strikes.incrementAndGet());
        player.setLevel(20);click(46);player.setItemOnCursor(new ItemStack(Material.DIAMOND_PICKAXE));click(21);player.setItemOnCursor(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));click(20);player.setItemOnCursor(new ItemStack(Material.NETHERITE_INGOT));click(22);click(25);
        server.getScheduler().performTicks(seconds*20+3);assertEquals(3,strikes.get());assertEquals(15,player.getLevel());assertEquals(1,player.getInventory().all(Material.NETHERITE_PICKAXE).size());
    }
    @Test void ritualRingSlotsOnlyAcceptTheirOwnIngredient(){
        click(47);player.setItemOnCursor(new ItemStack(Material.NETHERITE_INGOT));click(5);
        assertEquals(Material.NETHERITE_INGOT,player.getItemOnCursor().getType(),"the top slot takes only the magic book");
        click(3);assertTrue(player.getItemOnCursor().getType().isAir(),"the last ring slot takes the Netherite ingot");
        assertEquals(Material.NETHERITE_INGOT,player.getOpenInventory().getTopInventory().getItem(3).getType());
        click(5);assertTrue(player.getItemOnCursor().getType().isAir(),"an ingredient hint can never be picked up");
    }
    @Test void clickingTheResultSlotForgesLikeVanilla(){
        var base=new ItemStack(Material.NETHERITE_SWORD);var donor=new ItemStack(Material.IRON_SWORD);donor.addEnchantment(Enchantment.SMITE,4);player.setLevel(10);
        player.setItemOnCursor(base);click(20);player.setItemOnCursor(donor);click(22);click(25);server.getScheduler().performTicks(3);
        assertEquals(1,player.getInventory().all(Material.NETHERITE_SWORD).size());assertEquals(6,player.getLevel());
    }
    @Test void forgeTabRepairsWithMaterialUsingOnlyWhatIsNeeded(){
        var sword=new ItemStack(Material.DIAMOND_SWORD);sword.editMeta(org.bukkit.inventory.meta.Damageable.class,m->m.setDamage(700));player.setLevel(10);
        player.setItemOnCursor(sword);click(20);player.setItemOnCursor(new ItemStack(Material.DIAMOND,10));click(22);click(25);server.getScheduler().performTicks(3);
        var repaired=player.getInventory().all(Material.DIAMOND_SWORD).values().iterator().next();
        assertEquals(0,((org.bukkit.inventory.meta.Damageable)repaired.getItemMeta()).getDamage());
        assertEquals(8,player.getOpenInventory().getTopInventory().getItem(22).getAmount(),"two diamonds (390 each) repair 700 damage; eight stay in the slot");
        assertEquals(8,player.getLevel());
    }
}
