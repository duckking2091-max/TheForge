package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PackVisualsTest {
    ServerMock server;PackVisuals visuals;
    @BeforeEach void setup(){server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();var config=new YamlConfiguration();config.set("resource-pack.assume-installed",false);visuals=new PackVisuals(plugin,new ExpansionItems(plugin,n->config),n->config);}
    @AfterEach void teardown(){visuals.close();MockBukkit.unmock();}
    @Test void onlySuccessfulStatusForOurPackEnablesGlyphsAndFailureClearsThem(){
        var player=server.addPlayer();assertFalse(visuals.loaded(player));
        assertEquals("Forging",PlainTextComponentSerializer.plainText().serialize(visuals.title(player,PackVisuals.Theme.FORGING,3)));
        visuals.status(new PlayerResourcePackStatusEvent(player,UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));assertFalse(visuals.loaded(player));
        visuals.status(new PlayerResourcePackStatusEvent(player,PackVisuals.PACK_ID,PlayerResourcePackStatusEvent.Status.ACCEPTED));assertFalse(visuals.loaded(player));
        visuals.status(new PlayerResourcePackStatusEvent(player,PackVisuals.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));assertTrue(visuals.loaded(player));
        assertTrue(PlainTextComponentSerializer.plainText().serialize(visuals.title(player,PackVisuals.Theme.FORGING,3)).contains("\ue000"));
        visuals.status(new PlayerResourcePackStatusEvent(player,PackVisuals.PACK_ID,PlayerResourcePackStatusEvent.Status.FAILED_RELOAD));assertFalse(visuals.loaded(player));
    }
}
