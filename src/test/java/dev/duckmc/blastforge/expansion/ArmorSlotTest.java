package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import static org.junit.jupiter.api.Assertions.*;

class ArmorSlotTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void armorHasNoEquipOverrideSoTheNetheriteCarrierDecidesTheSlot(){
        var config=new YamlConfiguration();var items=new ExpansionItems(MockBukkit.createMockPlugin(),n->config);
        for(String tier:java.util.List.of("siftie","enderite","wardenite"))for(String piece:java.util.List.of("helmet","chestplate","leggings","boots"))
            assertFalse(items.create(tier+"_"+piece).getItemMeta().hasEquippable(),tier+"_"+piece);
        // MockBukkit does not store equippable components, so repairing an old item can only be checked in game.
        assertFalse(items.repairEquippable(items.create("siftie_chestplate")),"new armor needs no repair");
    }
}
