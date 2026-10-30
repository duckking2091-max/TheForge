package dev.duckmc.blastforge.expansion;

import java.util.*;

/** Deterministic population/stability and exclusive job ownership rules. */
final class VillagerRules {
    private VillagerRules(){}
    static double chance(double base,double bonus,int population){
        if(!Double.isFinite(base)||!Double.isFinite(bonus))return 0;
        return Math.clamp(Math.max(0,base)+Math.max(0,bonus)*Math.max(0,population),0,1);
    }
    record Stability(UUID world,double x,double y,double z,long since){
        boolean ready(UUID current,double px,double py,double pz,long now,long required,double radius){
            return contains(current,px,py,pz,radius)&&now>=since&&now-since>=Math.max(0,required);
        }
        boolean contains(UUID current,double px,double py,double pz,double radius){
            return world.equals(current)&&(px-x)*(px-x)+(pz-z)*(pz-z)<=radius*radius;
        }
    }
    static final class Claims {
        private final Map<String,UUID> sites=new HashMap<>();
        boolean claim(String site,UUID owner){if(sites.containsKey(site))return owner.equals(sites.get(site));if(sites.containsValue(owner))return false;sites.put(site,owner);return true;}
        boolean release(String site,UUID owner){return sites.remove(site,owner);}
        UUID owner(String site){return sites.get(site);}
    }
}
