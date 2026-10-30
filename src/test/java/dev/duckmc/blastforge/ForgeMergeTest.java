package dev.duckmc.blastforge;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;

class ForgeMergeTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void teardown() { MockBukkit.unmock(); }
    @Test void onlyDiamondSwordCanCombineAllDamageEnchantments() {
        for(Material type:new Material[]{Material.DIAMOND_SWORD,Material.NETHERITE_SWORD,Material.IRON_SWORD}) {
            ItemStack base=new ItemStack(type),donor=new ItemStack(Material.IRON_SWORD);
            base.addEnchantment(Enchantment.SHARPNESS,5);
            donor.addUnsafeEnchantment(Enchantment.SMITE,5);
            donor.addUnsafeEnchantment(Enchantment.BANE_OF_ARTHROPODS,5);
            ItemStack result=ForgeMerge.result(base,donor);
            if(type==Material.DIAMOND_SWORD) {
                assertNotNull(result);
                assertEquals(5,result.getEnchantmentLevel(Enchantment.SHARPNESS));
                assertEquals(5,result.getEnchantmentLevel(Enchantment.SMITE));
                assertEquals(5,result.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS));
            } else assertNull(result);
        }
    }
    @Test void extractedBooksCanApplyToDiamondSwordButNotInapplicableEnchantments() {
        ItemStack base=new ItemStack(Material.DIAMOND_SWORD),book=new ItemStack(Material.ENCHANTED_BOOK);
        base.addEnchantment(Enchantment.SHARPNESS,3);
        book.editMeta(org.bukkit.inventory.meta.EnchantmentStorageMeta.class,meta -> {
            meta.addStoredEnchant(Enchantment.SMITE,4,false);
            meta.addStoredEnchant(Enchantment.FORTUNE,3,false);
        });
        ItemStack result=ForgeMerge.result(base,book);
        assertNotNull(result);
        assertEquals(4,result.getEnchantmentLevel(Enchantment.SMITE));
        assertEquals(0,result.getEnchantmentLevel(Enchantment.FORTUNE));
    }
    @Test void transfersAcrossMaterialsPreservingFirstItemAndBothInputs() {
        ItemStack base=new ItemStack(Material.NETHERITE_AXE);
        base.editMeta(meta -> { meta.displayName(Component.text("My axe")); ((Damageable)meta).setDamage(75); });
        base.addEnchantment(Enchantment.EFFICIENCY,2);
        ItemStack donor=new ItemStack(Material.IRON_AXE);
        donor.addEnchantment(Enchantment.EFFICIENCY,2);
        donor.addEnchantment(Enchantment.UNBREAKING,3);
        ItemStack baseBefore=base.clone(), donorBefore=donor.clone();
        ItemStack result=ForgeMerge.result(base,donor);
        assertNotNull(result);
        assertEquals(Material.NETHERITE_AXE,result.getType());
        assertEquals(3,result.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals(3,result.getEnchantmentLevel(Enchantment.UNBREAKING));
        assertEquals(75,((Damageable)result.getItemMeta()).getDamage());
        assertEquals(base.getItemMeta().displayName(),result.getItemMeta().displayName());
        assertEquals(baseBefore,base); assertEquals(donorBefore,donor);
    }
    @Test void rejectsMismatchedTypesAndEmptyDonors() {
        ItemStack base=new ItemStack(Material.DIAMOND_AXE), donor=new ItemStack(Material.IRON_PICKAXE);
        donor.addEnchantment(Enchantment.EFFICIENCY,3);
        assertNull(ForgeMerge.result(base,donor));
        assertNull(ForgeMerge.result(base,new ItemStack(Material.IRON_AXE)));
        assertNull(ForgeMerge.result(base,null));
    }
    @Test void retainsSilkTouchAndRejectsConflictingFortune() {
        ItemStack base=new ItemStack(Material.DIAMOND_PICKAXE), donor=new ItemStack(Material.IRON_PICKAXE);
        base.addEnchantment(Enchantment.SILK_TOUCH,1);
        donor.addEnchantment(Enchantment.FORTUNE,3);
        donor.addEnchantment(Enchantment.UNBREAKING,2);
        ItemStack result=ForgeMerge.result(base,donor);
        assertNotNull(result);
        assertEquals(1,result.getEnchantmentLevel(Enchantment.SILK_TOUCH));
        assertEquals(0,result.getEnchantmentLevel(Enchantment.FORTUNE));
        assertEquals(2,result.getEnchantmentLevel(Enchantment.UNBREAKING));
    }
}
