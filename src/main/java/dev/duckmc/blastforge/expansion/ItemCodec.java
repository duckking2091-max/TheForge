package dev.duckmc.blastforge.expansion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.*;
import java.util.*;

/** Trusted local database serialization, not an external input format. */
public final class ItemCodec {
    private ItemCodec(){}
    public static String encode(Collection<ItemStack> items){
        try(var bytes=new ByteArrayOutputStream();var out=new BukkitObjectOutputStream(bytes)){
            out.writeInt(items.size());for(ItemStack item:items)out.writeObject(item);out.flush();return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException e){throw new IllegalStateException("Cannot save item escrow",e);}
    }
    public static List<ItemStack> decode(String value){
        try(var in=new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)))){
            int size=in.readInt();if(size<0||size>256)throw new IOException("Invalid escrow size");
            var result=new ArrayList<ItemStack>();for(int i=0;i<size;i++){Object item=in.readObject();if(!(item instanceof ItemStack stack))throw new IOException("Invalid escrow item");result.add(stack);}return result;
        }catch(IOException|ClassNotFoundException|IllegalArgumentException e){throw new IllegalStateException("Cannot recover item escrow",e);}
    }
}
