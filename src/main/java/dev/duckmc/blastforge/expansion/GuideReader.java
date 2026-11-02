package dev.duckmc.blastforge.expansion;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/**
 * The Forging Starter as an open book: two themed pages side by side.
 * <p>
 * Everything is drawn in the title of a 6-row container screen with resource-pack fonts:
 * <ul>
 *   <li>font {@code blastforge:guide}: the left/right page pictures of every chapter theme, plus spaces that move the pen;</li>
 *   <li>fonts {@code blastforge:gy5} .. {@code gy126}: Minecraft's own letters, shifted down to screen row 5..126,
 *       so each line of text sits exactly where it belongs (fonts can only move glyphs down, never up).</li>
 * </ul>
 * Line widths use the exact glyph widths of the 1.21.11 font ({@link GuideFont}), so lines never drift.
 * The bottom row of slots holds invisible buttons over the arrows and the ribbon; on the contents pages
 * each chapter's row is clickable.
 */
public final class GuideReader implements Listener,AutoCloseable {
    // ------------------------------------------------------------------ geometry (container-screen pixels)
    static final int PAGE_W=100,LINES=11,LINE_H=9,FIRST_TOP=6;
    static final int LEFT_X=-26,RIGHT_X=102;                 // text columns
    static final int BOOK_X=-40;                              // left edge of the open book
    static final int TITLE_X=8;                               // where Minecraft starts drawing a container title
    static final int PREV=45,CONTENTS=49,NEXT=53;
    static final Key GUIDE_FONT=Key.key("blastforge","guide");
    static final List<String> THEMES=List.of("cover","contents","welcome","miner","ores","ores_siftie","ores_enderite","ores_wardenite","forging","upgrade","ritual","magician","magic","magic_fire","magic_water","magic_earth","magic_wind","magic_darkness","gear","tips","faq");
    static final List<String> CHAPTER_THEMES=List.of("welcome","miner","ores","forging","upgrade","ritual","magician","magic","gear","tips","faq");
    static final TextColor INK=TextColor.color(0x3A2C1E),FAINT=TextColor.color(0x7A6A58);

    /** A word in a colour; a null colour marks a picture glyph of the guide font. */
    record Seg(String text,TextColor color){}
    /** A picture from the resource pack: glyph 0xE400+index*LINES+row draws it with its top on text row {@code row}. */
    record Figure(String name,int index,int width,int height){
        int lines(){return (height+1+LINE_H-1)/LINE_H;}
        char glyph(int row){return (char)(FIGURE_BASE+index*LINES+row);}
    }
    static final int FIGURE_BASE=0xE400;
    static final Map<String,Figure> FIGURES=loadFigures();
    private static Map<String,Figure> loadFigures(){
        var out=new LinkedHashMap<String,Figure>();var props=new Properties();
        try(var in=GuideReader.class.getResourceAsStream("/guide_figures.properties")){if(in!=null)props.load(in);}catch(java.io.IOException ignored){}
        for(String name:props.stringPropertyNames()){String[] v=props.getProperty(name).split(",");out.put(name,new Figure(name,Integer.parseInt(v[0].trim()),Integer.parseInt(v[1].trim()),Integer.parseInt(v[2].trim())));}
        return out;
    }
    static int segWidth(Seg s){if(s.color()==null){for(Figure f:FIGURES.values())if(s.text().length()==1&&(s.text().charAt(0)-FIGURE_BASE)/LINES==f.index())return f.width()+1;return 0;}return GuideFont.width(s.text());}
    record Line(List<Seg> segs,int x,int top){}                              // x is relative to the page's text column
    record Page(String theme,List<Line> lines){}
    record Entry(String title,TextColor color,int page){}                    // contents entry -> first page of the chapter

    private final JavaPlugin plugin;private final GuideBook book;private final ExpansionItems items;
    private final Map<UUID,Integer> lastSpread=new java.util.concurrent.ConcurrentHashMap<>();
    public GuideReader(JavaPlugin plugin,GuideBook book,ExpansionItems items){this.plugin=plugin;this.book=book;this.items=items;}
    public void start(){plugin.getServer().getPluginManager().registerEvents(this,plugin);}
    @Override public void close(){HandlerList.unregisterAll(this);for(Player p:plugin.getServer().getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Holder)p.closeInventory();}

