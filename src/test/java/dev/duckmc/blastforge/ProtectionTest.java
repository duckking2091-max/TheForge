package dev.duckmc.blastforge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProtectionTest {
    @Test void guardsSquareSpawnBoundaryAndAllowsExceptions() {
        assertTrue(SpawnProtection.blocked(true,true,false,16,16,-16));
        assertFalse(SpawnProtection.blocked(true,true,false,16,17,0));
        assertFalse(SpawnProtection.blocked(true,true,true,16,0,0));
        assertFalse(SpawnProtection.blocked(false,true,false,16,0,0));
        assertFalse(SpawnProtection.blocked(true,false,false,16,0,0));
        assertFalse(SpawnProtection.blocked(true,true,false,0,0,0));
    }
}
