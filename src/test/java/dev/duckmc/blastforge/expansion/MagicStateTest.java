package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MagicStateTest {
    @TempDir Path folder;
    @Test void everyAbilityHasItsOwnPersistentCooldown() {
        UUID player=UUID.randomUUID();Path path=folder.resolve("magic.db");
        try(var store=new PersistentStore(path)) {
            var cooldowns=new MagicCooldowns(store);
            cooldowns.commit(player,"earth_book",1,1_000,60);
            assertFalse(cooldowns.ready(player,"earth_book",1,1_001));
            assertTrue(cooldowns.ready(player,"earth_book",0,1_001),"another ability of the same book is still ready");
            assertTrue(cooldowns.ready(player,"water_book",1,1_001));
            cooldowns.commit(player,"legendary_scythe",0,1_000,30);
            assertFalse(cooldowns.ready(player,"legendary_scythe",0,1_001));
            assertTrue(cooldowns.ready(player,"legendary_scythe",1,1_001));
        }
        try(var store=new PersistentStore(path)) {
            var cooldowns=new MagicCooldowns(store);
            assertFalse(cooldowns.ready(player,"earth_book",1,60_999));
            assertTrue(cooldowns.ready(player,"earth_book",1,61_000));
        }
    }
    @Test void zeroCooldownExcavationDoesNotEraseAnExistingWallCooldown() {
        try(var store=new PersistentStore(folder.resolve("magic.db"))) {
            var cd=new MagicCooldowns(store);UUID p=UUID.randomUUID();
            cd.commit(p,"earth_book",1,1_000,60);
            cd.commit(p,"earth_book",0,1_001,0);
            assertFalse(cd.ready(p,"earth_book",1,1_002));
            assertTrue(cd.ready(p,"earth_book",0,1_002));
        }
    }
    @Test void sameStateReplacementRevokesTerrainOwnershipAcrossRestart() {
        UUID world=UUID.randomUUID(),owner=UUID.randomUUID(),op=UUID.randomUUID();
        var pos=new MagicTerrainJournal.Position(world,2,64,-3);Path path=folder.resolve("terrain.db");
        try(var store=new PersistentStore(path)) {
            var journal=new MagicTerrainJournal(store);
            assertTrue(journal.reserve(pos,op,owner,"minecraft:dirt","minecraft:air",10_000));
            assertFalse(journal.reserve(pos,UUID.randomUUID(),owner,"minecraft:air","minecraft:stone",9_000));
            journal.applied(pos,op);
            assertTrue(journal.mayRestore(pos,op,"minecraft:air"));
            journal.invalidate(pos);
            assertFalse(journal.mayRestore(pos,op,"minecraft:air"));
        }
        try(var store=new PersistentStore(path)) {
            var journal=new MagicTerrainJournal(store);
            assertFalse(journal.mayRestore(pos,op,"minecraft:air"));
        }
    }
    @Test void preparedOrMismatchedTerrainIsNeverRestoredAndAppliedRecordsSurviveRestart() {
        UUID world=UUID.randomUUID(),owner=UUID.randomUUID(),op=UUID.randomUUID();
        var pos=new MagicTerrainJournal.Position(world,17,-40,19);Path path=folder.resolve("terrain.db");
        try(var store=new PersistentStore(path)) {
            var journal=new MagicTerrainJournal(store);
            journal.reserve(pos,op,owner,"minecraft:deepslate","minecraft:air",99_000);
            assertFalse(journal.mayRestore(pos,op,"minecraft:air"));
            journal.applied(pos,op);
        }
        try(var store=new PersistentStore(path)) {
            var journal=new MagicTerrainJournal(store);
            assertTrue(journal.mayRestore(pos,op,"minecraft:air"));
            assertFalse(journal.mayRestore(pos,op,"minecraft:stone"));
            assertFalse(journal.mayRestore(pos,UUID.randomUUID(),"minecraft:air"));
            assertEquals(1,journal.count(owner));
            assertEquals("minecraft:deepslate",journal.records().get(pos).original());
        }
    }
    @Test void flightOverlapsRetainOriginalBaselineAndExpireOnlyAfterLastLease() {
        Path path=folder.resolve("flight.db");UUID p=UUID.randomUUID();
        try(var store=new PersistentStore(path)) {
            var leases=new MagicFlightState(store);
            leases.grant(p,false,false,"SURVIVAL",1_000,20_000);
            leases.grant(p,true,true,"SURVIVAL",2_000,30_000);
            assertFalse(leases.get(p).allowFlight());
            assertFalse(leases.get(p).flying());
            assertEquals(32_000,leases.get(p).expiry());
        }
        try(var store=new PersistentStore(path)) {
            var leases=new MagicFlightState(store);
            assertFalse(leases.expired(p,31_999));
            assertTrue(leases.expired(p,32_000));
            leases.remove(p);assertNull(leases.get(p));
        }
    }
    @Test void failedTerrainReservationBatchRollsBackMemoryAndDurableOwnershipTogether(){
        try(var store=new PersistentStore(folder.resolve("batch.db"))){var journal=new MagicTerrainJournal(store);var owner=UUID.randomUUID();var position=new MagicTerrainJournal.Position(UUID.randomUUID(),2,64,2);
            assertThrows(IllegalStateException.class,()->journal.batch(()->{journal.reserve(position,UUID.randomUUID(),owner,"minecraft:stone","minecraft:air",1000);throw new IllegalStateException("injected reservation failure");}));
            assertTrue(journal.records().isEmpty());assertTrue(store.entries("magic.terrain").isEmpty());
        }
    }
}
