package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VillagerRulesTest {
    @Test void populationChanceClampsAndUsesPercentagePoints() {
        assertEquals(.05,VillagerRules.chance(.05,.05,0),1e-9);
        assertEquals(.10,VillagerRules.chance(.05,.05,1),1e-9);
        assertEquals(.55,VillagerRules.chance(.05,.05,10),1e-9);
        assertEquals(1,VillagerRules.chance(.05,.05,19));
        assertEquals(1,VillagerRules.chance(.05,.05,500));
        assertEquals(0,VillagerRules.chance(Double.NaN,.05,1));
    }
    @Test void stabilityResetsAtWorldChangeOrAnchorExitAndIgnoresClockReversal() {
        UUID world=UUID.randomUUID();var tracker=new VillagerRules.Stability(world,0,64,0,1000);
        assertFalse(tracker.ready(world,20,65,20,600999,600000,64));
        assertTrue(tracker.ready(world,20,65,20,601000,600000,64));
        assertFalse(tracker.ready(UUID.randomUUID(),0,64,0,700000,600000,64));
        assertFalse(tracker.ready(world,65,64,0,700000,600000,64));
        assertFalse(tracker.ready(world,0,64,0,0,600000,64));
    }
    @Test void claimIsExclusiveAndUnloadedVillagerDoesNotReleaseOwnership() {
        var claims=new VillagerRules.Claims();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertTrue(claims.claim("w:1:2:3",a));assertFalse(claims.claim("w:1:2:3",b));
        assertFalse(claims.claim("w:2:2:3",a));assertFalse(claims.release("w:1:2:3",b));
        assertEquals(a,claims.owner("w:1:2:3"));assertTrue(claims.release("w:1:2:3",a));
        assertTrue(claims.claim("w:1:2:3",b));
    }
}
