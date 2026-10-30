package dev.duckmc.blastforge.expansion;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class WorkshopRecipes {
    public record Upgrade(String tier,ItemStack item){}
    private WorkshopRecipes(){}
    public static ItemStack forge(ExpansionItems items,org.bukkit.configuration.file.FileConfiguration scythe,ItemStack base,ItemStack donor){
        if(base!=null&&items.id(base).equals("legendary_scythe")){
            if(donor!=null&&items.id(donor).equals("wardenite_ingot")){
                if(base.getAmount()!=1||donor.getAmount()!=1||!scythe.getBoolean("repair-enabled",true)||!(base.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damage)||damage.getDamage()==0)return null;
                var result=base.clone();int maximum=damage.hasMaxDamage()?damage.getMaxDamage():base.getType().getMaxDurability();
                int repair=Math.max(1,(int)Math.ceil(maximum*Math.clamp(scythe.getDouble("repair-fraction",.25),0,1)));
                result.editMeta(org.bukkit.inventory.meta.Damageable.class,m->m.setDamage(Math.max(0,damage.getDamage()-repair)));return result;
            }
            if(!scythe.getBoolean("enchanting-enabled",true))return null;
        }
        return ExpansionMerge.merge(base,donor);
    }
    /** Whether donor is the material the vanilla anvil uses to repair base (custom tiers use their own ingot). */
    public static boolean repairMaterial(ExpansionItems items,ItemStack base,ItemStack donor){
        if(base==null||donor==null)return false;String id=items.id(base),tier=ExpansionItems.tier(id);
        if(!tier.isEmpty())return items.id(donor).equals(tier+"_ingot");
        if(!items.id(donor).isEmpty())return false;
        String name=base.getType().name();Material material=donor.getType();
        if(name.startsWith("WOODEN_"))return org.bukkit.Tag.PLANKS.isTagged(material);
        if(name.startsWith("STONE_"))return material==Material.COBBLESTONE||material==Material.COBBLED_DEEPSLATE||material==Material.BLACKSTONE;
        if(name.startsWith("IRON_")||name.startsWith("CHAINMAIL_"))return material==Material.IRON_INGOT;
        if(name.startsWith("GOLDEN_"))return material==Material.GOLD_INGOT;
        if(name.startsWith("DIAMOND_"))return material==Material.DIAMOND;
        if(name.startsWith("NETHERITE_"))return material==Material.NETHERITE_INGOT;
        if(name.startsWith("LEATHER_"))return material==Material.LEATHER;
        if(name.equals("TURTLE_HELMET"))return material==Material.TURTLE_SCUTE;
        if(name.equals("ELYTRA"))return material==Material.PHANTOM_MEMBRANE;
        if(name.equals("MACE"))return material==Material.BREEZE_ROD;
        return false;
    }
    public static Upgrade upgrade(ExpansionItems items,ItemStack source,ItemStack template,ItemStack ingot){
        if(source==null||source.getAmount()!=1||template==null||ingot==null||ExpansionMerge.family(source).isEmpty())return null;
        String id=items.id(source),tier;
        if(id.startsWith("siftie_"))tier="enderite";
        else if(id.startsWith("enderite_"))tier="wardenite";
        else if(!id.isEmpty())return null;
        else if(source.getType().name().startsWith("DIAMOND_"))tier="netherite";
        else if(source.getType().name().startsWith("NETHERITE_"))tier="siftie";
        else return null;
        if(tier.equals("netherite")){
            if(template.getType()!=Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE||ingot.getType()!=Material.NETHERITE_INGOT||!items.id(template).isEmpty()||!items.id(ingot).isEmpty())return null;
        }else if(!items.id(template).equals(tier+"_template")||!items.id(ingot).equals(tier+"_ingot"))return null;
        try{return new Upgrade(tier,items.upgrade(source,tier));}catch(IllegalArgumentException e){return null;}
    }
    public static boolean ritual(ExpansionItems items,ItemStack[] values){
        if(values==null||values.length!=8||Arrays.stream(values).anyMatch(Objects::isNull))return false;
        if(!Set.of("fire_book","water_book","earth_book","wind_book","darkness_book").contains(items.id(values[0])))return false;
        return items.id(values[1]).equals("wardenite_ingot")&&items.id(values[2]).equals("enderite_stick")&&items.id(values[3]).equals("dragon_heart")
            &&values[4].getType()==Material.ECHO_SHARD&&items.id(values[4]).isEmpty()&&items.id(values[5]).equals("warden_heart")&&items.id(values[6]).equals("bat_wing")
            &&values[7].getType()==Material.NETHERITE_INGOT&&items.id(values[7]).isEmpty();
    }
}
