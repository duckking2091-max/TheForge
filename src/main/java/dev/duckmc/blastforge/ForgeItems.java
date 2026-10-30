package dev.duckmc.blastforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.plugin.java.JavaPlugin;

final class ForgeItems implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey kind;
    private final Enchantment blast;
    ForgeItems(JavaPlugin plugin,Enchantment blast) { this.plugin=plugin; this.blast=blast; kind=new NamespacedKey(plugin,"item_kind"); }
    ItemStack book() { return marked(Material.ENCHANTED_BOOK,"forging_book","Book of Forging","Used to craft The Forging Anvil."); }
    ItemStack anvil() { return marked(Material.ANVIL,"enchanting_anvil","The Forging Anvil","Merge enchantments across tool materials."); }
    private ItemStack marked(Material type,String value,String name,String lore) {
        ItemStack stack=new ItemStack(type);
        stack.editMeta(meta -> {
            meta.setCustomModelData(dev.duckmc.blastforge.expansion.ItemModels.number(value));
            meta.displayName(Component.text(name,NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));
            meta.lore(List.of(Component.text(lore,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
            meta.getPersistentDataContainer().set(kind,PersistentDataType.STRING,value);
        });
        return stack;
    }
    boolean isAnvil(ItemStack stack) { return isKind(stack,"enchanting_anvil") && stack.getType()==Material.ANVIL; }
    private boolean isKind(ItemStack stack,String value) {
        return stack!=null && stack.hasItemMeta() && value.equals(stack.getItemMeta().getPersistentDataContainer().get(kind,PersistentDataType.STRING));
    }
    ItemStack blastBook(int level) {
        ItemStack stack=new ItemStack(Material.ENCHANTED_BOOK);
        stack.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(blast,level,false));
        return stack;
    }
    void registerRecipe() {
        ShapedRecipe recipe=new ShapedRecipe(new NamespacedKey(plugin,"enchanting_anvil"),anvil());
        recipe.shape("IBI"," A ","DDD");
        recipe.setIngredient('I',Material.IRON_BLOCK);
        recipe.setIngredient('B',new RecipeChoice.ExactChoice(book()));
        recipe.setIngredient('A',Material.ANVIL);
        recipe.setIngredient('D',Material.DIAMOND);
        plugin.getServer().addRecipe(recipe);
    }
    @EventHandler(ignoreCancelled=true)
    public void trade(VillagerAcquireTradeEvent event) {
        if(!(event.getEntity() instanceof Villager v) || v.getProfession()!=Villager.Profession.LIBRARIAN) return;
        if(v.getPersistentDataContainer().has(new NamespacedKey("blastforge","villager_kind"),PersistentDataType.STRING))return;
        var random=ThreadLocalRandom.current();
        double roll=random.nextDouble();
        // New survival forging books come from the Wandering Miner; locked old trades remain.
        double forging=0;
        double blastChance=Math.clamp(plugin.getConfig().getDouble("blast-book-trade-chance",Rarity.BLAST_CHANCE),0,1-forging);
        ItemStack result;
        int price;
        if(roll<forging) {
            result=book(); price=plugin.getConfig().getInt("forging-book-emeralds",24);
        } else if(roll<forging+blastChance) {
            int level=Rarity.level(plugin.getConfig(),random.nextDouble()); result=blastBook(level);
            price=plugin.getConfig().getInt("blast-book-base-emeralds",12)*level;
        } else return;
        MerchantRecipe recipe=new MerchantRecipe(result,0,12,true,5,0.2f);
        recipe.addIngredient(new ItemStack(Material.EMERALD,Math.clamp(price,1,64)));
        recipe.addIngredient(new ItemStack(Material.BOOK));
        event.setRecipe(recipe);
    }
}
