package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.configuration.file.*;
import org.bukkit.inventory.meta.BookMeta;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class GuideBookTest {
    ServerMock server;GuideBook guide;Map<String,FileConfiguration> cfg=new HashMap<>();
    static FileConfiguration res(String n){return YamlConfiguration.loadConfiguration(new InputStreamReader(GuideBookTest.class.getResourceAsStream("/"+n+".yml")));}
    @BeforeEach void setup(){server=MockBukkit.mock();for(String n:List.of("config","materials","magic","villagers","scythe"))cfg.put(n,res(n));guide=new GuideBook(MockBukkit.createMockPlugin(),cfg::get);}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void everyPageFitsAndEveryNumberIsFilledIn(){
        var pages=guide.pageSource();assertTrue(pages.size()>20,"pages: "+pages.size());
        for(int i=0;i<pages.size();i++){
            int lines=0;for(String part:pages.get(i).split("\n",-1))lines+=part.isEmpty()?1:GuideBook.lines(part);
            assertTrue(lines<=GuideBook.LINES,"page "+(i+1)+" has "+lines+" lines:\n"+pages.get(i));
            assertFalse(pages.get(i).contains("{"),"unfilled placeholder on page "+(i+1)+": "+pages.get(i));
            assertFalse(pages.get(i).matches("(?s).*\\b\\?\\b.*")&&pages.get(i).contains(" ? "),"missing value on page "+(i+1));
        }
        assertTrue(String.join(" ",pages).contains("Y -60 to -30"),"Siftie range from materials.yml");
        assertTrue(String.join(" ",pages).contains("every 20 minutes"),"Miner interval from villagers.yml");
        String all=String.join(" ",pages);
        assertTrue(all.contains("10, 70, 150 and 250"),"lists are written out");assertFalse(all.matches("(?s).*\\[\\d.*"),"no raw lists");
        assertTrue(all.contains("Wandering Miner"),"the Miner's real name");
        assertTrue(all.contains("Fireball: burns targets 10s, 60s cooldown"),"Fire book third spell from magic.yml");
        assertTrue(all.contains("leaves after 30 minutes"),"Miner despawn from villagers.yml");
    }
    @Test void contentsLinksPointAtTheirChapters(){
        var pages=guide.pageSource();var tocBuilder=new StringBuilder();for(int i=1;i<pages.size()&&!pages.get(i).startsWith(GuideBook.TOC_LINK);i++)tocBuilder.append(pages.get(i)).append("\n");var toc=tocBuilder.toString();var m=Pattern.compile("change_page:(\\d+)>.*?>(\\d+)\\. ([^<]+)<").matcher(toc);int found=0;
        while(m.find()){int page=Integer.parseInt(m.group(1));assertTrue(pages.get(page-1).contains("<b>"+m.group(3)+"</b>"),"chapter "+m.group(3)+" should start on page "+page);found++;}
        assertEquals(11,found);
        for(int i=2;i<pages.size();i++)if(pages.get(i).startsWith(GuideBook.TOC_LINK))assertTrue(pages.get(i).contains("change_page:2"));
    }
    @Test void bookIsAWrittenBookSeparateFromTheBookOfForging(){
        var book=guide.create();assertTrue(GuideBook.isGuide(book));var meta=(BookMeta)book.getItemMeta();
        assertEquals("The Forging Starter",meta.getTitle());assertEquals("BlastForge",meta.getAuthor());assertEquals(guide.pageSource().size(),meta.getPageCount());
        var items=new ExpansionItems(MockBukkit.createMockPlugin(),n->new YamlConfiguration());assertEquals("",items.id(book));assertFalse(GuideBook.isGuide(items.create("forging_book")));
    }
    @Test void bookAndIronIngotCraftTheGuide(){
        guide.start();var recipe=server.getRecipe(GuideBook.RECIPE);assertNotNull(recipe);
        var shapeless=(org.bukkit.inventory.ShapelessRecipe)recipe;
        assertEquals(java.util.Set.of(org.bukkit.Material.BOOK,org.bukkit.Material.IRON_INGOT),shapeless.getChoiceList().stream().map(c->((org.bukkit.inventory.RecipeChoice.MaterialChoice)c).getItemStack().getType()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(GuideBook.isGuide(recipe.getResult()));
        guide.close();assertNull(server.getRecipe(GuideBook.RECIPE));
    }
    @Test void firstJoinGivesExactlyOneCopy(){
        var player=server.addPlayer();
        guide.join(new org.bukkit.event.player.PlayerJoinEvent(player,net.kyori.adventure.text.Component.empty()));server.getScheduler().performTicks(25);
        guide.join(new org.bukkit.event.player.PlayerJoinEvent(player,net.kyori.adventure.text.Component.empty()));server.getScheduler().performTicks(25);
        assertEquals(1,Arrays.stream(player.getInventory().getContents()).filter(GuideBook::isGuide).count());
        cfg.get("config").set("guide.give-on-first-join",false);var other=server.addPlayer();
        guide.join(new org.bukkit.event.player.PlayerJoinEvent(other,net.kyori.adventure.text.Component.empty()));server.getScheduler().performTicks(25);
        assertEquals(0,Arrays.stream(other.getInventory().getContents()).filter(GuideBook::isGuide).count());
    }
}
