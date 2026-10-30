package dev.duckmc.blastforge.expansion;

import com.google.common.collect.ArrayListMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Function;

/** Authoritative item identity. Names and resource-pack components never grant an identity. */
public final class ExpansionItems {
    static final NamespacedKey KIND=key("item_kind"), TIER=key("material_tier");
    static final List<String> PIECES=List.of("sword","pickaxe","axe","shovel","hoe","helmet","chestplate","leggings","boots","spear");
    static final List<String> TIERS=List.of("siftie","enderite","wardenite");
    private static final Map<String,Material> BASES;
    static {
        var map=new LinkedHashMap<String,Material>();
        map.put("enchanting_anvil",Material.ANVIL); map.put("forging_book",Material.ENCHANTED_BOOK);
        // No vanilla cooking recipe accepts this carrier, including a furnace's cached prior recipe.
        map.put("raw_siftie",Material.PRISMARINE_CRYSTALS); map.put("siftie_ingot",Material.IRON_INGOT);
        map.put("siftie_ore",Material.DEEPSLATE_DIAMOND_ORE); map.put("siftie_template",Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        map.put("enderite_scrap",Material.NETHERITE_SCRAP); map.put("enderite_ingot",Material.NETHERITE_INGOT);
        map.put("enderite_ore",Material.ANCIENT_DEBRIS); map.put("enderite_template",Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        map.put("enderite_stick",Material.STICK); map.put("wardenite_scrap",Material.NETHERITE_SCRAP);
        map.put("wardenite_ingot",Material.NETHERITE_INGOT); map.put("wardenite_template",Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        map.put("dragon_heart",Material.DRAGON_BREATH); map.put("warden_heart",Material.HEART_OF_THE_SEA); map.put("bat_wing",Material.PHANTOM_MEMBRANE);
        for(String book:List.of("fire","water","earth","wind","darkness"))map.put(book+"_book",Material.ENCHANTED_BOOK);
        map.put("legendary_scythe",Material.NETHERITE_SWORD);
        for(String tier:TIERS)for(String piece:PIECES)map.put(tier+"_"+piece,Material.valueOf("NETHERITE_"+piece.toUpperCase(Locale.ROOT)));
        for(String model:List.of("anvil_hammer","anvil_tools","magician_robes","miner_helmet","miner_backpack"))map.put(model,Material.PAPER);map.put("magician_hat",Material.PAPER);for(String t:List.of("tab_forging","tab_upgrading","tab_ritual","tab_rename","tab_close","guide_button"))map.put(t,Material.PAPER);
        BASES=Collections.unmodifiableMap(map);
    }
    private final Function<String,FileConfiguration> configs;
    public ExpansionItems(JavaPlugin plugin,Function<String,FileConfiguration> configs) { this.configs=Objects.requireNonNull(configs); }
    static NamespacedKey key(String key) { return new NamespacedKey("blastforge",key); }
    public List<String> ids() { return List.copyOf(BASES.keySet()); }
    public String id(ItemStack stack) {
        if(stack==null || stack.getType().isAir() || !stack.hasItemMeta())return "";
        String id=stack.getItemMeta().getPersistentDataContainer().get(KIND,PersistentDataType.STRING);
        // Reject copied identifiers on incompatible carrier items, including incorrectly renamed gear.
        return id!=null && BASES.get(id)==stack.getType()?id:"";
    }
    public ItemStack create(String id) {
        Material material=BASES.get(id);
        if(material==null)throw new IllegalArgumentException("Unknown The Forge item: "+id);
        ItemStack stack=new ItemStack(material);
        ItemMeta meta=stack.getItemMeta();
        meta.displayName(Component.text(title(id),color(id)).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(Component.text(description(id),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
        mark(meta,id);
        String tier=tier(id),piece=piece(id);
        if(!tier.isEmpty() && !piece.isEmpty())equipment(meta,material,tier,piece);
        if(id.equals("legendary_scythe")) {
            ((Damageable)meta).setMaxDamage(Math.max(1,configs.apply("scythe").getInt("durability",5000)));
            attributes(meta,"scythe","sword",EquipmentSlotGroup.MAINHAND,10,1.6,0,0,1);
            meta.setMaxStackSize(1);
        }
        stack.setItemMeta(meta);
        return stack;
    }
    private void mark(ItemMeta meta,String id) {
        meta.getPersistentDataContainer().set(KIND,PersistentDataType.STRING,id);
        meta.getPersistentDataContainer().set(key("item_schema"),PersistentDataType.INTEGER,1);
        meta.setCustomModelData(ItemModels.number(id));
        String tier=tier(id);
        if(!tier.isEmpty())meta.getPersistentDataContainer().set(TIER,PersistentDataType.STRING,tier);
    }
    /** Returns a new stack and rejects skipped tiers before any input is consumed. */
    public ItemStack upgrade(ItemStack input,String target) {
        Objects.requireNonNull(input,"equipment");
        String piece=equipmentPiece(input),previous=equipmentTier(input);
        List<String> progression=List.of("diamond","netherite","siftie","enderite","wardenite");
        int next=progression.indexOf(target);
        if(input.getAmount()!=1 || piece.isEmpty() || next<1 || !progression.get(next-1).equals(previous))
            throw new IllegalArgumentException("Upgrade requires exactly one equipment item from the previous tier");
        ItemStack result=input.clone();
        ItemMeta source=input.getItemMeta();
        int oldMax=maxDamage(input),oldDamage=source instanceof Damageable d?d.getDamage():0;
        result.setType(Material.valueOf("NETHERITE_"+piece.toUpperCase(Locale.ROOT)));
        // Same equipment category and Netherite backing preserve supported components, unknown PDC and enchantments.
        result.setItemMeta(source);
        ItemMeta meta=result.getItemMeta();
        if(target.equals("netherite")) {
            if(meta instanceof Damageable damageable)damageable.setMaxDamage(null);
        } else {
            String id=target+"_"+piece;
            // Only replace the catalog's own display name; player/third-party names remain untouched.
            String oldId=id(input);
            if(!meta.hasDisplayName() || (!oldId.isEmpty() && Objects.equals(meta.displayName(),create(oldId).getItemMeta().displayName())))
                meta.displayName(Component.text(title(id),color(id)).decoration(TextDecoration.ITALIC,false));
            if(!oldId.isEmpty() && Objects.equals(meta.lore(),create(oldId).getItemMeta().lore()))
                meta.lore(List.of(Component.text(description(id),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
            mark(meta,id); equipment(meta,result.getType(),target,piece);
        }
        if(meta instanceof Damageable damageable) {
            int newMax=damageable.hasMaxDamage()?damageable.getMaxDamage():result.getType().getMaxDurability();
            damageable.setDamage(Math.clamp((int)Math.round((double)oldDamage*newMax/Math.max(1,oldMax)),0,Math.max(0,newMax-1)));
        }
        result.setItemMeta(meta); return result;
    }
    private void equipment(ItemMeta meta,Material base,String tier,String piece) {
        var cfg=configs.apply("materials"); String path="tiers."+tier+".";
        int rank=TIERS.indexOf(tier);
        boolean armor=isArmor(piece);
        int max=armor?cfg.getInt(path+"armor-durability."+piece,base.getMaxDurability()):cfg.getInt(path+"tool-durability",2500+500*rank);
        ((Damageable)meta).setMaxDamage(Math.max(1,max)); meta.setMaxStackSize(1);
        // Wardenite gear shimmers like an enchanted item (configurable: tiers.<tier>.glint).
        meta.setEnchantmentGlintOverride(glint(tier,piece)?Boolean.TRUE:null);
        EquipmentSlot slot=switch(piece) { case "helmet"->EquipmentSlot.HEAD;case "chestplate"->EquipmentSlot.CHEST;case "leggings"->EquipmentSlot.LEGS;case "boots"->EquipmentSlot.FEET;default->EquipmentSlot.HAND; };
        EquipmentSlotGroup group=slot.getGroup();
        // Defaults are the Siftie stats as shown in the tooltip; higher tiers add to the sword. All are overridable in materials.yml.
        double damage=switch(piece) {case "sword"->9+rank;case "axe"->10;case "pickaxe"->7;case "shovel"->6.5;case "hoe"->2;case "spear"->6;default->1;};
        double speed=switch(piece) {case "sword"->1.7;case "axe"->1.2;case "pickaxe"->1;case "shovel"->1;case "hoe"->4.5;case "spear"->1;default->4;};
        damage=piece.equals("sword")?cfg.getDouble(path+"sword-damage",damage):cfg.getDouble(path+"attack-damage."+piece,damage);
        speed=cfg.getDouble(path+"attack-speed."+piece,speed);
        double points=cfg.getDouble(path+"armor-points."+piece,switch(piece) {case "chestplate"->9;case "leggings"->7;case "helmet","boots"->4;default->0;});
        knockback=perPiece(cfg,path+"knockback-resistance",piece,.15); // tooltip shows this x10: 0.15 = "+1.5 Knockback Resistance"
        attributes(meta,tier,piece,group,damage,speed,armor?points:0,armor?cfg.getDouble(path+"armor-toughness",4+rank*.5):0,
            armor?1:perPiece(cfg,path+"mining-speed",piece,tier.equals("siftie")?1.15:1));
        if(armor) {
            // The netherite carrier already equips into the right slot. A custom override here made every piece go on the head.
            meta.setEquippable(null);
        }
    }
    private double knockback=.15;
    /** A setting can be one number for every piece, or a map with a value per piece (missing pieces use the fallback). */
    private static double perPiece(org.bukkit.configuration.file.FileConfiguration cfg,String path,String piece,double fallback){
        return cfg.isConfigurationSection(path)?cfg.getDouble(path+"."+piece,fallback):cfg.getDouble(path,fallback);
    }
    private void attributes(ItemMeta meta,String tier,String piece,EquipmentSlotGroup group,double damage,double speed,double armor,double toughness,double mining) {
        var modifiers=ArrayListMultimap.<Attribute,AttributeModifier>create();
        var old=meta.getAttributeModifiers();
        // Vanilla/base and our prior tier stats are replaced; unrelated plugin modifiers are retained verbatim.
        if(old!=null)old.forEach((attribute,modifier)->{
            NamespacedKey key=modifier.getKey();String name=key.getKey();
            boolean vanilla=key.getNamespace().equals("minecraft") && (name.equals("base_attack_damage") || name.equals("base_attack_speed") || name.equals("armor."+piece));
            boolean ours=key.getNamespace().equals("blastforge") && (Set.of("equipment_damage","equipment_speed","equipment_mining").contains(name) || name.equals(piece+"_armor") || name.equals(piece+"_toughness") || name.equals(piece+"_knockback"));
            if(!vanilla && !ours)modifiers.put(attribute,modifier);
        });
        meta.setAttributeModifiers(modifiers);
        if(isArmor(piece)) {
            add(meta,Attribute.ARMOR,piece+"_armor",Math.max(0,armor),group,AttributeModifier.Operation.ADD_NUMBER);
            add(meta,Attribute.ARMOR_TOUGHNESS,piece+"_toughness",Math.max(0,toughness),group,AttributeModifier.Operation.ADD_NUMBER);
            add(meta,Attribute.KNOCKBACK_RESISTANCE,piece+"_knockback",Math.clamp(knockback,0,1),group,AttributeModifier.Operation.ADD_NUMBER);
        } else {
            add(meta,Attribute.ATTACK_DAMAGE,"equipment_damage",Math.max(0,damage-1),group,AttributeModifier.Operation.ADD_NUMBER);
            add(meta,Attribute.ATTACK_SPEED,"equipment_speed",Math.max(-4,speed-4),group,AttributeModifier.Operation.ADD_NUMBER);
            if(mining!=1)add(meta,Attribute.BLOCK_BREAK_SPEED,"equipment_mining",Math.max(-.99,mining-1),group,AttributeModifier.Operation.ADD_SCALAR);
        }
    }
    private static void add(ItemMeta meta,Attribute attribute,String id,double value,EquipmentSlotGroup slot,AttributeModifier.Operation operation) {
        meta.addAttributeModifier(attribute,new AttributeModifier(key(id),value,operation,slot));
    }
    static int maxDamage(ItemStack stack) {return stack.getItemMeta() instanceof Damageable d && d.hasMaxDamage()?d.getMaxDamage():stack.getType().getMaxDurability();}
    static boolean isArmor(String piece) {return List.of("helmet","chestplate","leggings","boots").contains(piece);}
    static String tier(String id) {for(String tier:TIERS)if(id.startsWith(tier+"_"))return tier;return "";}
    static String piece(String id) {for(String piece:PIECES)if(id.endsWith("_"+piece))return piece;return "";}
    public String equipmentPiece(ItemStack stack) {return stack==null?"":piece(stack.getType().name().toLowerCase(Locale.ROOT));}
    public String equipmentTier(ItemStack stack) {
        if(stack==null)return "";String id=id(stack);if(!id.isEmpty())return piece(id).isEmpty()?"":tier(id);
        // Ordinary Netherite/diamond equipment is valid; unknown marked items are not treated as vanilla.
        if(stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(KIND))return "";
        String name=stack.getType().name();return name.startsWith("NETHERITE_")?"netherite":name.startsWith("DIAMOND_")?"diamond":"";
    }
    /** Removes the broken equip override from armor made by rc.11 and earlier. Returns true if the item was changed. */
    public boolean repairEquippable(ItemStack stack){
        if(stack==null)return false;String id=id(stack),piece=piece(id);
        if(id.equals("enchanting_anvil")&&stack.hasItemMeta()&&stack.getItemMeta().hasDisplayName()&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName()).equals("Enchanting Anvil")){
            stack.editMeta(m->m.displayName(create("enchanting_anvil").getItemMeta().displayName()));return true;}   /* renamed to The Forging Anvil */
        if(piece.isEmpty()||tier(id).isEmpty())return false;boolean changed=false;
        if(isArmor(piece)&&stack.getItemMeta().hasEquippable()){stack.editMeta(m->m.setEquippable(null));changed=true;}
        /* Bring the glint of existing items in line with the current setting (the helmet has none by default). */
        String tier=tier(id);Boolean want=glint(tier,piece)?Boolean.TRUE:null;var meta=stack.getItemMeta();
        Boolean have=meta.hasEnchantmentGlintOverride()?meta.getEnchantmentGlintOverride():null;
        if(!java.util.Objects.equals(want,have)){stack.editMeta(m->m.setEnchantmentGlintOverride(want));changed=true;}
        return changed;
    }
    /** tiers.<tier>.glint turns the armor shimmer on; helmet-glint and tool-glint (both off by default) extend it to the helmet and to tools/weapons. */
    boolean glint(String tier,String piece){
        if(tier==null||tier.isEmpty())return false;var cfg=configs.apply("materials");String path="tiers."+tier+".";
        if(!cfg.getBoolean(path+"glint",tier.equals("wardenite")))return false;
        if(!isArmor(piece))return cfg.getBoolean(path+"tool-glint",false);
        return !piece.equals("helmet")||cfg.getBoolean(path+"helmet-glint",false);
    }
    public boolean canHarvest(ItemStack tool,String oreTier) {
        if(tool==null || !equipmentPiece(tool).equals("pickaxe"))return false;
        int rank=List.of("diamond","netherite","siftie","enderite","wardenite").indexOf(equipmentTier(tool));
        return switch(oreTier) {case "siftie","siftie_ore"->rank>=0;case "enderite","enderite_ore"->rank>=1;default->false;}; // Siftie: diamond pickaxe or better. Enderite: netherite pickaxe or better.
    }
    static String title(String id) {
        if(id.equals("forging_book"))return "Book of Forging";if(id.equals("enchanting_anvil"))return "The Forging Anvil";if(id.equals("enderite_scrap"))return "Raw Enderite";if(id.equals("darkness_book"))return "Book of Darkness";
        if(id.endsWith("_template"))return title(id.replace("_template",""))+" Upgrade Template";
        StringJoiner out=new StringJoiner(" ");for(String s:id.split("_"))out.add(s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1));return out.toString();
    }
    private static NamedTextColor color(String id) {if(id.startsWith("siftie"))return NamedTextColor.AQUA;if(id.startsWith("enderite"))return NamedTextColor.LIGHT_PURPLE;if(id.startsWith("wardenite"))return NamedTextColor.DARK_AQUA;return NamedTextColor.GOLD;}
    private static String description(String id) {
        if(id.endsWith("_book") && !id.equals("forging_book"))return "Hold to use magic; keep in your inventory for passives.";
        if(id.endsWith("_template"))return "Consumed with the previous equipment tier and a matching ingot.";
        if(id.equals("legendary_scythe"))return "Dash · Flight · Windmill. Created only by the Dark Ritual.";
        if(id.endsWith("_ore"))return "Owned custom ore. Silk Touch preserves the ore for placement.";
        return "The Forge · "+title(id);
    }
}
