package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import static org.junit.jupiter.api.Assertions.*;

class MerchantAppearanceTest {
    ServerMock server;MerchantAppearance look;ExpansionItems items;
    @BeforeEach void setup(){server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();var cfg=new YamlConfiguration();items=new ExpansionItems(plugin,n->cfg);look=new MerchantAppearance(plugin,items,n->cfg,null);}
    @AfterEach void teardown(){look.close();MockBukkit.unmock();}
    Villager villager(String kind,Villager.Profession job){
        var world=new VisualTestWorld(server,"w"+kind);Villager v=world.spawn(new Location(world,0,64,0),Villager.class);
        v.getPersistentDataContainer().set(VillagerSystem.KIND,PersistentDataType.STRING,kind);v.setProfession(job);return v;
    }
    String head(Villager v){return items.id(v.getEquipment().getHelmet());}
    String body(Villager v){return items.id(v.getEquipment().getItemInMainHand());}
    @Test void magicianOutfitFollowsTheJobAndUsesNoExtraEntities(){
        Villager v=villager("magician",Villager.Profession.LIBRARIAN);int before=v.getWorld().getEntities().size();
        look.track(v,"magician");
        assertEquals("magician_hat",head(v));assertEquals("magician_robes",body(v));
        assertEquals(0f,v.getEquipment().getHelmetDropChance());assertEquals(0f,v.getEquipment().getItemInMainHandDropChance());
        assertEquals(before,v.getWorld().getEntities().size(),"no display entities");assertTrue(v.getPassengers().isEmpty());
        v.setProfession(Villager.Profession.NONE);look.tick();assertEquals("",head(v));assertEquals("",body(v));   // lost the job: undressed
        look.tick();assertEquals("",head(v),"stays undressed");
        v.setProfession(Villager.Profession.LIBRARIAN);look.tick();assertEquals("magician_hat",head(v));assertEquals("magician_robes",body(v));   // job back: dressed again
        v.getEquipment().setItemInMainHand(null);look.tick();assertEquals("magician_robes",body(v),"restored after vanilla empties the arms");
        look.strip(v);assertEquals("",head(v));assertEquals("",body(v));look.tick();assertEquals("",head(v),"stripped stays stripped");
    }
    @Test void minerWearsHelmetAndBackpackOnlyAsToolsmith(){
        Villager v=villager("miner",Villager.Profession.TOOLSMITH);v.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.IRON_PICKAXE));
        look.track(v,"miner");assertEquals("miner_helmet",head(v));assertEquals("miner_backpack",body(v),"old held pickaxe replaced");
        v.setProfession(Villager.Profession.FARMER);look.tick();assertEquals("",head(v));assertEquals("",body(v));
    }
    @Test void untaggedVillagerIsNeverDressed(){
        var world=new VisualTestWorld(server,"plain");Villager v=world.spawn(new Location(world,0,64,0),Villager.class);v.setProfession(Villager.Profession.LIBRARIAN);
        look.track(v,"magician");assertEquals("",head(v));
    }
}
