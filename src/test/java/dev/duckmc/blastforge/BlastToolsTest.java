package dev.duckmc.blastforge;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Material;
import static org.junit.jupiter.api.Assertions.*;

class BlastToolsTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void shovelsDigTheirTerrain(){
        for(Material tool:new Material[]{Material.WOODEN_SHOVEL,Material.STONE_SHOVEL,Material.COPPER_SHOVEL,Material.IRON_SHOVEL,Material.GOLDEN_SHOVEL,Material.DIAMOND_SHOVEL,Material.NETHERITE_SHOVEL}){
            assertTrue(BlastTools.supported(tool));
            for(Material block:new Material[]{Material.DIRT,Material.GRASS_BLOCK,Material.SAND,Material.GRAVEL,Material.CLAY,Material.SNOW_BLOCK})
                assertTrue(BlastTools.mineable(tool,block),tool+" / "+block);
            assertFalse(BlastTools.mineable(tool,Material.STONE));
        }
    }
    @Test void pickaxesKeepTheirOwnBlockSelection(){
        assertTrue(BlastTools.supported(Material.DIAMOND_PICKAXE));
        assertTrue(BlastTools.mineable(Material.DIAMOND_PICKAXE,Material.STONE));
        assertTrue(BlastTools.mineable(Material.DIAMOND_PICKAXE,Material.IRON_ORE));
        assertFalse(BlastTools.mineable(Material.DIAMOND_PICKAXE,Material.DIRT));
    }
    @Test void otherToolsAndAirNeverActivateBlast(){
        for(Material tool:new Material[]{Material.DIAMOND_AXE,Material.DIAMOND_HOE,Material.DIAMOND_SWORD,Material.AIR}){
            assertFalse(BlastTools.supported(tool));assertFalse(BlastTools.mineable(tool,Material.DIRT));
        }
        assertFalse(BlastTools.mineable(Material.DIAMOND_SHOVEL,Material.AIR));
    }
}
