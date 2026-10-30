package dev.duckmc.blastforge;
import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class RarityTest {
    @Test void defaultWeightsGiveDescendingRarity(){
        var config=new YamlConfiguration();int[] counts=new int[3];
        for(int i=0;i<10000;i++)counts[Rarity.level(config,(i+0.5)/10000.0)-1]++;
        assertArrayEquals(new int[]{8000,1700,300},counts);
    }
    @Test void boundariesAndCustomWeightsAreRespected(){
        var config=new YamlConfiguration();
        assertEquals(1,Rarity.level(config,0));assertEquals(2,Rarity.level(config,0.8));
        assertEquals(3,Rarity.level(config,0.97));assertEquals(3,Rarity.level(config,0.999999));
        config.set("blast-level-weights.1",0);config.set("blast-level-weights.2",1);config.set("blast-level-weights.3",0);
        assertEquals(2,Rarity.level(config,0));assertEquals(2,Rarity.level(config,0.999));
    }
    @Test void allZeroWeightsFallBackToDefaults(){
        var config=new YamlConfiguration();
        for(int i=1;i<=3;i++)config.set("blast-level-weights."+i,0);
        assertEquals(1,Rarity.level(config,0.5));assertEquals(3,Rarity.level(config,0.99));
    }
    @Test void migratesOldDefaultsButPreservesCustomConfig(){
        var old=new YamlConfiguration();old.set("forging-book-trade-chance",0.15);old.set("blast-book-trade-chance",0.15);
        assertTrue(Rarity.migrate(old));
        assertEquals(0.02,old.getDouble("forging-book-trade-chance"));assertEquals(0.04,old.getDouble("blast-book-trade-chance"));
        assertFalse(Rarity.migrate(old));
        var custom=new YamlConfiguration();custom.set("forging-book-trade-chance",0.07);custom.set("blast-book-trade-chance",0.01);
        custom.set("forging-book-emeralds",45);custom.set("blast-level-weights.3",9);
        Rarity.migrate(custom);
        assertEquals(0.07,custom.getDouble("forging-book-trade-chance"));assertEquals(0.01,custom.getDouble("blast-book-trade-chance"));
        assertEquals(45,custom.getInt("forging-book-emeralds"));assertEquals(9,custom.getInt("blast-level-weights.3"));
    }
    @Test void resourceDefaultsDoNotSuppressMigrationOfExistingFiles(){
        var old=new YamlConfiguration();old.set("forging-book-trade-chance",0.15);old.set("blast-book-trade-chance",0.15);
        old.addDefault("rarity-version",1);
        assertTrue(Rarity.migrate(old));
        assertEquals(0.02,old.getDouble("forging-book-trade-chance"));
        assertTrue(old.isSet("rarity-version"));
    }
}