    // ------------------------------------------------------------------ text model
    /** Darker versions of the chat colours, readable on parchment. */
    static TextColor ink(String name){
        return switch(name){
            case "gold"->TextColor.color(0x9A5B00);case "yellow"->TextColor.color(0x8A6600);case "aqua"->TextColor.color(0x0A7486);
            case "dark_aqua"->TextColor.color(0x0B5866);case "light_purple"->TextColor.color(0x9A2AAE);case "dark_purple"->TextColor.color(0x5E1A7A);
            case "red"->TextColor.color(0xA3241D);case "dark_red"->TextColor.color(0x7A1010);case "blue"->TextColor.color(0x2342A0);case "dark_blue"->TextColor.color(0x1A2470);
            case "green"->TextColor.color(0x3C7D1E);case "dark_green"->TextColor.color(0x2F6B1F);case "gray"->FAINT;case "dark_gray"->TextColor.color(0x4A4038);
            default->INK;};
    }
    /** Letters the font can draw; everything else is replaced by a close ASCII stand-in. */
    static String clean(String s){
        var out=new StringBuilder();
        for(char c:s.toCharArray()){
            String r=switch(c){case '•'->"-";case '×'->"x";case '—','–'->"-";case '’','‘'->"'";case '“','”'->"\"";case '…'->"...";case '☐'->"[ ]";default->null;};
            if(r!=null)out.append(r);else out.append(GuideFont.has(c)?c:'?');
        }
        return out.toString();
    }
    /** MiniMessage paragraph -> styled words per hard line ("<newline>"). Only colours matter here. */
    static List<List<Seg>> words(String mini){
        var lines=new ArrayList<List<Seg>>();var cur=new ArrayList<Seg>();var colors=new ArrayDeque<TextColor>();colors.push(INK);var buf=new StringBuilder();
        Runnable flush=()->{};
        for(int i=0;i<mini.length();i++){
            char c=mini.charAt(i);
            if(c=='<'){int end=GuideBook.tagEnd(mini,i);if(end>i){
                if(buf.length()>0){splitWords(buf.toString(),colors.peek(),cur);buf.setLength(0);}
                String tag=mini.substring(i+1,end).toLowerCase(Locale.ROOT);i=end;
                if(tag.equals("newline")||tag.equals("br")){lines.add(cur);cur=new ArrayList<>();}
                else if(tag.startsWith("/")){String n=tag.substring(1);if(NamedTextColor.NAMES.value(n)!=null&&colors.size()>1)colors.pop();}
                else if(NamedTextColor.NAMES.value(tag)!=null)colors.push(ink(tag));
                continue;}}
            buf.append(c);
        }
        if(buf.length()>0)splitWords(buf.toString(),colors.peek(),cur);
        lines.add(cur);return lines;
    }
    private static void splitWords(String text,TextColor color,List<Seg> out){
        text=clean(text);int start=0;
        for(int i=0;i<=text.length();i++)if(i==text.length()||text.charAt(i)==' '){if(i>start)out.add(new Seg(text.substring(start,i),color));if(i<text.length())out.add(new Seg(" ",color));start=i+1;}
    }
    /** Word-wrap one paragraph to the page width. */
    static List<List<Seg>> wrap(String mini){
        var out=new ArrayList<List<Seg>>();
        for(var hard:words(mini)){
            var line=new ArrayList<Seg>();int w=0;
            for(Seg s:hard){
                int sw=GuideFont.width(s.text());
                if(s.text().equals(" ")){if(w>0&&w+sw<=PAGE_W){line.add(s);w+=sw;}continue;}
                if(w+sw>PAGE_W&&w>0){trim(line);out.add(line);line=new ArrayList<>();w=0;}
                line.add(s);w+=sw;
            }
            trim(line);out.add(line);
        }
        return out;
    }
    private static void trim(List<Seg> line){while(!line.isEmpty()&&line.getLast().text().equals(" "))line.removeLast();}
    static int width(List<Seg> line){int w=0;for(Seg s:line)w+=segWidth(s);return w;}

