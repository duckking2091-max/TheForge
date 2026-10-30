package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.*;
import org.bukkit.enchantments.Enchantment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VillagerOffersTest {
    ExpansionItems items;YamlConfiguration config;
    @BeforeEach void setup(){MockBukkit.mock();config=new YamlConfiguration();items=new ExpansionItems(MockBukkit.createMockPlugin(),n->config);}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void elementalUnlocksAreProgressiveAndResultsHaveActualIdentity(){
        var offers=new VillagerOffers(items,config);
        assertEquals(Set.of("fire_book"),books(offers.magician(1)));
        assertEquals(Set.of("fire_book","water_book"),books(offers.magician(2)));
        assertEquals(Set.of("fire_book","water_book","earth_book"),books(offers.magician(3)));
        assertEquals(Set.of("fire_book","water_book","earth_book","wind_book"),books(offers.magician(4)));
        config.set("magician.trades.water_book.level",1);config.set("magician.trades.water_book.emeralds",35);
        var water=new VillagerOffers(items,config).magician(1).stream().filter(o->o.id().equals("water_book")).findFirst().orElseThrow();
        assertEquals(35,water.recipe().getIngredients().getFirst().getAmount());assertEquals("water_book",items.id(water.recipe().getResult()));
    }
    @Test void randomMagiciansHaveExactlyTwoLevelOneTradesOneBookAndDiffer(){
        var offers=new VillagerOffers(items,config);var firstBooks=new HashSet<String>();var firstItems=new HashSet<String>();
        for(long seed=0;seed<200;seed++){var level1=offers.magician(1,seed);assertEquals(2,level1.size(),"exactly 2 trades at level 1");
            var ids=level1.stream().map(VillagerOffers.Offer::id).toList();assertEquals(1,ids.stream().filter(VillagerOffers.BOOKS::contains).count(),"1 elemental book + 1 other");
            assertFalse(ids.contains("echo_shard"),"echo shards stay at level 5");
            firstBooks.addAll(ids.stream().filter(VillagerOffers.BOOKS::contains).toList());firstItems.addAll(ids.stream().filter(id->!VillagerOffers.BOOKS.contains(id)).toList());}
        assertEquals(Set.copyOf(VillagerOffers.BOOKS),firstBooks,"any of the four books can come first, not always Fire");
        assertTrue(firstItems.size()>=4,"the extra trade varies: "+firstItems);
    }
    @Test void plansAreStableGrowWithLevelsAndNeverSellDarkness(){
        var offers=new VillagerOffers(items,config);
        for(long seed=0;seed<200;seed++){
            assertEquals(offers.magician(5,seed).stream().map(VillagerOffers.Offer::id).toList(),offers.magician(5,seed).stream().map(VillagerOffers.Offer::id).toList(),"same seed, same plan");
            List<String> previous=List.of();
            for(int level=1;level<=5;level++){var ids=offers.magician(level,seed).stream().map(VillagerOffers.Offer::id).toList();
                assertEquals(2*level,ids.size(),"2 more per level");assertTrue(ids.containsAll(previous),"levelling only adds");assertEquals(ids.size(),Set.copyOf(ids).size(),"no duplicates");
                assertFalse(ids.contains("darkness_book"));previous=ids;}
            assertTrue(offers.magician(4,seed).stream().map(VillagerOffers.Offer::id).toList().containsAll(VillagerOffers.BOOKS),"all four books by level 4");
        }
    }
    @Test void randomTradesKeepTheirConfiguredPricesAndCanBeConfigured(){
        config.set("magician.trades.wind_book.emeralds",41);config.set("magician.random-trades.trades-per-level",List.of(3,2,2,2,1));
        var offers=new VillagerOffers(items,config);
        for(long seed=0;seed<50;seed++){assertEquals(3,offers.magician(1,seed).size());
            offers.magician(5,seed).stream().filter(o->o.id().equals("wind_book")).forEach(o->assertEquals(41,o.recipe().getIngredients().getFirst().getAmount()));}
    }
    Set<String> books(List<VillagerOffers.Offer> list){var set=new HashSet<String>();for(var o:list)if(!items.id(o.recipe().getResult()).isEmpty())set.add(items.id(o.recipe().getResult()));return set;}
    @Test void minerAlwaysOffersExactForgingBookForEmeraldsAndOrdinaryAnvil(){
        var list=new VillagerOffers(items,config).miner(new Random(4));
        var book=list.stream().filter(o->o.id().equals("forging_book")).findFirst().orElseThrow();
        assertEquals("forging_book",items.id(book.recipe().getResult()));
        assertEquals(Material.EMERALD,book.recipe().getIngredients().get(0).getType());assertEquals(30,book.recipe().getIngredients().get(0).getAmount());
        assertEquals(Material.ANVIL,book.recipe().getIngredients().get(1).getType());assertEquals(1,book.recipe().getIngredients().get(1).getAmount());
        assertTrue(VillagerOffers.ordinary(new ItemStack(Material.ANVIL),items));assertFalse(VillagerOffers.ordinary(items.create("enchanting_anvil"),items));
        assertFalse(VillagerOffers.ordinary(items.create("siftie_pickaxe"),items));
    }
    @Test void everyMinerHasNineMineralsTwoPickaxesAndTheBook(){
        var bulk=Set.of(Material.RAW_IRON,Material.IRON_INGOT,Material.RAW_GOLD,Material.GOLD_INGOT,Material.RAW_COPPER,Material.COPPER_INGOT);
        for(int seed=0;seed<300;seed++){
            var list=new VillagerOffers(items,config).miner(new Random(seed));
            assertEquals(12,list.size(),"seed "+seed);
            var minerals=list.stream().filter(o->o.id().startsWith("buy_")||o.id().startsWith("sell_")).toList();
            var picks=list.stream().filter(o->o.id().startsWith("pickaxe_")).toList();
            assertEquals(9,minerals.size());assertEquals(2,picks.size());assertEquals(1,list.stream().filter(o->o.id().equals("forging_book")).count());
            long buys=minerals.stream().filter(o->o.id().startsWith("buy_")).count();assertTrue(buys>=3&&buys<=6,"mix of buying and selling");
            assertTrue(minerals.stream().anyMatch(o->o.id().startsWith("sell_")&&bulk.contains(o.recipe().getResult().getType())),"sells an ingot or raw metal");
            var items_=new HashSet<String>();for(var o:minerals)assertTrue(items_.add(o.id().substring(o.id().indexOf('_')+1)),"same mineral twice");
            for(var o:minerals){
                ItemStack mineral=o.id().startsWith("buy_")?o.recipe().getIngredients().getFirst():o.recipe().getResult();
                ItemStack emerald=o.id().startsWith("buy_")?o.recipe().getResult():o.recipe().getIngredients().getFirst();
                assertEquals(Material.EMERALD,emerald.getType());assertTrue(emerald.getAmount()>=1&&emerald.getAmount()<=64);
                assertFalse(mineral.getType().name().endsWith("_ORE"),"no ore blocks: "+mineral.getType());
                if(bulk.contains(mineral.getType()))assertTrue(mineral.getAmount()>=1&&mineral.getAmount()<=5);else assertEquals((int)VillagerOffers.ITEMS.get(mineral.getType().name().toLowerCase()),mineral.getAmount());
            }
            assertNotEquals(picks.get(0).recipe().getResult(),picks.get(1).recipe().getResult(),"identical pickaxes");
            for(var o:picks){var pick=o.recipe().getResult();assertTrue(Set.of(Material.STONE_PICKAXE,Material.IRON_PICKAXE,Material.GOLDEN_PICKAXE,Material.DIAMOND_PICKAXE).contains(pick.getType()));
                assertFalse(pick.getEnchantments().isEmpty());assertFalse(pick.containsEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE)&&pick.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH));
                for(var e:pick.getEnchantments().entrySet())assertTrue(e.getValue()>=1&&e.getValue()<=e.getKey().getMaxLevel());}
        }
    }
    @Test void quantityAndPriceSettingsAreRespected(){
        config.set("miner.offers.quantity-min",3);config.set("miner.offers.quantity-max",3);config.set("miner.minerals.iron_ingot.buy-price",2.0);
        for(String ore:List.of("coal","redstone","lapis_lazuli","diamond","amethyst_shard","quartz","raw_copper","copper_ingot","raw_iron","raw_gold","gold_ingot"))config.set("miner.minerals."+ore+".enabled",false);
        var minerals=new VillagerOffers(items,config).miner(new Random(1)).stream().filter(o->o.id().endsWith("iron_ingot")).toList();
        assertEquals(1,minerals.size());var o=minerals.getFirst();
        if(o.id().startsWith("buy_")){assertEquals(3,o.recipe().getIngredients().getFirst().getAmount());assertEquals(6,o.recipe().getResult().getAmount());}
        else assertEquals(3,o.recipe().getResult().getAmount());
    }
    @Test void offerPersistenceRetainsUsesAndRolledEnchantmentRatherThanRerolling(){
        var offers=new VillagerOffers(items,config).miner(new Random(2));offers.getFirst().recipe().setUses(3);
        String encoded=VillagerOffers.encode(offers);var restored=VillagerOffers.decode(encoded);
        assertEquals(offers.size(),restored.size());assertEquals(3,restored.getFirst().recipe().getUses());
        for(int i=0;i<offers.size();i++){assertEquals(offers.get(i).id(),restored.get(i).id());assertEquals(offers.get(i).recipe().getResult(),restored.get(i).recipe().getResult());assertEquals(offers.get(i).recipe().getIngredients(),restored.get(i).recipe().getIngredients());}
    }
}
