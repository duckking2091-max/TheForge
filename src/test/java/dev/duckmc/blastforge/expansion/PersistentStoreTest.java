package dev.duckmc.blastforge.expansion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PersistentStoreTest {
    @TempDir Path folder;
    @Test void kvAndCooldownsSurviveReopen(){
        Path file=folder.resolve("state.db");var player=UUID.randomUUID();
        try(var store=new PersistentStore(file)){store.put("test","one","value");store.setCooldown(player,"fire",12345);}
        try(var store=new PersistentStore(file)){assertEquals("value",store.get("test","one"));assertEquals(12345,store.cooldown(player,"fire"));store.remove("test","one");assertNull(store.get("test","one"));}
    }
    @Test void capacityCountsReservationsAndOnlyPermanentBreakReleasesActiveSlot(){
        var first=UUID.randomUUID();var second=UUID.randomUUID();Path file=folder.resolve("state.db");
        try(var store=new PersistentStore(file)){assertTrue(store.reserveScythe(first,1));assertFalse(store.reserveScythe(second,1));store.activateScythe(first);store.releaseReservation(first);assertEquals(1,store.occupiedScythes());}
        try(var store=new PersistentStore(file)){assertEquals(1,store.occupiedScythes());assertTrue(store.destroyScythe(first));assertFalse(store.destroyScythe(first));assertTrue(store.reserveScythe(second,1));store.releaseReservation(second);assertEquals(0,store.occupiedScythes());}
    }
    @Test void committedCraftAtomicallyQueuesOutputAndCanBeRepeated(){
        UUID scythe=UUID.randomUUID(),owner=UUID.randomUUID(),tx=UUID.randomUUID();
        try(var store=new PersistentStore(folder.resolve("commit.db"))){
            assertTrue(store.reserveScythe(scythe,1));String payload="C\n"+owner+"\n5\n"+scythe+"\ninputs\noutputs";
            store.commitCraft(scythe,tx.toString(),payload);store.commitCraft(scythe,tx.toString(),payload);
            assertEquals("ACTIVE",store.scythes().get(scythe.toString()));assertEquals("P\n0\noutputs",store.get("mailbox",owner+":"+tx));
            assertEquals(payload,store.get("transactions",tx.toString()));
        }
    }
    @Test void failedBatchRollsBackEveryWrite(){
        try(var store=new PersistentStore(folder.resolve("batch.db"))){
            assertThrows(IllegalStateException.class,()->store.batch(()->{store.put("batch","one","value");store.put("batch","two","value");throw new IllegalStateException("injected");}));
            assertTrue(store.entries("batch").isEmpty());
            store.batch(()->{store.put("batch","one","value");store.put("batch","two","value");});assertEquals(2,store.entries("batch").size());
        }
    }
}
