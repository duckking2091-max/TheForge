package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.configuration.file.*;
import java.io.InputStreamReader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuideReaderTest {
    ServerMock server;GuideReader reader;Map<String,FileConfiguration> cfg=new HashMap<>();
    static FileConfiguration res(String n){return YamlConfiguration.loadConfiguration(new InputStreamReader(GuideReaderTest.class.getResourceAsStream("/"+n+".yml")));}
    @BeforeEach void setup(){server=MockBukkit.mock();for(String n:List.of("config","materials","magic","villagers","scythe"))cfg.put(n,res(n));var plugin=MockBukkit.createMockPlugin();
        reader=new GuideReader(plugin,new GuideBook(plugin,cfg::get),new ExpansionItems(plugin,n->new YamlConfiguration()));}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void everyLineFitsItsPageAndPagesComeInPairs(){
        var layout=reader.layout();assertEquals(0,layout.pages().size()%2);assertTrue(layout.pages().size()>60,"pages "+layout.pages().size());
        for(int i=0;i<layout.pages().size();i++){
            var page=layout.pages().get(i);assertTrue(GuideReader.THEMES.contains(page.theme()),"theme "+page.theme());
            for(var line:page.lines()){
                assertTrue(line.x()+GuideReader.width(line.segs())<=GuideReader.PAGE_W,"page "+(i+1)+" line too wide: "+line);
                assertTrue(line.top()>=5&&line.top()+8<=GuideReader.FIRST_TOP+GuideReader.LINES*GuideReader.LINE_H+GuideReader.LINE_H*2,"page "+(i+1)+" line outside the page: "+line.top());
                for(var s:line.segs())if(s.color()!=null)for(char c:s.text().toCharArray())assertTrue(GuideFont.has(c),"unprintable '"+c+"' on page "+(i+1));
            }
            long body=page.lines().stream().filter(l->(l.top()-GuideReader.FIRST_TOP)%GuideReader.LINE_H==0&&l.top()<=GuideReader.FIRST_TOP+(GuideReader.LINES-1)*GuideReader.LINE_H).map(GuideReader.Line::top).distinct().count();
            assertTrue(body<=GuideReader.LINES);
        }
    }
    @Test void chaptersOpenOnLeftPagesAndContentsJumpToThem(){
        var layout=reader.layout();assertEquals(11,layout.entries().size());
        for(int i=0;i<layout.entries().size();i++){var e=layout.entries().get(i);assertEquals(0,e.page()%2,e.title()+" should open on a left page");
            assertEquals("CHAPTER "+(i+1<10?"0":"")+(i+1),layout.pages().get(e.page()).lines().getFirst().segs().getFirst().text(),e.title()+" must point at its own chapter");}
        var seen=new HashSet<Integer>();
        for(int spread=0;spread<2;spread++)for(var slot:GuideReader.contentsSlots(layout,spread).entrySet()){
            assertFalse(Set.of(GuideReader.PREV,GuideReader.CONTENTS,GuideReader.NEXT).contains(slot.getKey()));seen.add(slot.getValue());}
        assertEquals(11,seen.size(),"every chapter reachable from the contents");
    }
    @Test void elementalAndOreSectionsUseTheirOwnDesigns(){
        var themes=new HashSet<String>();for(var p:reader.layout().pages())themes.add(p.theme());
        for(String t:List.of("ores_siftie","ores_enderite","ores_wardenite","magic_fire","magic_water","magic_earth","magic_wind","magic_darkness","miner","forging","ritual","faq"))assertTrue(themes.contains(t),"missing design "+t);
    }
    @Test void penMovesAddUpExactly(){
        for(int px:new int[]{-300,-129,-48,-1,1,7,100,257,511}){int sum=0;for(char c:GuideReader.move(px).toCharArray())sum+=c>=0xF821?(1<<(c-0xF821)):-(1<<(c-0xF801));assertEquals(px,sum);}
        assertNotNull(reader.title(reader.layout(),0));
    }
    @Test void everyFigureIsKnownFitsAndNeverOverlapsText(){
        assertTrue(GuideReader.FIGURES.size()>=25);
        var guide=new GuideBook(MockBukkit.createMockPlugin(),cfg::get);int used=0;
        for(var ch:guide.chapters(guide.guide()))for(String p:ch.paragraphs()){String f=GuideBook.figure(p);if(f!=null){assertNotNull(GuideReader.FIGURES.get(f),"unknown figure "+f);used++;}}
        assertTrue(used>=20,"figures used "+used);
        for(var page:reader.layout().pages()){
            var taken=new ArrayList<int[]>();
            for(var line:page.lines()){
                int h=8;var s=line.segs().getFirst();
                if(s.color()==null){var fig=GuideReader.FIGURES.values().stream().filter(f->(s.text().charAt(0)-GuideReader.FIGURE_BASE)/GuideReader.LINES==f.index()).findFirst().orElseThrow();
                    h=fig.height();assertEquals(line.top(),GuideReader.FIRST_TOP+((s.text().charAt(0)-GuideReader.FIGURE_BASE)%GuideReader.LINES)*GuideReader.LINE_H,"glyph row matches line");
                    assertTrue(line.top()+h<=GuideReader.FIRST_TOP+GuideReader.LINES*GuideReader.LINE_H,"figure runs off the page");}
                if(line.top()>GuideReader.FIRST_TOP+GuideReader.LINES*GuideReader.LINE_H)continue;
                for(int[] t:taken)if(line.x()<t[2]&&t[0]<line.x()+GuideReader.width(line.segs())&&line.top()<t[3]&&t[1]<line.top()+h)fail("overlap on page with "+line);
                taken.add(new int[]{line.x(),line.top(),line.x()+GuideReader.width(line.segs()),line.top()+h});
            }
        }
    }
}
