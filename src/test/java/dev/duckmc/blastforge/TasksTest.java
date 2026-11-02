package dev.duckmc.blastforge;

import org.bukkit.Location;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TasksTest {
    ServerMock server;org.bukkit.plugin.Plugin plugin;
    @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.createMockPlugin();}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void paperIsNotFolia(){assertFalse(Tasks.folia());}
    @Test void jobsRunOnTheirSchedulersAndStopWhenCancelled(){
        var world=server.addSimpleWorld("w");var player=server.addPlayer();AtomicInteger global=new AtomicInteger(),region=new AtomicInteger(),entity=new AtomicInteger();
        var g=Tasks.globalTimer(plugin,1,1,global::incrementAndGet);
        Tasks.at(plugin,new Location(world,0,64,0),region::incrementAndGet);
        var e=Tasks.entityTimer(plugin,player,1,1,h->entity.incrementAndGet(),null);
        server.getScheduler().performTicks(5);
        assertTrue(global.get()>=3,"global timer ran "+global);assertEquals(1,region.get(),"region job ran once");assertTrue(entity.get()>=3,"player timer ran "+entity);
        g.cancel();e.cancel();assertTrue(g.cancelled());assertTrue(e.cancelled());int g0=global.get(),e0=entity.get();
        server.getScheduler().performTicks(5);
        assertEquals(g0,global.get(),"cancelled global timer stays stopped");assertEquals(e0,entity.get(),"cancelled player timer stays stopped");
    }
    @Test void nowRunsImmediatelyWhereTheThreadOwnsTheTarget(){
        var player=server.addPlayer();AtomicInteger ran=new AtomicInteger();
        Tasks.now(plugin,player,ran::incrementAndGet);assertEquals(1,ran.get(),"Paper's main thread owns everything: runs at once");
    }
    @Test void reentryIsPerThread() throws Exception{
        var flag=new Reentry();flag.enter();assertTrue(flag.active());
        boolean[] other={true};var t=new Thread(()->other[0]=flag.active());t.start();t.join();
        assertFalse(other[0],"another thread does not see this thread's flag");flag.exit();assertFalse(flag.active());
    }
}
