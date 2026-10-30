package dev.duckmc.blastforge.expansion;

import net.kyori.adventure.nbt.BinaryTagIO;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import java.nio.file.*;
import java.util.UUID;

/** Paper saveData can fail silently. Read back a fresh nonce before acknowledging. */
public final class PlayerSaveCheckpoint {
    private static final NamespacedKey NONCE=new NamespacedKey("blastforge","save_nonce");
    private PlayerSaveCheckpoint(){}
    public static void save(Player player){
        String nonce=UUID.randomUUID().toString();
        player.getPersistentDataContainer().set(NONCE,PersistentDataType.STRING,nonce);
        player.saveData();
        Path file=Bukkit.getWorlds().getFirst().getWorldFolder().toPath().resolve("playerdata").resolve(player.getUniqueId()+".dat");
        if(!matches(file,nonce))throw new IllegalStateException("Player save could not be verified for "+player.getUniqueId()+"; escrow retained. Check player saving and disk errors.");
    }
    static boolean matches(Path file,String nonce){
        try{return nonce.equals(BinaryTagIO.reader(64L*1024*1024).read(file,BinaryTagIO.Compression.GZIP).getCompound("BukkitValues").getString(NONCE.toString()));}
        catch(Exception error){return false;}
    }
}