    // ------------------------------------------------------------------ pagination
    record Layout(List<Page> pages,List<Entry> entries){}
    Layout layout(){
        var chapters=book.chapters(book.guide());
        var pages=new ArrayList<Page>();var entries=new ArrayList<Entry>();
        pages.add(cover());pages.add(null);pages.add(null);pages.add(howTo());          // contents pages are filled in last
        for(int c=0;c<chapters.size();c++){
            var ch=chapters.get(c);String theme=ch.theme()!=null&&THEMES.contains(ch.theme())?ch.theme():CHAPTER_THEMES.get(Math.min(c,CHAPTER_THEMES.size()-1));
            if(pages.size()%2==1)pages.add(new Page(pages.getLast().theme(),List.of()));   // chapters open on a left page
            entries.add(new Entry(ch.shortTitle()!=null?ch.shortTitle():ch.title(),ink(ch.color()),pages.size()));
            var lines=new ArrayList<List<Seg>>();
            lines.add(List.of(new Seg("CHAPTER "+(c+1<10?"0":"")+(c+1),FAINT)));
            for(var l:wrap("<"+ch.color()+">"+ch.title().toUpperCase(Locale.ROOT)+"</"+ch.color()+">"))lines.add(l);
            String pageTheme=theme;var current=new ArrayList<Line>();
            Runnable dummy=()->{};
            int used=0;
            for(var l:lines){current.add(new Line(l,0,FIRST_TOP+used*LINE_H));used++;}
            for(String raw:ch.paragraphs()){
                Figure fig=FIGURES.get(GuideBook.figure(raw));String paragraph=GuideBook.stripFigure(raw);
                var block=paragraph.isBlank()?new ArrayList<List<Seg>>():wrap(paragraph);String sub=subTheme(theme,paragraph);
                int gap=used>0?1:0;
                if(fig!=null){
                    // a section with a picture stays on one page; the picture never splits
                    int need=block.size()+fig.lines();
                    if(need>LINES&&!block.isEmpty()){
                        // too tall for one page together: the picture leads and the text may run onto the next page
                        if(used+gap+fig.lines()+Math.min(3,block.size())>LINES&&used>0){pages.add(new Page(pageTheme,current));current=new ArrayList<>();used=0;gap=0;}
                        if(used==0)pageTheme=sub;
                        used+=gap;
                        int nl=paragraph.indexOf("<newline>");int head=nl<0?0:Math.min(block.size(),wrap(paragraph.substring(0,nl)).size());
                        for(var l:block.subList(0,head)){current.add(new Line(l,0,FIRST_TOP+used*LINE_H));used++;}   // the heading stays on top
                        current.add(new Line(List.of(new Seg(String.valueOf(fig.glyph(used)),null)),(PAGE_W-fig.width())/2,FIRST_TOP+used*LINE_H));used+=fig.lines();
                        for(var l:block.subList(head,block.size())){
                            if(used>=LINES){pages.add(new Page(pageTheme,current));current=new ArrayList<>();used=0;pageTheme=sub;}
                            current.add(new Line(l,0,FIRST_TOP+used*LINE_H));used++;
                        }
                        continue;
                    }
                    if(used+gap+need>LINES&&used>0){pages.add(new Page(pageTheme,current));current=new ArrayList<>();used=0;gap=0;}
                    if(used==0)pageTheme=sub;
                    used+=gap;
                    for(var l:block){current.add(new Line(l,0,FIRST_TOP+used*LINE_H));used++;}
                    if(used+fig.lines()>LINES){pages.add(new Page(pageTheme,current));current=new ArrayList<>();used=0;}
                    current.add(new Line(List.of(new Seg(String.valueOf(fig.glyph(used)),null)),(PAGE_W-fig.width())/2,FIRST_TOP+used*LINE_H));
                    used+=fig.lines();
                    continue;
                }
                // A section that does not fit continues on the next page, like a real book, as long as at least
                // 3 of its lines fit here; otherwise it starts on a fresh page.
                if(used+gap+block.size()>LINES&&LINES-used-gap<3){
                    if(used>0){pages.add(new Page(pageTheme,current));current=new ArrayList<>();used=0;gap=0;}
                }
                if(used==0)pageTheme=sub;
                used+=gap;
                for(var l:block){
                    if(used>=LINES){pages.add(new Page(pageTheme,current));current=new ArrayList<>();used=0;pageTheme=sub;}
                    current.add(new Line(l,0,FIRST_TOP+used*LINE_H));used++;
                }
            }
            if(!current.isEmpty())pages.add(new Page(pageTheme,current));
        }
        if(pages.size()%2==1)pages.add(new Page(pages.getLast().theme(),List.of()));
        // contents: 6 chapters on page 2 (right), the rest on page 3 (left); one per slot row
        pages.set(1,contents(entries,0,5,"CONTENTS",0));pages.set(2,contents(entries,5,10,"CONTENTS (2)",0));
        if(entries.size()>10){var rest=contents(entries,10,entries.size(),null,0);var merged=new ArrayList<>(rest.lines());merged.addAll(pages.get(3).lines());pages.set(3,new Page("contents",merged));}
        return new Layout(pages,entries);
    }
    /** Ores and elemental magic switch to the matching sub-design per section. */
    static String subTheme(String theme,String paragraph){
        String up=paragraph.toUpperCase(Locale.ROOT);
        if(theme.equals("ores")){for(String s:List.of("SIFTIE","ENDERITE","WARDENITE"))if(up.contains("<B>"+s)||up.startsWith("<"+"AQUA><B>"+s)||up.contains(s+" •"))return "ores_"+s.toLowerCase(Locale.ROOT);}
        if(theme.equals("magic")){for(String s:List.of("FIRE","WATER","EARTH","WIND","DARKNESS"))if(up.contains("<B>"+s+" BOOK")||up.contains("<B>"+s+" PASSIVE"))return "magic_"+s.toLowerCase(Locale.ROOT);}
        return theme;
    }
    static int contentsTop(int row){return 22+18*row;}   // centred in container slot row 0..5
    private Page contents(List<Entry> entries,int from,int to,String heading,int firstRow){
        var lines=new ArrayList<Line>();
        if(heading!=null)lines.add(new Line(List.of(new Seg(heading,FAINT)),0,FIRST_TOP));
        for(int i=from;i<to&&i-from<5;i++){
            Entry e=entries.get(i);String num=(i+1)+" ";String label=clean(e.title());String pg=String.valueOf(e.page()+1);
            if(GuideFont.width(num+label)>PAGE_W-GuideFont.width(pg)-6){while(GuideFont.width(num+label+"..")>PAGE_W-GuideFont.width(pg)-6&&label.length()>3)label=label.substring(0,label.length()-1);label=label.trim()+"..";}
            lines.add(new Line(List.of(new Seg(num,FAINT),new Seg(label,e.color())),0,contentsTop(firstRow+i-from)));
            lines.add(new Line(List.of(new Seg(pg,FAINT)),PAGE_W-GuideFont.width(pg),contentsTop(firstRow+i-from)));
        }
        return new Page("contents",lines);
    }
    private Page cover(){
        var l=new ArrayList<Line>();
        l.add(center(List.of(new Seg("THE FORGING",ink("gold"))),FIRST_TOP+18));
        l.add(center(List.of(new Seg("STARTER",ink("gold"))),FIRST_TOP+27));
        l.add(center(List.of(new Seg("A field guide to",FAINT)),FIRST_TOP+45));
        l.add(center(List.of(new Seg("THE FORGE",INK)),FIRST_TOP+54));
        l.add(center(List.of(new Seg("Your journey",INK)),FIRST_TOP+81));
        l.add(center(List.of(new Seg("begins here.",INK)),FIRST_TOP+90));
        return new Page("cover",l);
    }
    private Page howTo(){
        var l=new ArrayList<Line>();int y=contentsTop(1);
        int last=FIRST_TOP+(LINES-1)*LINE_H;
        for(String p:List.of("<gold>HOW TO READ</gold>","Arrows: turn two pages.","Ribbon: back to the contents.","Click a chapter to jump to it.")){
            for(var line:wrap(p)){if(y>last)break;l.add(new Line(line,0,y));y+=LINE_H;}y+=LINE_H;
        }
        return new Page("contents",l);
    }
    private static Line center(List<Seg> segs,int top){return new Line(segs,(PAGE_W-width(segs))/2,top);}

