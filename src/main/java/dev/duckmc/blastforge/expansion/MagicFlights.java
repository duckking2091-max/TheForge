package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.*;
import java.util.*;
import java.util.function.Function;

/** Shared Wind/scythe flight authorization with durable original-state restoration. */
final class MagicFlights {
    private final PersistentStore store;private final MagicFlightState state;private final Function<String,FileConfiguration> configs;
    MagicFlights(PersistentStore store,Function<String,FileConfiguration> configs){this.store=store;this.configs=configs;this.state=new MagicFlightState(store);}
    boolean grant(Player p,double seconds){
        if(p.isDead() || p.isInsideVehicle() || p.isGliding() || seconds<=0)return false;
        state.grant(p.getUniqueId(),p.getAllowFlight(),p.isFlying(),p.getGameMode().name(),System.currentTimeMillis(),(long)(Math.min(300,seconds)*1000));
        store.remove("magic.flight-restore",p.getUniqueId().toString());
        p.setAllowFlight(true);
        // Lift off the ground first: a Survival client that is flying while standing on the ground stops flying,
        // which is what happened when Sneak (descend) was still held at activation.
        if(p.isOnGround())p.setVelocity(p.getVelocity().setY(.55));
        p.setFlying(true);return true;
    }
    boolean leased(Player p){return state.get(p.getUniqueId())!=null;}
    /**
     * Runs every tick for players with an active flight lease (Wind Book or scythe).
     * Holding Sneak descends as normal but stops just above the ground instead of landing, so flight is never
     * cancelled by Sneak; after a deliberate landing, jumping resumes flight by itself.
     */
    void hold(Player p){
        if(!leased(p) || !p.getAllowFlight() || p.getGameMode()==GameMode.CREATIVE || p.getGameMode()==GameMode.SPECTATOR)return;
        if(!p.isFlying()){if(!p.isOnGround() && !p.isInsideVehicle() && !p.isGliding())p.setFlying(true);return;}
        if(p.isSneaking()){
            var below=p.getWorld().rayTraceBlocks(p.getLocation().add(0,.05,0),new org.bukkit.util.Vector(0,-1,0),.7,FluidCollisionMode.NEVER,true);
            if(below!=null && p.getVelocity().getY()<0)p.setVelocity(p.getVelocity().setY(0));
        }
    }
    boolean external(Player p){String permission=configs.apply("magic").getString("flight.external-permission","essentials.fly");return !permission.isBlank() && p.isPermissionSet(permission) && p.hasPermission(permission);}
    boolean nativeFlight(Player p){return p.getGameMode()==GameMode.CREATIVE || p.getGameMode()==GameMode.SPECTATOR || external(p);}
    void restore(Player p){var lease=state.get(p.getUniqueId());String pending=store.get("magic.flight-restore",p.getUniqueId().toString());
        if(lease==null && pending!=null)lease=MagicFlightState.Lease.decode(pending);if(lease==null)return;
        // Move live authorization into a durable restoration intent before touching playerdata.
        store.put("magic.flight-restore",p.getUniqueId().toString(),lease.encode());
        boolean survivalBaseline=lease.gameMode().equals(GameMode.SURVIVAL.name()) || lease.gameMode().equals(GameMode.ADVENTURE.name());
        boolean nativeGrant=nativeFlight(p),allowed=survivalBaseline && lease.allowFlight() || nativeGrant;
        // A survival baseline must never revoke a later creative/spectator or integrated external grant.
        if(!allowed){if(p.isFlying())p.setFlying(false);p.setAllowFlight(false);descent(p);}
        else {p.setAllowFlight(true);if(!nativeGrant && p.getGameMode().name().equals(lease.gameMode()))p.setFlying(lease.flying());}
        state.remove(p.getUniqueId());
        try{PlayerSaveCheckpoint.save(p);store.remove("magic.flight-restore",p.getUniqueId().toString());}
        catch(RuntimeException failedSave){/* Reconcile the saved baseline at the next join; never acknowledge a failed save. */}
    }
    private boolean shutdown;
    private void descent(Player p){
        if(p.isDead() || p.isOnGround())return;
        store.put("magic.descent",p.getUniqueId().toString(),Long.toString(System.currentTimeMillis()+120_000));
        p.setFallDistance(0);
        // Normally the fall is simply cancelled on landing (no Slow Falling). Only while the plugin shuts down,
        // when that listener disappears, a finite Slow Falling protects the descent.
        if(!shutdown)return;
        PotionEffect existing=p.getPotionEffect(PotionEffectType.SLOW_FALLING);
        if(existing==null)p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,2400,0,true,false,true));
    }
    boolean safeFall(Player p){String expiry=store.get("magic.descent",p.getUniqueId().toString());if(expiry==null)return false;boolean active=Long.parseLong(expiry)>System.currentTimeMillis();store.remove("magic.descent",p.getUniqueId().toString());return active;}
    void tick(Player p,long now){if(state.expired(p.getUniqueId(),now))restore(p);if(p.isOnGround() && store.get("magic.descent",p.getUniqueId().toString())!=null)store.remove("magic.descent",p.getUniqueId().toString());}
    void reconcile(Player p){restore(p);}
    void close(){shutdown=true;Set<UUID> players=new HashSet<>(state.players());for(String id:store.entries("magic.flight-restore").keySet())players.add(UUID.fromString(id));for(UUID player:players){Player p=Bukkit.getPlayer(player);if(p!=null)restore(p);}}
}
