package dev.duckmc.blastforge;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;

class AnvilOperationTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void nativeRenameAndRepairResultsAreUntouched(){
        var base=new ItemStack(Material.DIAMOND_SWORD);
        var nativeResult=base.clone();nativeResult.editMeta(m->m.displayName(Component.text("Renamed")));
        for(ItemStack donor:new ItemStack[]{null,new ItemStack(Material.DIAMOND,4),new ItemStack(Material.DIAMOND_SWORD)}){
            var operation=AnvilOperation.resolve(base,donor,nativeResult);
            assertFalse(operation.custom());assertSame(nativeResult,operation.result());
        }
    }
    @Test void ordinaryBooksAndVanillaRejectedResultsStayNative(){
        var base=new ItemStack(Material.DIAMOND_SWORD);var donor=new ItemStack(Material.ENCHANTED_BOOK);
        donor.editMeta(EnchantmentStorageMeta.class,m->m.addStoredEnchant(Enchantment.SHARPNESS,5,false));
        var result=AnvilOperation.resolve(base,donor,null);
        assertFalse(result.custom());assertNull(result.result());
        var same=new ItemStack(Material.DIAMOND_SWORD);same.addEnchantment(Enchantment.SHARPNESS,5);
        assertFalse(AnvilOperation.resolve(base,same,null).custom());
    }
    @Test void specialCrossMaterialTransferStillWorks(){
        var base=new ItemStack(Material.NETHERITE_AXE);var donor=new ItemStack(Material.IRON_AXE);
        donor.addEnchantment(Enchantment.EFFICIENCY,3);
        var result=AnvilOperation.resolve(base,donor,null);
        assertTrue(result.custom());assertEquals(3,result.result().getEnchantmentLevel(Enchantment.EFFICIENCY));
    }
    @Test void diamondConflictMergePreservesNativeRepairAndRename(){
        var base=new ItemStack(Material.DIAMOND_SWORD);base.addEnchantment(Enchantment.SHARPNESS,5);
        var donor=new ItemStack(Material.DIAMOND_SWORD);donor.addEnchantment(Enchantment.SMITE,5);
        var nativeResult=base.clone();nativeResult.editMeta(m->{((Damageable)m).setDamage(20);m.displayName(Component.text("Repaired"));});
        var result=AnvilOperation.resolve(base,donor,nativeResult);
        assertTrue(result.custom());assertEquals(5,result.result().getEnchantmentLevel(Enchantment.SMITE));
        assertEquals(5,result.result().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(20,((Damageable)result.result().getItemMeta()).getDamage());
        assertEquals(nativeResult.getItemMeta().displayName(),result.result().getItemMeta().displayName());
    }
    @Test void plainBookStillSelectsExtraction(){
        var base=new ItemStack(Material.IRON_SWORD);base.addEnchantment(Enchantment.SHARPNESS,3);
        assertTrue(AnvilOperation.resolve(base,new ItemStack(Material.BOOK),null).custom());
    }
}