    // ------------------------------------------------------------------ drawing
    /** Pen-moving characters in the guide font: -1..-256 and +1..+256 pixels (powers of two). */
    static String move(int px){
        var sb=new StringBuilder();int base=px<0?0xF801:0xF821;int n=Math.abs(px);
        for(int bit=8;bit>=0;bit--){int v=1<<bit;while(n>=v){sb.append((char)(base+bit));n-=v;}}
        return sb.toString();
    }
    static char picture(String theme,boolean left){return (char)(0xE100+THEMES.indexOf(theme)*2+(left?0:1));}
    Component title(Layout layout,int spread){
        Page l=layout.pages().get(spread*2),r=layout.pages().get(spread*2+1);
        var out=Component.text().color(NamedTextColor.WHITE);int[] pen={TITLE_X};
        java.util.function.IntConsumer to=x->{if(x!=pen[0]){out.append(Component.text(move(x-pen[0])).font(GUIDE_FONT));pen[0]=x;}};
        to.accept(BOOK_X);
        out.append(Component.text(String.valueOf(picture(l.theme(),true))+move(-1)+picture(r.theme(),false)).font(GUIDE_FONT));pen[0]=BOOK_X+129-1+129;
        for(int side=0;side<2;side++){
            Page page=side==0?l:r;int col=side==0?LEFT_X:RIGHT_X;
            for(Line line:page.lines()){
                to.accept(col+line.x());
                if(line.segs().size()==1&&line.segs().getFirst().color()==null){   // a picture
                    out.append(Component.text(line.segs().getFirst().text()).font(GUIDE_FONT));pen[0]+=width(line.segs());continue;}
                var text=Component.text().font(Key.key("blastforge","gy"+line.top()));
                for(Seg s:line.segs())text.append(Component.text(s.text(),s.color()));
                out.append(text.build());pen[0]+=width(line.segs());
            }
            // page number in the outer bottom corner, spread label beside the arrow
            int number=spread*2+side+1;String n=String.valueOf(number);
            to.accept(side==0?LEFT_X:RIGHT_X+PAGE_W-GuideFont.width(n));
            out.append(Component.text(n,FAINT).font(Key.key("blastforge","gy113")));pen[0]+=GuideFont.width(n);
        }
        String label="p. "+(spread*2+1)+"-"+(spread*2+2);
        to.accept(52-GuideFont.width(label)/2);
        out.append(Component.text(label,FAINT).font(Key.key("blastforge","gy113")));pen[0]+=GuideFont.width(label);
        return out.build().decoration(TextDecoration.ITALIC,false);
    }

