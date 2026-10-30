package dev.duckmc.blastforge;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.util.Map;

record AnvilOperation(boolean custom,ItemStack result) {
    static AnvilOperation resolve(ItemStack base,ItemStack donor,ItemStack nativeResult) {
        if(Extraction.eligible(base,donor)) {
            ItemStack icon=new ItemStack(Material.ENCHANTED_BOOK);
            icon.editMeta(m->{
                m.displayName(Component.text("Choose enchantments to extract"));
                m.lore(java.util.List.of(Component.text("Click to select one or more enchantments."),
                    Component.text("One book receives your selection. No XP refund.")));
            });
            return new AnvilOperation(true,icon);
        }
        if(base==null || donor==null)return new AnvilOperation(false,nativeResult);
        boolean book=donor.getItemMeta() instanceof EnchantmentStorageMeta;
        String family=MergeRules.family(base.getType().name());
        boolean crossMaterial=!book && !family.isEmpty() && base.getType()!=donor.getType()
            && family.equals(MergeRules.family(donor.getType().name()));
        Map<Enchantment,Integer> enchants=book ? ((EnchantmentStorageMeta)donor.getItemMeta()).getStoredEnchants() : donor.getEnchantments();
        boolean diamondConflict=base.getType()==Material.DIAMOND_SWORD && enchants.keySet().stream()
            .filter(e->e.canEnchantItem(base))
            .anyMatch(e->java.util.stream.Stream.concat(base.getEnchantments().keySet().stream(),enchants.keySet().stream())
                .anyMatch(other->!e.equals(other) && other.canEnchantItem(base)
                    && (e.conflictsWith(other)||other.conflictsWith(e))));
        ItemStack merged=(crossMaterial||diamondConflict)?ForgeMerge.result(base,donor):null;
        if(merged==null)return new AnvilOperation(false,nativeResult);
        // Native same-material results already contain repair, rename and prior-work
        // metadata. Overlay the enhanced enchantments without merging levels twice.
        if(nativeResult!=null && !nativeResult.getType().isAir()) {
            ItemStack enhanced=nativeResult.clone();
            merged.getEnchantments().forEach((e,l)->{
                if(l>enhanced.getEnchantmentLevel(e))enhanced.addEnchantment(e,l);
            });
            merged=enhanced;
        }
        return new AnvilOperation(true,merged);
    }
}
