package dev.duckmc.blastforge;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @Test void volumesAndOrientationMatchEveryFace() {
        for (int depth=1; depth<=3; depth++) for (int axis=0; axis<3; axis++) for (int sign : new int[]{-1,1}) {
            var cells = MiningShape.offsets(depth, axis, sign);
            assertEquals(9*depth, cells.size());
            assertEquals(cells.size(), new HashSet<>(cells).size());
            assertTrue(cells.contains(new MiningShape.Cell(0,0,0)));
            for (var c : cells) {
                int[] values = {c.x(),c.y(),c.z()};
                assertTrue(values[axis]*sign >= 0 && values[axis]*sign < depth);
                for (int a=0;a<3;a++) if(a!=axis) assertTrue(Math.abs(values[a])<=1);
            }
        }
    }
    @Test void rejectsInvalidGeometry() {
        assertThrows(IllegalArgumentException.class, () -> MiningShape.offsets(4,0,1));
        assertThrows(IllegalArgumentException.class, () -> MiningShape.offsets(1,3,1));
    }
    @Test void preservesToolFamiliesAcrossMaterials() {
        assertEquals(MergeRules.family("NETHERITE_AXE"), MergeRules.family("IRON_AXE"));
        assertNotEquals(MergeRules.family("IRON_AXE"), MergeRules.family("IRON_PICKAXE"));
        assertEquals("", MergeRules.family("IRON_BLOCK"));
        assertEquals("BOOTS", MergeRules.family("DIAMOND_BOOTS"));
        assertEquals("", MergeRules.family("DIAMOND"));
    }
    @Test void mergesLevelsWithoutExceedingMaximum() {
        assertEquals(3,MergeRules.level(2,2,3));
        assertEquals(3,MergeRules.level(3,3,3));
        assertEquals(3,MergeRules.level(1,3,3));
        assertEquals(2,MergeRules.level(2,1,3));
        assertEquals(1,MergeRules.level(0,1,3));
    }
}