    // ------------------------------------------------------------------ the screen
    static final class Holder implements InventoryHolder{
        final Layout layout;final int spread;Inventory inventory;
        Holder(Layout layout,int spread){this.layout=layout;this.spread=spread;}
        @Override public Inventory getInventory(){return inventory;}
    }
    int spreads(Layout layout){return layout.pages().size()/2;}
    public void open(Player player,int spread){
        Layout layout=layout();spread=Math.clamp(spread,0,spreads(layout)-1);
        var holder=new Holder(layout,spread);var inv=plugin.getServer().createInventory(holder,54,title(layout,spread));holder.inventory=inv;
        if(spread>0)inv.setItem(PREV,button("Previous pages","Pages "+(spread*2-1)+"-"+(spread*2)));
        if(spread<spreads(layout)-1)inv.setItem(NEXT,button("Next pages","Pages "+(spread*2+3)+"-"+(spread*2+4)));
        inv.setItem(CONTENTS,button("Contents","Back to the chapter list"));
        for(var entry:contentsSlots(layout,spread).entrySet())inv.setItem(entry.getKey(),button(layout.entries().get(entry.getValue()).title(),"Go to page "+(layout.entries().get(entry.getValue()).page()+1)));
        lastSpread.put(player.getUniqueId(),spread);player.openInventory(inv);
    }
    public void open(Player player){open(player,lastSpread.getOrDefault(player.getUniqueId(),0));}
    /** Slot -> chapter index for the contents pages on this spread. */
    static Map<Integer,Integer> contentsSlots(Layout layout,int spread){
        var out=new HashMap<Integer,Integer>();
        // spread 0: right page rows 0-4 = chapters 1-5; spread 1: left page rows 0-4 = chapters 6-10, right page row 0 = chapter 11+
        if(spread==0)fill(out,true,0,5,0,layout.entries().size());
        else if(spread==1){fill(out,false,5,5,0,layout.entries().size());fill(out,true,10,1,0,layout.entries().size());}
        return out;
    }
    private static void fill(Map<Integer,Integer> out,boolean right,int firstChapter,int count,int firstRow,int total){
        for(int i=0;i<count&&firstChapter+i<total;i++){int row=firstRow+i;
            for(int col=right?5:0;col<=(right?8:3);col++){int slot=row*9+col;if(slot==PREV||slot==CONTENTS||slot==NEXT)continue;out.put(slot,firstChapter+i);}}
    }
    private ItemStack button(String name,String hint){
        ItemStack stack=items.create("guide_button");
        stack.editMeta(m->{m.displayName(Component.text(clean(name),NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));m.lore(List.of(Component.text(hint,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));});
        return stack;
    }
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder holder))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||event.getClickedInventory()!=event.getView().getTopInventory())return;
        int slot=event.getRawSlot();Integer target=null;
        if(slot==PREV&&holder.spread>0)target=holder.spread-1;
        else if(slot==NEXT&&holder.spread<spreads(holder.layout)-1)target=holder.spread+1;
        else if(slot==CONTENTS)target=0;
        else{Integer chapter=contentsSlots(holder.layout,holder.spread).get(slot);if(chapter!=null)target=holder.layout.entries().get(chapter).page()/2;}
        if(target!=null){int t=target;dev.duckmc.blastforge.Tasks.entity(plugin,player,()->{if(player.isOnline())open(player,t);});
            player.playSound(player.getLocation(),org.bukkit.Sound.ITEM_BOOK_PAGE_TURN,1f,1f);}
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);}
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event){lastSpread.remove(event.getPlayer().getUniqueId());}
}
