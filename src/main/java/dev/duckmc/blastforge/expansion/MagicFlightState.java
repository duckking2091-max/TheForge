package dev.duckmc.blastforge.expansion;

import java.util.*;

/** The first lease owns the baseline; subsequent Wind/scythe grants only extend its deadline. */
final class MagicFlightState {
    record Lease(boolean allowFlight,boolean flying,String gameMode,long expiry) {
        String encode(){return allowFlight+"|"+flying+"|"+gameMode+"|"+expiry;}
        static Lease decode(String value){var p=value.split("\\|");return new Lease(Boolean.parseBoolean(p[0]),Boolean.parseBoolean(p[1]),p[2],Long.parseLong(p[3]));}
    }
    private final PersistentStore store;
    private final Map<UUID,Lease> leases=new HashMap<>();
    MagicFlightState(PersistentStore store){this.store=store;for(var e:store.entries("magic.flight").entrySet())try{leases.put(UUID.fromString(e.getKey()),Lease.decode(e.getValue()));}catch(RuntimeException malformed){store.put("magic.flight-quarantine",e.getKey(),e.getValue());store.remove("magic.flight",e.getKey());}}
    Lease get(UUID player){return leases.get(player);}
    Set<UUID> players(){return Set.copyOf(leases.keySet());}
    void grant(UUID player,boolean allow,boolean flying,String mode,long now,long duration){
        Lease old=leases.get(player);Lease lease=old==null?new Lease(allow,flying,mode,now+duration):new Lease(old.allowFlight,old.flying,old.gameMode,Math.max(old.expiry,now+duration));
        store.put("magic.flight",player.toString(),lease.encode());leases.put(player,lease);
    }
    boolean expired(UUID player,long now){Lease lease=get(player);return lease!=null && lease.expiry<=now;}
    void remove(UUID player){store.remove("magic.flight",player.toString());leases.remove(player);}
}
