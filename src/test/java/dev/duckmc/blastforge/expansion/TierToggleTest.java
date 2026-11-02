package dev.duckmc.blastforge.expansion;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class TierToggleTest {
    YamlConfiguration materials=new YamlConfiguration();ExpansionItems items;
    @BeforeEach void setup(){MockBukkit.mock();items=new ExpansionItems(MockBukkit.createMockPlugin(),n->n.equals("materials")?materials:new YamlConfiguration());}
    @AfterEach void teardown(){MockBukkit.unmock();}
    String upgraded(ItemStack source,String template,String ingot){var r=WorkshopRecipes.upgrade(items,source,items.create(template),items.create(ingot));return r==null?null:items.id(r.item());}
    @Test void allTiersAreOnByDefault(){for(String t:new String[]{"siftie","enderite","wardenite"})assertTrue(items.tierEnabled(t));}
    @Test void theUpgradePathSkipsATurnedOffTier(){
        assertEquals("siftie_pickaxe",upgraded(new ItemStack(Material.NETHERITE_PICKAXE),"siftie_template","siftie_ingot"));
        materials.set("tiers-enabled.siftie",false);
        assertNull(upgraded(new ItemStack(Material.NETHERITE_PICKAXE),"siftie_template","siftie_ingot"),"nothing upgrades into Siftie");
        assertEquals("enderite_pickaxe",upgraded(new ItemStack(Material.NETHERITE_PICKAXE),"enderite_template","enderite_ingot"),"Netherite goes straight to Enderite");
        assertEquals("enderite_pickaxe",upgraded(items.create("siftie_pickaxe"),"enderite_template","enderite_ingot"),"Siftie gear players already own can still move on");
        materials.set("tiers-enabled.wardenite",false);
        assertNull(upgraded(items.create("enderite_pickaxe"),"wardenite_template","wardenite_ingot"),"the end of the path when Wardenite is off");
    }
    @Test void theRitualTakesVanillaStandInsForTurnedOffTiers(){
        ItemStack[] ring={items.create("fire_book"),items.create("wardenite_ingot"),items.create("enderite_stick"),items.create("dragon_heart"),new ItemStack(Material.ECHO_SHARD),items.create("warden_heart"),items.create("bat_wing"),new ItemStack(Material.NETHERITE_INGOT)};
        assertTrue(WorkshopRecipes.ritual(items,ring));
        materials.set("tiers-enabled.wardenite",false);materials.set("tiers-enabled.enderite",false);
        assertFalse(WorkshopRecipes.ritual(items,ring),"the turned-off items are no longer what the ritual asks for");
        ring[1]=new ItemStack(Material.NETHERITE_INGOT);ring[2]=new ItemStack(Material.BLAZE_ROD);
        assertTrue(WorkshopRecipes.ritual(items,ring),"a netherite ingot and a blaze rod take their place");
    }
    @Test void ownedItemsStayUsable(){materials.set("tiers-enabled.wardenite",false);var sword=items.create("wardenite_sword");assertEquals("wardenite_sword",items.id(sword),"existing gear keeps its identity and stats");}
}
