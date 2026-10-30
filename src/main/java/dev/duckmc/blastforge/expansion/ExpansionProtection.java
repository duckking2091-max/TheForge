package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

/** Native boundaries and optional WorldGuard authorization. Damage still fires native events. */
public final class ExpansionProtection {
    private final JavaPlugin plugin;
    private boolean warned;
    private java.util.function.BooleanSupplier passiveTargets=()->false;
    public ExpansionProtection(JavaPlugin plugin){this.plugin=plugin;}
    public void passiveTargets(java.util.function.BooleanSupplier setting){passiveTargets=setting;}
    public boolean canAffect(Player player,Location location){
        World world=location.getWorld();
        if(world==null || location.getY()<world.getMinHeight() || location.getY()>=world.getMaxHeight() || !player.getWorld().equals(world) || !world.getWorldBorder().isInside(location)
            || plugin.getConfig().getStringList("disabled-worlds").contains(world.getName())
            || !world.isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4))return false;
        var server=plugin.getServer();
        if(!player.isOp() && !server.getOperators().isEmpty() && world.equals(server.getWorlds().getFirst()) && server.getSpawnRadius()>0){
            Location spawn=world.getSpawnLocation();
            if(Math.max(Math.abs((long)location.getBlockX()-spawn.getBlockX()),Math.abs((long)location.getBlockZ()-spawn.getBlockZ()))<=server.getSpawnRadius())return false;
        }
        return worldGuard(player,location,"BUILD");
    }
    public boolean canDamage(Player player,LivingEntity target){
        if(target.equals(player) || !target.isValid() || target.isDead() || !player.getWorld().equals(target.getWorld()))return false;
        if(target instanceof Tameable tame && (tame.isTamed()||player.getUniqueId().equals(tame.getOwnerUniqueId())))return false;
        if(target instanceof Player other){
            if(!player.getWorld().getPVP() || other.getGameMode()==GameMode.CREATIVE || other.getGameMode()==GameMode.SPECTATOR)return false;
            var team=player.getScoreboard().getEntryTeam(player.getName());
            if(team!=null && team.hasEntry(other.getName()) && !team.allowFriendlyFire())return false;
            return worldGuard(player,target.getLocation(),"PVP") && worldGuard(other,player.getLocation(),"PVP");
        }
        return target instanceof Monster || passiveTargets.getAsBoolean()&&(target instanceof Animals||target instanceof WaterMob||target instanceof Bat);
    }
    public boolean canSpawn(Location location){
        return location.getWorld()!=null && !plugin.getConfig().getStringList("disabled-worlds").contains(location.getWorld().getName()) && location.getY()>=location.getWorld().getMinHeight() && location.getY()<location.getWorld().getMaxHeight() && location.getWorld().getWorldBorder().isInside(location)
            && location.getWorld().isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4)
            && worldGuard(null,location,"MOB_SPAWNING");
    }
    private boolean worldGuard(Player player,Location location,String flag){
        if(plugin.getServer().getPluginManager().getPlugin("WorldGuard")==null)return true;
        try {
            Class<?> adapter=Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adapted=adapter.getMethod("adapt",Location.class).invoke(null,location);
            Class<?> wg=Class.forName("com.sk89q.worldguard.WorldGuard");
            Object singleton=wg.getMethod("getInstance").invoke(null);
            Object platform=wg.getMethod("getPlatform").invoke(singleton);
            Object container=platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query=container.getClass().getMethod("createQuery").invoke(container);
            Object local=null;
            if(player!=null){Class<?> wgp=Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");Object instance=wgp.getMethod("inst").invoke(null);local=wgp.getMethod("wrapPlayer",Player.class).invoke(instance,player);}
            Object stateFlag=Class.forName("com.sk89q.worldguard.protection.flags.Flags").getField(flag).get(null);
            Class<?> stateType=Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Object flags=Array.newInstance(stateType,1);Array.set(flags,0,stateFlag);
            for(Method method:query.getClass().getMethods())if(method.getName().equals("testState") && method.getParameterCount()==3
                && method.getParameterTypes()[0].isInstance(adapted) && method.getParameterTypes()[2].isInstance(flags)
                && (local==null || method.getParameterTypes()[1].isInstance(local)))return (boolean)method.invoke(query,adapted,local,flags);
            throw new ReflectiveOperationException("No compatible WorldGuard region query");
        }catch(ReflectiveOperationException|LinkageError e){
            if(!warned){warned=true;plugin.getLogger().warning("WorldGuard authorization unavailable; denying The Forge actions until compatibility is restored: "+e.getMessage());}
            return false;
        }
    }
}
