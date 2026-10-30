package dev.duckmc.blastforge;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import net.kyori.adventure.text.Component;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void extractsThreeOntoOneBookAndPreservesTheRest() {
        ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS,5); sword.addEnchantment(Enchantment.LOOTING,3);
        sword.addEnchantment(Enchantment.FIRE_ASPECT,2); sword.addEnchantment(Enchantment.UNBREAKING,3);
        sword.editMeta(m->{m.displayName(Component.text("Keep me"));((Damageable)m).setDamage(99);});
        ItemStack before=sword.clone();
        var result=Extraction.extract(sword,Set.of(Enchantment.SHARPNESS,Enchantment.LOOTING,Enchantment.FIRE_ASPECT));
        assertEquals(1,result.item().getEnchantments().size());
        assertEquals(3,result.item().getEnchantmentLevel(Enchantment.UNBREAKING));
        assertEquals(99,((Damageable)result.item().getItemMeta()).getDamage());
        assertEquals(before.getItemMeta().displayName(),result.item().getItemMeta().displayName());
        var stored=((EnchantmentStorageMeta)result.book().getItemMeta()).getStoredEnchants();
        assertEquals(3,stored.size()); assertEquals(5,stored.get(Enchantment.SHARPNESS));
        assertEquals(3,stored.get(Enchantment.LOOTING)); assertEquals(2,stored.get(Enchantment.FIRE_ASPECT));
        assertEquals(before,sword); assertEquals(1,result.book().getAmount());
    }
    @Test void refusesEmptyOrStaleSelections() {
        ItemStack sword=new ItemStack(Material.DIAMOND_SWORD); sword.addEnchantment(Enchantment.SHARPNESS,1);
        assertNull(Extraction.extract(sword,Set.of()));
        assertNull(Extraction.extract(sword,Set.of(Enchantment.LOOTING)));
    }
}
