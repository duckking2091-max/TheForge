package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;

class MaterialRecipesTest {
    ExpansionItems items; MaterialRecipes recipes;
    @BeforeEach void setup(){MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();items=new ExpansionItems(plugin,n->new YamlConfiguration());recipes=new MaterialRecipes(plugin,items);}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void exactSiftieLayoutRejectsRenamedVanillaIngredients(){
        ItemStack[] grid={items.create("siftie_ingot"),vanilla(Material.BLAZE_ROD),items.create("siftie_ingot"),vanilla(Material.BLAZE_ROD),vanilla(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),vanilla(Material.BLAZE_ROD),items.create("siftie_ingot"),vanilla(Material.BLAZE_ROD),items.create("siftie_ingot")};
        assertTrue(recipes.matches("siftie_template",grid));grid[0]=vanilla(Material.IRON_INGOT);grid[0].editMeta(m->m.displayName(Component.text("Siftie Ingot")));assertFalse(recipes.matches("siftie_template",grid));
    }
    @Test void enderiteCrossNeedsFourActualScrapsAndSiftieTemplate(){
        ItemStack[] grid={null,items.create("enderite_scrap"),null,items.create("enderite_scrap"),items.create("siftie_template"),items.create("enderite_scrap"),null,items.create("enderite_scrap"),null};
        assertTrue(recipes.matches("enderite_template",grid));grid[0]=items.create("enderite_scrap");assertFalse(recipes.matches("enderite_template",grid));
    }
    @Test void wardeniteRingAndVerticalStickUseExactIdentity(){
        ItemStack[] ring=new ItemStack[9];java.util.Arrays.setAll(ring,i->i==4?vanilla(Material.ECHO_SHARD):items.create("wardenite_scrap"));assertTrue(recipes.matches("wardenite_ingot",ring));ring[4]=items.create("warden_heart");assertFalse(recipes.matches("wardenite_ingot",ring));
        assertTrue(recipes.matches("enderite_stick",new ItemStack[]{null,items.create("enderite_ingot"),null,null,items.create("enderite_ingot"),null,null,null,null}));
        assertFalse(recipes.matches("enderite_stick",new ItemStack[]{items.create("enderite_ingot"),items.create("enderite_ingot"),null,null,null,null,null,null,null}));
    }
    @Test void darknessRequiresFourDistinctRealBooksInAnyOrder(){
        ItemStack[] matrix={items.create("wind_book"),items.create("fire_book"),items.create("water_book"),items.create("earth_book")};assertTrue(recipes.matches("darkness_book",matrix));matrix[3]=items.create("wind_book");assertFalse(recipes.matches("darkness_book",matrix));
    }
    @Test void legitimateRenamesRemainValidAndCustomItemsCannotSubstituteForVanilla(){
        ItemStack a=items.create("enderite_ingot");a.editMeta(m->m.displayName(Component.text("Treasured ingot")));assertTrue(recipes.matches("enderite_stick",new ItemStack[]{a,null,a,null}));
        assertFalse(recipes.matches("enchanting_anvil",new ItemStack[]{vanilla(Material.IRON_BLOCK),items.create("forging_book"),vanilla(Material.IRON_BLOCK),null,items.create("enchanting_anvil"),null,vanilla(Material.DIAMOND),vanilla(Material.DIAMOND),vanilla(Material.DIAMOND)}));
    }
    @Test void isolatedCookingCarriersDoNotMatchCachedVanillaIronRecipeAndRenamedSourcesRemainValid(){
        recipes.start();var server=(org.mockbukkit.mockbukkit.ServerMock)Bukkit.getServer();var world=server.addSimpleWorld("renamed_cooking");var block=world.getBlockAt(0,10,0);block.setType(Material.FURNACE);
        assertEquals(Material.PRISMARINE_CRYSTALS,items.create("raw_siftie").getType());assertEquals(Material.NETHERITE_SCRAP,items.create("enderite_scrap").getType());
        var vanillaIron=new org.bukkit.inventory.FurnaceRecipe(NamespacedKey.minecraft("iron_ingot_from_smelting_raw_iron"),new ItemStack(Material.IRON_INGOT),Material.RAW_IRON,.7f,200);
        for(String source:java.util.List.of("raw_siftie","enderite_scrap"))for(String method:java.util.List.of("smelt","blast")) {
            var recipe=(org.bukkit.inventory.CookingRecipe<?>)Bukkit.getRecipe(NamespacedKey.fromString("blastforge:materials_"+source+"_"+method));assertNotNull(recipe);assertInstanceOf(org.bukkit.inventory.RecipeChoice.MaterialChoice.class,recipe.getInputChoice());
            var renamed=items.create(source);renamed.setAmount(32);renamed.editMeta(meta->{meta.displayName(Component.text("My resource"));meta.lore(java.util.List.of(Component.text("Kept safe")));});
            assertTrue(recipe.getInputChoice().test(renamed));assertFalse(vanillaIron.getInputChoice().test(renamed));
            var event=new org.bukkit.event.inventory.FurnaceSmeltEvent(block,renamed,recipe.getResult(),recipe);recipes.smelt(event);assertFalse(event.isCancelled());assertEquals(recipe.getResult(),event.getResult());
            var ordinary=new ItemStack(renamed.getType());ordinary.editMeta(meta->meta.displayName(Component.text("My resource")));var fakeEvent=new org.bukkit.event.inventory.FurnaceSmeltEvent(block,ordinary,recipe.getResult(),recipe);recipes.smelt(fakeEvent);assertTrue(fakeEvent.isCancelled());
            var malformed=renamed.clone();malformed.editMeta(meta->meta.getPersistentDataContainer().set(NamespacedKey.fromString("blastforge:item_kind"),org.bukkit.persistence.PersistentDataType.STRING,"wardenite_scrap"));var malformedEvent=new org.bukkit.event.inventory.FurnaceSmeltEvent(block,malformed,recipe.getResult(),recipe);recipes.smelt(malformedEvent);assertTrue(malformedEvent.isCancelled());
        }
    }
    @Test void ordinaryOrMalformedIsolatedCarriersCannotConsumeFreshFuel(){
        recipes.start();var server=(org.mockbukkit.mockbukkit.ServerMock)Bukkit.getServer();var world=server.addSimpleWorld("fuel_guards");
        for(Material furnaceType:java.util.List.of(Material.FURNACE,Material.BLAST_FURNACE))for(String id:java.util.List.of("raw_siftie","enderite_scrap")) {
            var block=world.getBlockAt(0,10,0);block.setType(furnaceType);var furnace=(org.bukkit.block.Furnace)block.getState();
            var canonical=items.create(id);furnace.getInventory().setSmelting(new ItemStack(canonical.getType()));
            var invalid=new org.bukkit.event.inventory.FurnaceBurnEvent(block,new ItemStack(Material.COAL),1600);Bukkit.getPluginManager().callEvent(invalid);assertTrue(invalid.isCancelled());
            canonical.editMeta(meta->meta.displayName(Component.text("Renamed valid resource")));furnace.getInventory().setSmelting(canonical);
            var valid=new org.bukkit.event.inventory.FurnaceBurnEvent(block,new ItemStack(Material.COAL),1600);Bukkit.getPluginManager().callEvent(valid);assertFalse(valid.isCancelled());
            canonical.editMeta(meta->meta.getPersistentDataContainer().set(NamespacedKey.fromString("blastforge:item_kind"),org.bukkit.persistence.PersistentDataType.STRING,"bad_identity"));furnace.getInventory().setSmelting(canonical);
            var malformed=new org.bukkit.event.inventory.FurnaceBurnEvent(block,new ItemStack(Material.COAL),1600);Bukkit.getPluginManager().callEvent(malformed);assertTrue(malformed.isCancelled());
        }
    }    @Test void customSmeltingNeverRewritesVanillaRecipeResultsAndRejectsMalformedSources(){
        recipes.start();var world=Bukkit.getWorlds().isEmpty()?((org.mockbukkit.mockbukkit.ServerMock)Bukkit.getServer()).addSimpleWorld("smelting"):Bukkit.getWorlds().getFirst();var block=world.getBlockAt(0,10,0);block.setType(Material.FURNACE);
        var vanillaRecipe=new org.bukkit.inventory.FurnaceRecipe(NamespacedKey.minecraft("iron_ingot_from_smelting_raw_iron"),new ItemStack(Material.IRON_INGOT),Material.RAW_IRON,.7f,200);
        var ordinary=new org.bukkit.event.inventory.FurnaceSmeltEvent(block,new ItemStack(Material.RAW_IRON),new ItemStack(Material.IRON_INGOT),vanillaRecipe);recipes.smelt(ordinary);assertFalse(ordinary.isCancelled());assertEquals(new ItemStack(Material.IRON_INGOT),ordinary.getResult());
        var wrongRecipe=new org.bukkit.event.inventory.FurnaceSmeltEvent(block,items.create("raw_siftie"),new ItemStack(Material.IRON_INGOT),vanillaRecipe);recipes.smelt(wrongRecipe);assertTrue(wrongRecipe.isCancelled());assertEquals(new ItemStack(Material.IRON_INGOT),wrongRecipe.getResult());
        var custom=(org.bukkit.inventory.CookingRecipe<?>)Bukkit.getRecipe(NamespacedKey.fromString("blastforge:materials_raw_siftie_smelt"));
        var wrongSource=new org.bukkit.event.inventory.FurnaceSmeltEvent(block,new ItemStack(Material.RAW_IRON),custom.getResult(),custom);recipes.smelt(wrongSource);assertTrue(wrongSource.isCancelled());
        var valid=new org.bukkit.event.inventory.FurnaceSmeltEvent(block,items.create("raw_siftie"),custom.getResult(),custom);recipes.smelt(valid);assertFalse(valid.isCancelled());assertEquals(custom.getResult(),valid.getResult());
    }    static ItemStack vanilla(Material m){return new ItemStack(m);}
}


