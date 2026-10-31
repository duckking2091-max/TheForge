package dev.duckmc.blastforge;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class CompatTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void attributesAreFoundByTheirCurrentName(){
        for(String name:new String[]{"armor","armor_toughness","knockback_resistance","attack_damage","attack_speed","block_break_speed"})
            assertNotNull(Compat.attribute(name),name);
        assertNull(Compat.attribute("no_such_attribute"));
    }
    @Test void equipmentAndItemModelsWorkOnThisApi(){
        assertTrue(Compat.hasEquipmentModels());
        var meta=new ItemStack(Material.NETHERITE_HELMET).getItemMeta();
        assertTrue(Compat.equipmentModel(meta,org.bukkit.inventory.EquipmentSlot.HEAD,new org.bukkit.NamespacedKey("blastforge","siftie")));
        assertTrue(Compat.itemModel(new ItemStack(Material.PAPER).getItemMeta(),new org.bukkit.NamespacedKey("blastforge","water_orb")));
    }
    @Test void versionCheckReadsTheServerVersion(){assertTrue(Compat.atLeast(0));assertFalse(Compat.atLeast(99));}
}
