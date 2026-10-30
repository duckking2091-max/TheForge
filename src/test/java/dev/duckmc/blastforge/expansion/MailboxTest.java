package dev.duckmc.blastforge.expansion;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MailboxTest {
    @TempDir Path folder;
    @Test void quarantineIsTerminalEvenWhenSourceOrPendingRecordRemains(){
        var server=MockBukkit.mock();
        try(var store=new PersistentStore(folder.resolve("mail.db"))){
            var player=server.addPlayer();var plugin=MockBukkit.createMockPlugin();UUID tx=UUID.randomUUID();String key=player.getUniqueId()+":"+tx;
            var mailbox=new Mailbox(plugin,store,p->{throw new AssertionError("Must not mutate player");});
            store.put("quarantine",key,"uncertain delivery");
            mailbox.queue(player.getUniqueId(),tx,List.of(new ItemStack(Material.DIAMOND)),3);
            assertNull(store.get("mailbox",key));
            store.put("mailbox",key,"P\n3\n"+ItemCodec.encode(List.of(new ItemStack(Material.DIAMOND))));
            mailbox.deliver(player);assertNull(store.get("mailbox",key));assertTrue(player.getInventory().isEmpty());assertEquals(0,player.getLevel());
        }finally{MockBukkit.unmock();}
    }
}
