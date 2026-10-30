package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MagicFlightPersistenceTest {
    @TempDir Path directory;ServerMock server;PersistentStore store;
    @BeforeEach void setup(){server=MockBukkit.mock();server.addSimpleWorld("world");store=new PersistentStore(directory.resolve("flights.db"));}
    @AfterEach void close(){store.close();MockBukkit.unmock();}
    @Test void unacknowledgedPlayerSaveKeepsRestorationIntentForNextJoin(){
        var player=server.addPlayer();var config=new YamlConfiguration();var flights=new MagicFlights(store,n->config);
        assertTrue(flights.grant(player,20));assertTrue(player.getAllowFlight());flights.restore(player);assertFalse(player.getAllowFlight());
        // MockBukkit has no disk playerdata save: nonce acknowledgment must fail safely.
        assertFalse(store.entries("magic.flight-restore").isEmpty());
        player.setAllowFlight(true);player.setFlying(true); // stale persisted playerdata after a simulated crash
        new MagicFlights(store,n->config).reconcile(player);assertFalse(player.getAllowFlight());assertFalse(player.isFlying());
    }
    @Test void existingFlightAllowanceReturnsToItsOriginalNotFlyingState(){
        var player=server.addPlayer();player.setAllowFlight(true);var flights=new MagicFlights(store,n->new YamlConfiguration());
        assertTrue(flights.grant(player,20));assertTrue(player.isFlying());flights.restore(player);
        assertTrue(player.getAllowFlight());assertFalse(player.isFlying());
    }
    @Test void sneakingNeverEndsAnActiveFlightAndRestoreStillRevokesIt(){
        var player=server.addPlayer();player.setGameMode(org.bukkit.GameMode.SURVIVAL);var flights=new MagicFlights(store,n->new YamlConfiguration());
        player.setSneaking(true);assertTrue(flights.grant(player,30));assertTrue(player.isFlying(),"flight starts while Sneak is held");
        player.setFlying(false);flights.hold(player);assertTrue(player.isFlying(),"airborne: flight resumes even with Sneak held");
        flights.restore(player);assertFalse(player.getAllowFlight());assertFalse(player.isFlying(),"the survival baseline is restored afterwards");
        flights.hold(player);assertFalse(player.isFlying(),"no lease: nothing is re-enabled");
    }
    @Test void creativeFlightIsNeverTouched(){
        var player=server.addPlayer();player.setGameMode(org.bukkit.GameMode.CREATIVE);var flights=new MagicFlights(store,n->new YamlConfiguration());
        assertTrue(flights.grant(player,30));flights.restore(player);assertTrue(player.getAllowFlight(),"creative keeps its own flight");
    }
    @Test void windmillOrbitsExactlyOneAndAHalfTimesTheOriginalSpeed(){assertEquals(0.35*1.5,MagicSystem.WINDMILL_ORBIT,1e-12);}
}
