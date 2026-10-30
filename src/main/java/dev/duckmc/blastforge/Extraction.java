package dev.duckmc.blastforge;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.util.Set;

final class Extraction {
    record Result(ItemStack item,ItemStack book) {}
    static boolean eligible(ItemStack item,ItemStack book) {
        return item!=null && item.getAmount()==1 && !item.getEnchantments().isEmpty()
            && book!=null && book.getType()==Material.BOOK && book.getAmount()>0;
    }
    static Result extract(ItemStack source,Set<Enchantment> selected) {
        if(source==null || source.getAmount()!=1 || selected.isEmpty()
            || !source.getEnchantments().keySet().containsAll(selected)) return null;
        ItemStack item=source.clone(),book=new ItemStack(Material.ENCHANTED_BOOK);
        book.editMeta(EnchantmentStorageMeta.class,meta -> {
            for(Enchantment enchant:selected) meta.addStoredEnchant(enchant,source.getEnchantmentLevel(enchant),true);
        });
        selected.forEach(item::removeEnchantment);
        return new Result(item,book);
    }
}
