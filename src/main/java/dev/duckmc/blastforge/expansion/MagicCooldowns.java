package dev.duckmc.blastforge.expansion;

import java.util.UUID;

/** Wall-clock cooldown authority, one bucket per ability; duplicate items never introduce another bucket. */
final class MagicCooldowns {
    private final PersistentStore store;
    MagicCooldowns(PersistentStore store){this.store=store;}
    private String key(String item,int ability){return item.equals("legendary_scythe")?"scythe."+ability:"magic."+item+"."+ability;}
    long remaining(UUID player,String item,int ability,long now){return Math.max(0,store.cooldown(player,key(item,ability))-now);}
    boolean ready(UUID player,String item,int ability,long now){return remaining(player,item,ability,now)==0;}
    void commit(UUID player,String item,int ability,long now,double seconds){
        if(seconds<=0)return;
        long expiry=now+(long)(Math.min(seconds,86_400)*1000);
        store.setCooldown(player,key(item,ability),Math.max(expiry,store.cooldown(player,key(item,ability))));
    }
}
