package dev.duckmc.blastforge;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import java.lang.reflect.Method;

/**
 * Everything that differs between Paper 1.21.0 and 1.21.11, in one place.
 * <p>
 * The plugin source must compile against every 1.21.x Paper API (checked by the build), so nothing here may name a
 * class, field or method that only some versions have: those are looked up by name at runtime instead.
 * When a feature does not exist on the running server, the helper does nothing and the plugin keeps working without it.
 */
public final class Compat {
    private Compat(){}

    /** e.g. "armor" (1.21.2+) or "generic.armor" (1.21.0-1.21.1); "block_break_speed" / "player.block_break_speed". */
    public static Attribute attribute(String name){
        for(String key:new String[]{name,"generic."+name,"player."+name}){
            Attribute found=Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if(found!=null)return found;
        }
        return null;
    }

    /** Hostile mobs that the Harming effect heals (zombies, skeletons, phantoms, the Wither...). */
    public static boolean undead(LivingEntity t){
        return t instanceof Zombie || t instanceof AbstractSkeleton || t instanceof Phantom || t instanceof Wither
            || t instanceof Zoglin || t instanceof SkeletonHorse || t instanceof ZombieHorse;
    }

    // ---------------------------------------------------------------- item model (1.21.4+)
    private static final Method SET_ITEM_MODEL=method(ItemMeta.class,"setItemModel",NamespacedKey.class);
    /** Gives an item a model from the resource pack by id. Returns false on servers older than 1.21.4. */
    public static boolean itemModel(ItemMeta meta,NamespacedKey model){return call(SET_ITEM_MODEL,meta,model);}

    // ---------------------------------------------------------------- equippable component (1.21.2+)
    private static final Method GET_EQUIPPABLE=method(ItemMeta.class,"getEquippable");
    private static final Method HAS_EQUIPPABLE=method(ItemMeta.class,"hasEquippable");
    private static final Class<?> EQUIPPABLE=type("org.bukkit.inventory.meta.components.EquippableComponent");
    private static final Method SET_EQUIPPABLE=EQUIPPABLE==null?null:method(ItemMeta.class,"setEquippable",EQUIPPABLE);
    private static final Method SET_SLOT=EQUIPPABLE==null?null:method(EQUIPPABLE,"setSlot",EquipmentSlot.class);
    private static final Method SET_MODEL=EQUIPPABLE==null?null:method(EQUIPPABLE,"setModel",NamespacedKey.class);
    public static boolean hasEquipmentModels(){return GET_EQUIPPABLE!=null && SET_EQUIPPABLE!=null && SET_SLOT!=null && SET_MODEL!=null;}
    /** Shows armour with a custom equipment model (null = the item's own model). False where unsupported. */
    public static boolean equipmentModel(ItemMeta meta,EquipmentSlot slot,NamespacedKey model){
        if(!hasEquipmentModels())return false;
        try{Object component=GET_EQUIPPABLE.invoke(meta);SET_SLOT.invoke(component,slot);SET_MODEL.invoke(component,model);SET_EQUIPPABLE.invoke(meta,component);return true;}
        catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    /** Removes a custom equippable component left on old items. */
    public static boolean clearEquippable(ItemMeta meta){
        if(HAS_EQUIPPABLE==null || SET_EQUIPPABLE==null)return false;
        try{if(!(Boolean)HAS_EQUIPPABLE.invoke(meta))return false;SET_EQUIPPABLE.invoke(meta,(Object)null);return true;}
        catch(ReflectiveOperationException|RuntimeException e){return false;}
    }

    // ---------------------------------------------------------------- helpers
    public static boolean is(String className){return type(className)!=null;}
    private static Class<?> type(String name){try{return Class.forName(name);}catch(ClassNotFoundException|LinkageError e){return null;}}
    static Method method(Class<?> owner,String name,Class<?>... parameters){try{return owner.getMethod(name,parameters);}catch(NoSuchMethodException|LinkageError e){return null;}}
    private static boolean call(Method method,Object target,Object... arguments){
        if(method==null)return false;try{method.invoke(target,arguments);return true;}catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    /** True when the server runs Minecraft 1.21.patch or newer (any later minor version counts too). */
    public static boolean atLeast(int patch){
        String[] parts=minecraft().split("[.-]");
        try{int minor=Integer.parseInt(parts[1]),p=parts.length>2?Integer.parseInt(parts[2]):0;return minor>21 || minor==21 && p>=patch;}
        catch(RuntimeException unknown){return true;}
    }
    /** The running Minecraft version, e.g. "1.21.4". */
    public static String minecraft(){return Bukkit.getMinecraftVersion();}
}
