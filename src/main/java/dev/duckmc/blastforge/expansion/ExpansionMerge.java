package dev.duckmc.blastforge.expansion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.util.*;

public final class ExpansionMerge {
    private ExpansionMerge(){}
    public static String family(ItemStack item){
        if(item==null)return "";String name=item.getType().name();int split=name.lastIndexOf('_');if(split<0)return "";
        String suffix=name.substring(split+1);return Set.of("SWORD","PICKAXE","AXE","SHOVEL","HOE","HELMET","CHESTPLATE","LEGGINGS","BOOTS","SPEAR").contains(suffix)?suffix:"";
    }
    public static ItemStack merge(ItemStack base,ItemStack donor){
        if(base==null||donor==null||base.getAmount()!=1||donor.getAmount()!=1||family(base).isEmpty())return null;
        boolean book=donor.getItemMeta() instanceof EnchantmentStorageMeta;
        if(!book&&!family(base).equals(family(donor)))return null;
        var result=base.clone();var enchants=book?((EnchantmentStorageMeta)donor.getItemMeta()).getStoredEnchants():donor.getEnchantments();
        for(var entry:enchants.entrySet()){
            var enchant=entry.getKey();if(!enchant.canEnchantItem(result))continue;
            int current=result.getEnchantmentLevel(enchant),next=Math.max(current,Math.min(enchant.getMaxLevel(),entry.getValue()));
            if(next>current)result.addUnsafeEnchantment(enchant,next);
        }
        return result.equals(base)?null:result;
    }
}
