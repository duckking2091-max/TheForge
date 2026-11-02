package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Recipe shapes are discoverable, while every actual consumption validates PDC identity. */
final class MaterialRecipes implements Listener,AutoCloseable {
    private final JavaPlugin plugin; private final ExpansionItems items;
    private final Map<String,String[]> shapes=new LinkedHashMap<>();
    private final Map<String,Map<Character,String>> ingredients=new HashMap<>();
    private final List<NamespacedKey> keys=new ArrayList<>();
    MaterialRecipes(JavaPlugin plugin,ExpansionItems items) {
        this.plugin=plugin;this.items=items;
        define("siftie_template",new String[]{"IBI","BTB","IBI"},Map.of('I',"siftie_ingot",'B',"BLAZE_ROD",'T',"NETHERITE_UPGRADE_SMITHING_TEMPLATE"));
        define("enderite_template",new String[]{" S ","STS"," S "},Map.of('S',"enderite_scrap",'T',"siftie_template"));
        define("enderite_stick",new String[]{"I","I"},Map.of('I',"enderite_ingot"));
        define("wardenite_ingot",new String[]{"SSS","SES","SSS"},Map.of('S',"wardenite_scrap",'E',"ECHO_SHARD"));
        define("enchanting_anvil",new String[]{"IBI"," A ","DDD"},Map.of('I',"IRON_BLOCK",'B',"forging_book",'A',"ANVIL",'D',"DIAMOND"));
    }
    private void define(String id,String[] shape,Map<Character,String> components) {shapes.put(id,shape);ingredients.put(id,components);}
    void start() {
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        // tiers turned off in materials.yml lose their recipes; Enderite's template uses a netherite one while Siftie is off
        if(!items.tierEnabled("siftie"))ingredients.put("enderite_template",Map.of('S',"enderite_scrap",'T',"NETHERITE_UPGRADE_SMITHING_TEMPLATE"));
        for(var entry:shapes.entrySet()) {
            if(!enabled(entry.getKey()))continue;
            // Keep the legacy anvil recipe key so previously discovered recipes still resolve.
            NamespacedKey key=key(entry.getKey());plugin.getServer().removeRecipe(key);
            ShapedRecipe recipe=new ShapedRecipe(key,items.create(entry.getKey()));recipe.shape(entry.getValue());
            ingredients.get(entry.getKey()).forEach((symbol,id)->recipe.setIngredient(symbol,carrier(id)));
            plugin.getServer().addRecipe(recipe);keys.add(key);
        }
        NamespacedKey darkness=key("darkness_book");plugin.getServer().removeRecipe(darkness);
        ShapelessRecipe recipe=new ShapelessRecipe(darkness,items.create("darkness_book"));
        for(int i=0;i<4;i++)recipe.addIngredient(Material.ENCHANTED_BOOK);
        plugin.getServer().addRecipe(recipe);keys.add(darkness);
        if(items.tierEnabled("siftie"))smelting("raw_siftie","siftie_ingot");if(items.tierEnabled("enderite"))smelting("enderite_scrap","enderite_ingot");
    }
    /** A recipe exists only while the tier it makes is turned on. */
    private boolean enabled(String recipe){
        for(String tier:List.of("siftie","enderite","wardenite"))if(recipe.startsWith(tier+"_"))return items.tierEnabled(tier);
        return true;
    }
    private NamespacedKey key(String id) {return new NamespacedKey("blastforge",id.equals("enchanting_anvil")?id:"materials_"+id);}
    private void smelting(String from,String to) {
        NamespacedKey furnace=key(from+"_smelt"),blast=key(from+"_blast");
        plugin.getServer().removeRecipe(furnace);plugin.getServer().removeRecipe(blast);
        // Isolated carriers have no vanilla cooking recipes. Their constant custom result is
        // correct before native canBurn checks; PDC guards permit legitimate presentation changes.
        // Raw Iron cannot be used here: its cached vanilla recipe also accepts marked Raw Iron.
        Material carrier=items.create(from).getType();
        plugin.getServer().addRecipe(new FurnaceRecipe(furnace,items.create(to),new RecipeChoice.MaterialChoice(carrier),.7f,200));
        plugin.getServer().addRecipe(new BlastingRecipe(blast,items.create(to),new RecipeChoice.MaterialChoice(carrier),.7f,100));
        keys.add(furnace);keys.add(blast);
    }
    private Material carrier(String id) {Material vanilla=Material.getMaterial(id);return vanilla!=null?vanilla:items.create(id).getType();}
    boolean matches(String recipe,ItemStack[] matrix) {
        if(!enabled(recipe))return false;
        if(matrix==null || (matrix.length!=4 && matrix.length!=9))return false;
        if(recipe.equals("darkness_book")) {
            Set<String> required=new HashSet<>(List.of("fire_book","water_book","earth_book","wind_book"));int count=0;
            for(ItemStack item:matrix)if(!empty(item)) {count++;if(!required.remove(items.id(item)))return false;}
            return count==4 && required.isEmpty();
        }
        String[] shape=shapes.get(recipe);if(shape==null)return false;
        int size=matrix.length==9?3:2,minX=size,minY=size,maxX=-1,maxY=-1;
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)if(!empty(matrix[y*size+x])) {minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        if(maxX-minX+1!=shape[0].length() || maxY-minY+1!=shape.length)return false;
        for(int y=0;y<shape.length;y++)for(int x=0;x<shape[0].length();x++) {
            char symbol=shape[y].charAt(x);ItemStack item=matrix[(y+minY)*size+x+minX];
            if(symbol==' ') {if(!empty(item))return false;} else if(!ingredient(item,ingredients.get(recipe).get(symbol)))return false;
        }
        return true;
    }
    private boolean ingredient(ItemStack item,String expected) {
        if(empty(item))return false;Material material=Material.getMaterial(expected);
        if(material==null)return expected.equals(items.id(item));
        return item.getType()==material && !marked(item);
    }
    private boolean marked(ItemStack item) {return item!=null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(ExpansionItems.KIND);}
    private static boolean empty(ItemStack item) {return item==null || item.getType().isAir() || item.getAmount()<1;}
    private String recipeId(Recipe recipe) {
        if(!(recipe instanceof Keyed keyed) || !keyed.getKey().getNamespace().equals("blastforge"))return "";
        String id=keyed.getKey().getKey();return id.equals("enchanting_anvil")?id:id.startsWith("materials_")?id.substring(10):"";
    }
    private boolean valid(Recipe recipe,ItemStack[] grid) {
        String id=recipeId(recipe);
        if(!id.isEmpty())return matches(id,grid);
        // Marked ingredients cannot be laundered into unrelated vanilla crafting outputs.
        for(ItemStack input:grid)if(marked(input))return false;
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {if(!valid(event.getRecipe(),event.getInventory().getMatrix()))event.getInventory().setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent event) {if(!valid(event.getRecipe(),event.getInventory().getMatrix()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void automatic(CrafterCraftEvent event) {
        if(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter && !valid(event.getRecipe(),crafter.getInventory().getContents()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(FurnaceBurnEvent event) {
        if(!(event.getBlock().getState() instanceof org.bukkit.block.Furnace furnace))return;
        ItemStack source=furnace.getInventory().getSmelting();
        if(source==null)return;
        String expected=switch(source.getType()) {
            case PRISMARINE_CRYSTALS->"raw_siftie";
            case NETHERITE_SCRAP->"enderite_scrap";
            default->"";
        };
        if(!expected.isEmpty() && !expected.equals(items.id(source)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void smelt(FurnaceSmeltEvent event) {
        String selected=recipeId(event.getRecipe());
        String expected=switch(selected) {
            case "raw_siftie_smelt","raw_siftie_blast"->"raw_siftie";
            case "enderite_scrap_smelt","enderite_scrap_blast"->"enderite_scrap";
            default->"";
        };
        if(expected.isEmpty()) {
            // A marked source must not fall through a vanilla carrier recipe, even after renaming.
            if(marked(event.getSource()) || marked(event.getResult()))event.setCancelled(true);
            return;
        }
        String output=expected.equals("raw_siftie")?"siftie_ingot":"enderite_ingot";
        CookingRecipe<?> recipe=event.getRecipe();
        if(!expected.equals(items.id(event.getSource())) || recipe==null || !recipe.getInputChoice().test(event.getSource())
            || !output.equals(items.id(event.getResult())) || !recipe.getResult().isSimilar(event.getResult()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void smithing(PrepareSmithingEvent event) {
        for(ItemStack input:event.getInventory().getContents())if(marked(input)){event.setResult(null);break;}
    }
    @Override public void close() {HandlerList.unregisterAll(this);for(NamespacedKey key:keys)plugin.getServer().removeRecipe(key);keys.clear();}
}
