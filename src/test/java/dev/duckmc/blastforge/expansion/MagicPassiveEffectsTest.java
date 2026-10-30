package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.*;
import static org.junit.jupiter.api.Assertions.*;

class MagicPassiveEffectsTest {
    ServerMock server;MagicPassives passives;ExpansionItems items;
    @BeforeEach void setup(){server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();items=new ExpansionItems(plugin,n->new YamlConfiguration());passives=new MagicPassives(items);}
    @AfterEach void close(){MockBukkit.unmock();}
    @Test void hastePresenceUsesAmplifierOneAndRemovingBookOnlyClearsOwnedEffect(){
        var player=server.addPlayer();player.getInventory().setItem(20,items.create("earth_book"));passives.tick(player);
        assertEquals(1,player.getPotionEffect(PotionEffectType.HASTE).getAmplifier());
        player.getInventory().clear();passives.tick(player);assertFalse(player.hasPotionEffect(PotionEffectType.HASTE));
    }
    @Test void strongerExternalHasteSurvivesInventoryReconciliationAndCleanup(){
        var player=server.addPlayer();player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,1200,3));player.getInventory().setItem(20,items.create("earth_book"));
        passives.tick(player);assertEquals(3,player.getPotionEffect(PotionEffectType.HASTE).getAmplifier());assertEquals(1200,player.getPotionEffect(PotionEffectType.HASTE).getDuration());
        passives.remove(player);assertEquals(3,player.getPotionEffect(PotionEffectType.HASTE).getAmplifier());
    }
}
