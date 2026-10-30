package dev.duckmc.blastforge.expansion;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopRulesTest {
    ExpansionItems items;
    @BeforeEach void setup(){MockBukkit.mock();items=new ExpansionItems(MockBukkit.createMockPlugin(),n->new YamlConfiguration());}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void mergeUsesHigherLevelAndAllowsApplicableConflicts(){
        var first=new ItemStack(Material.NETHERITE_SWORD);first.addEnchantment(Enchantment.SHARPNESS,4);
        var donor=new ItemStack(Material.IRON_SWORD);donor.addEnchantment(Enchantment.SHARPNESS,4);donor.addUnsafeEnchantment(Enchantment.SMITE,5);
        var result=ExpansionMerge.merge(first,donor);
        assertNotNull(result);assertEquals(4,result.getEnchantmentLevel(Enchantment.SHARPNESS));assertEquals(5,result.getEnchantmentLevel(Enchantment.SMITE));
        assertEquals(Material.NETHERITE_SWORD,result.getType());assertFalse(first.containsEnchantment(Enchantment.SMITE));
    }
    @Test void requiresExactPreviousTierTemplateAndIngot(){
        var source=new ItemStack(Material.NETHERITE_PICKAXE);
        assertNotNull(WorkshopRecipes.upgrade(items,source,items.create("siftie_template"),items.create("siftie_ingot")));
        assertNull(WorkshopRecipes.upgrade(items,source,items.create("enderite_template"),items.create("enderite_ingot")));
        assertNull(WorkshopRecipes.upgrade(items,source,new ItemStack(Material.PAPER),items.create("siftie_ingot")));
    }
    @Test void ritualRequiresAllEightDistinctIngredientsButAcceptsAnyElement(){
        var ingredients=new ItemStack[]{items.create("fire_book"),items.create("wardenite_ingot"),items.create("enderite_stick"),items.create("dragon_heart"),new ItemStack(Material.ECHO_SHARD),items.create("warden_heart"),items.create("bat_wing"),new ItemStack(Material.NETHERITE_INGOT)};
        assertTrue(WorkshopRecipes.ritual(items,ingredients));
        ingredients[0]=items.create("darkness_book");assertTrue(WorkshopRecipes.ritual(items,ingredients));
        ingredients[0]=new ItemStack(Material.BOOK);assertFalse(WorkshopRecipes.ritual(items,ingredients));
    }
    @Test void scytheRepairUsesMarkedIngotAndPreservesEnchantments(){
        var scythe=items.create("legendary_scythe");scythe.addEnchantment(Enchantment.SHARPNESS,5);
        scythe.editMeta(org.bukkit.inventory.meta.Damageable.class,m->m.setDamage(2000));
        var config=new YamlConfiguration();var result=WorkshopRecipes.forge(items,config,scythe,items.create("wardenite_ingot"));
        assertNotNull(result);assertEquals(750,((org.bukkit.inventory.meta.Damageable)result.getItemMeta()).getDamage());assertEquals(5,result.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertNull(WorkshopRecipes.forge(items,config,scythe,new ItemStack(Material.NETHERITE_INGOT)));
        config.set("repair-enabled",false);assertNull(WorkshopRecipes.forge(items,config,scythe,items.create("wardenite_ingot")));
    }
}
