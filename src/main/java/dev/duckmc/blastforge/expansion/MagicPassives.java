package dev.duckmc.blastforge.expansion;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
import java.util.*;

/** Inventory-presence cache and short potion leases; external effects are never indiscriminately cleared. */
final class MagicPassives {
    static final int FIRE=1,WATER=2,EARTH=4,WIND=8,DARKNESS=16;
    private record Effect(int amplifier,long expiry){}
    private final ExpansionItems items;private final Map<UUID,Integer> cache=new HashMap<>();
    private final Map<UUID,Map<PotionEffectType,Effect>> owned=new HashMap<>();private int writes;
    MagicPassives(ExpansionItems items){this.items=items;}
    int scan(Player player){int mask=0;for(ItemStack item:player.getInventory().getStorageContents())mask|=mask(items.id(item));mask|=mask(items.id(player.getInventory().getItemInOffHand()));cache.put(player.getUniqueId(),mask);return mask;}
    private int mask(String id){return switch(id){case "fire_book"->FIRE;case "water_book"->WATER;case "earth_book"->EARTH;case "wind_book"->WIND;case "darkness_book"->FIRE|WATER|EARTH|WIND|DARKNESS;default->0;};}
    int books(Player p){Integer present=cache.get(p.getUniqueId());return present==null?scan(p):present;}
    void dirty(Player p){cache.remove(p.getUniqueId());}
    void tick(Player p){int mask=scan(p);if((mask&EARTH)!=0)lease(p,PotionEffectType.HASTE,1,40);else release(p,PotionEffectType.HASTE);
        if((mask&WATER)!=0 && p.isSwimming())lease(p,PotionEffectType.DOLPHINS_GRACE,0,40);else release(p,PotionEffectType.DOLPHINS_GRACE);
    }
    void lease(Player p,PotionEffectType type,int amplifier,int ticks){
        PotionEffect current=p.getPotionEffect(type);Effect mine=owned.getOrDefault(p.getUniqueId(),Map.of()).get(type);
        if(current!=null && (mine==null || current.getAmplifier()>amplifier || current.getAmplifier()==amplifier && current.getDuration()>ticks+2))return;
        writes++;try{if(p.addPotionEffect(new PotionEffect(type,ticks,amplifier,true,false,true)))owned.computeIfAbsent(p.getUniqueId(),u->new HashMap<>()).put(type,new Effect(amplifier,System.currentTimeMillis()+ticks*50L));}finally{writes--;}
    }
    void changed(EntityPotionEffectEvent e){if(writes==0 && e.getEntity() instanceof Player p && !e.isCancelled()){var map=owned.get(p.getUniqueId());if(map!=null)map.remove(e.getModifiedType());}}
    void release(Player p,PotionEffectType type){var map=owned.get(p.getUniqueId());Effect mine=map==null?null:map.remove(type);PotionEffect current=p.getPotionEffect(type);
        if(mine!=null && current!=null && current.getAmplifier()==mine.amplifier && current.getDuration()<=Math.max(0,(mine.expiry-System.currentTimeMillis())/50)+3){writes++;try{p.removePotionEffect(type);}finally{writes--;}}
    }
    void remove(Player p){release(p,PotionEffectType.HASTE);release(p,PotionEffectType.DOLPHINS_GRACE);cache.remove(p.getUniqueId());owned.remove(p.getUniqueId());}
}
