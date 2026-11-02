package dev.duckmc.blastforge;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import java.util.function.Consumer;

/**
 * Scheduling that works on both Paper and Folia.
 * <p>
 * Folia runs each region of a world on its own thread, so work must run on the thread that owns what it touches:
 * an entity's own scheduler for players/mobs (it follows them between regions), the region scheduler for blocks,
 * and the global region for world-independent bookkeeping. Paper implements these same schedulers on its main
 * thread, so this one code path is correct on both.
 */
public final class Tasks {
    private Tasks(){}
    private static final boolean FOLIA=Compat.is("io.papermc.paper.threadedregions.RegionizedServer");
    /** True on Folia (regionised multithreading). */
    public static boolean folia(){return FOLIA;}

    /** A repeating or delayed job that can be cancelled whichever scheduler started it. */
    public interface Handle{void cancel();boolean cancelled();}
    private static Handle of(ScheduledTask task){
        if(task==null)return CANCELLED;
        return new Handle(){
            private volatile boolean stopped;
            public void cancel(){stopped=true;try{task.cancel();}catch(RuntimeException unsupported){/* the job also checks cancelled() */}}
            public boolean cancelled(){if(stopped)return true;try{return task.isCancelled();}catch(RuntimeException unsupported){return false;}}
        };
    }
    private static final Handle CANCELLED=new Handle(){public void cancel(){}public boolean cancelled(){return true;}};

    // ---------------------------------------------------------------- global (no world data)
    public static void global(Plugin plugin,Runnable job){Bukkit.getGlobalRegionScheduler().execute(plugin,job);}
    public static Handle globalLater(Plugin plugin,long ticks,Runnable job){return of(Bukkit.getGlobalRegionScheduler().runDelayed(plugin,t->job.run(),Math.max(1,ticks)));}
    public static Handle globalTimer(Plugin plugin,long delay,long period,Runnable job){Handle[] self=new Handle[1];self[0]=of(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,t->{if(self[0]!=null&&self[0].cancelled())return;job.run();},Math.max(1,delay),Math.max(1,period)));return self[0];}

    // ---------------------------------------------------------------- a block / location (its region)
    public static void at(Plugin plugin,Location location,Runnable job){Bukkit.getRegionScheduler().execute(plugin,location,job);}
    public static void at(Plugin plugin,Block block,Runnable job){at(plugin,block.getLocation(),job);}
    public static Handle atLater(Plugin plugin,Location location,long ticks,Runnable job){return of(Bukkit.getRegionScheduler().runDelayed(plugin,location,t->job.run(),Math.max(1,ticks)));}
    public static Handle atTimer(Plugin plugin,Location location,long delay,long period,Consumer<Handle> job){
        Handle[] self=new Handle[1];self[0]=of(Bukkit.getRegionScheduler().runAtFixedRate(plugin,location,t->{if(self[0]!=null&&self[0].cancelled())return;job.accept(self[0]==null?of(t):self[0]);},Math.max(1,delay),Math.max(1,period)));return self[0];
    }

    // ---------------------------------------------------------------- an entity (follows it; stops when it is removed)
    /** Runs next tick on the entity's thread; does nothing if the entity is gone. */
    public static void entity(Plugin plugin,Entity entity,Runnable job){entity.getScheduler().run(plugin,t->job.run(),null);}
    /** Like {@link #entity}, with a fallback if the entity is removed first. */
    public static void entity(Plugin plugin,Entity entity,Runnable job,Runnable retired){entity.getScheduler().run(plugin,t->job.run(),retired);}
    public static Handle entityLater(Plugin plugin,Entity entity,long ticks,Runnable job){return of(entity.getScheduler().runDelayed(plugin,t->job.run(),null,Math.max(1,ticks)));}
    public static Handle entityTimer(Plugin plugin,Entity entity,long delay,long period,Consumer<Handle> job,Runnable retired){
        Handle[] self=new Handle[1];self[0]=of(entity.getScheduler().runAtFixedRate(plugin,t->{if(self[0]!=null&&self[0].cancelled())return;job.accept(self[0]==null?of(t):self[0]);},retired,Math.max(1,delay),Math.max(1,period)));return self[0];
    }


    // ---------------------------------------------------------------- "here if possible"
    /** Runs now if this thread owns the entity (always on Paper's main thread), otherwise on the entity's own thread. */
    public static void now(Plugin plugin,Entity entity,Runnable job){if(owns(entity))job.run();else entity(plugin,entity,job);}
    /** Runs now if this thread owns the location (always on Paper's main thread), otherwise in its region. */
    public static void nowAt(Plugin plugin,Location location,Runnable job){if(owns(location))job.run();else at(plugin,location,job);}
    /**
     * A tick counter for "same tick" checks. Paper: the server tick. Folia has no single server tick (each region keeps
     * its own time), so there it counts 50 ms steps of the system clock.
     */
    public static int tick(){return FOLIA?(int)(System.nanoTime()/50_000_000L):Bukkit.getCurrentTick();}
    /**
     * Finds an entity by id from a loop that may run on any thread. Paper: the normal server lookup. Folia: no thread
     * may search every region, so the plugin's own record of entities it saw spawn or load is used (stale entries are dropped).
     */
    public static Entity find(java.util.Map<java.util.UUID,Entity> known,java.util.UUID id){
        if(!FOLIA)return Bukkit.getEntity(id);
        Entity entity=known.get(id);if(entity!=null&&!entity.isValid()){known.remove(id,entity);return null;}return entity;
    }
    /** True if the current thread may touch this entity/location right now (always true on Paper's main thread). */
    public static boolean owns(Entity entity){return Bukkit.isOwnedByCurrentRegion(entity);}
    public static boolean owns(Location location){return Bukkit.isOwnedByCurrentRegion(location);}
}
