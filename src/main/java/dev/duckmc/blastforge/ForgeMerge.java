package dev.duckmc.blastforge;

import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import java.util.Comparator;
import org.bukkit.Material;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

final class ForgeMerge {
    static ItemStack result(ItemStack base,ItemStack donor) {
        if(base==null || donor==null || base.getAmount()!=1 || donor.getAmount()!=1) return null;
        String family=MergeRules.family(base.getType().name());
        boolean book=donor.getType()==Material.ENCHANTED_BOOK;
        if(family.isEmpty() || (!book && !family.equals(MergeRules.family(donor.getType().name())))) return null;
        ItemStack output=base.clone();
        // Stable order also makes the result deterministic for externally created
        // donors with mutually conflicting enchantments.
        var enchants=book ? ((EnchantmentStorageMeta)donor.getItemMeta()).getStoredEnchants() : donor.getEnchantments();
        for(var entry:enchants.entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getKey().getKey().toString())).toList()) {
            Enchantment enchant=entry.getKey();
            if(!enchant.canEnchantItem(output)) continue;
            if(base.getType()!=Material.DIAMOND_SWORD && output.getEnchantments().keySet().stream().anyMatch(existing -> !existing.equals(enchant)
                && (existing.conflictsWith(enchant) || enchant.conflictsWith(existing)))) continue;
            int current=output.getEnchantmentLevel(enchant);
            int next=MergeRules.level(current,entry.getValue(),enchant.getMaxLevel());
            if(next>current) output.addEnchantment(enchant,next);
        }
        return output.equals(base) ? null : output;
    }
}
