package dev.duckmc.blastforge.expansion;
import net.kyori.adventure.nbt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class PlayerSaveCheckpointTest {
    @TempDir Path folder;
    @Test void savedReceiptMustMatchTheFreshNonce()throws Exception{
        Path file=folder.resolve("player.dat");
        var values=CompoundBinaryTag.builder().putString("blastforge:save_nonce","fresh").build();
        BinaryTagIO.writer().write(CompoundBinaryTag.builder().put("BukkitValues",values).build(),file,BinaryTagIO.Compression.GZIP);
        assertTrue(PlayerSaveCheckpoint.matches(file,"fresh"));assertFalse(PlayerSaveCheckpoint.matches(file,"stale"));
    }
    @Test void absentOrCorruptPlayerFileCannotAcknowledgeSave()throws Exception{
        Path file=folder.resolve("player.dat");assertFalse(PlayerSaveCheckpoint.matches(file,"fresh"));
        Files.writeString(file,"broken");assertFalse(PlayerSaveCheckpoint.matches(file,"fresh"));
    }
}
