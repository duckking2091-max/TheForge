package dev.duckmc.blastforge.expansion;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "The Forging Starter": the in-game BlastForge guide.
 * Chapter text lives in plugins/TheForge/guide.yml (MiniMessage). Numbers are written as {file:path} placeholders and
 * filled in from the same settings the game uses, so the book never goes out of date. Pages are laid out with Minecraft's
 * font widths (114 px x 14 lines per page) and rebuilt every time the book is opened.
 */
public final class GuideBook implements Listener,AutoCloseable {
    public static final NamespacedKey GUIDE=new NamespacedKey("blastforge","guide_book");
    static final NamespacedKey RECEIVED=new NamespacedKey("blastforge","guide_received");
    static final int WIDTH=114,LINES=14;
    private static final Pattern PLACEHOLDER=Pattern.compile("\\{([a-z]+):([a-z0-9_.\\-]+)(?:([*/])([0-9.]+))?(?:\\|([^}]*))?}");
    private final JavaPlugin plugin;private final Function<String,FileConfiguration> configs;
    public GuideBook(JavaPlugin plugin,Function<String,FileConfiguration> configs){this.plugin=plugin;this.configs=configs;}
    /** Book + iron ingot (any arrangement) = The Forging Starter. */
    public static final NamespacedKey RECIPE=new NamespacedKey("blastforge","forging_starter");
    public void start(){
        if(!new File(plugin.getDataFolder(),"guide.yml").exists()&&plugin.getResource("guide.yml")!=null)plugin.saveResource("guide.yml",false);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        if(configs.apply("config").getBoolean("guide.craftable",true)&&plugin.getServer().getRecipe(RECIPE)==null){
            var recipe=new org.bukkit.inventory.ShapelessRecipe(RECIPE,create());
            recipe.addIngredient(Material.BOOK);recipe.addIngredient(Material.IRON_INGOT);recipe.setGroup("blastforge_guide");
            plugin.getServer().addRecipe(recipe,true);
        }
    }
    @Override public void close(){HandlerList.unregisterAll(this);plugin.getServer().removeRecipe(RECIPE,true);}

    // ------------------------------------------------------------------ content
    FileConfiguration guide(){
        var defaults=new YamlConfiguration();
        try(var in=plugin.getResource("guide.yml")!=null?plugin.getResource("guide.yml"):GuideBook.class.getResourceAsStream("/guide.yml")){if(in!=null)defaults=YamlConfiguration.loadConfiguration(new InputStreamReader(in,StandardCharsets.UTF_8));}catch(java.io.IOException ignored){}
        File file=new File(plugin.getDataFolder(),"guide.yml");
        var loaded=file.exists()?YamlConfiguration.loadConfiguration(file):defaults;
        return loaded.getMapList("chapters").isEmpty()?defaults:loaded;
    }
    static final java.util.regex.Pattern FIGURE=java.util.regex.Pattern.compile("\\s*(?:<newline>)?\\[figure:([a-z0-9_]+)]\\s*$");
    /** Name of the picture a section ends with ("... [figure:recipe_anvil]"), or null. */
    static String figure(String paragraph){var m=FIGURE.matcher(paragraph);return m.find()?m.group(1):null;}
    static String stripFigure(String paragraph){return FIGURE.matcher(paragraph).replaceFirst("");}
    record Chapter(String title,String color,List<String> paragraphs,String theme,String shortTitle){}
    /**
     * Sections about a tier turned off in materials.yml are left out of the guide: their heading names the tier
     * ("SIFTIE • LOCATION", "WARDENITE EQUIPMENT"), an upgrade step leads into it, or their picture shows it.
     */
    boolean hidden(String paragraph){
        FileConfiguration materials=configs.apply("materials");if(materials==null)return false;
        String figure=figure(paragraph),text=stripFigure(paragraph);int nl=text.indexOf("<newline>");
        String heading=(nl<0?text:text.substring(0,nl)).replaceAll("<[^>]*>","").toUpperCase(Locale.ROOT),body=text.toUpperCase(Locale.ROOT);
        for(String tier:List.of("siftie","enderite","wardenite")){
            if(materials.getBoolean("tiers-enabled."+tier,true))continue;String name=tier.toUpperCase(Locale.ROOT);
            if(heading.contains(name)||heading.startsWith("UPGRADE •")&&body.contains(name)||figure!=null&&figure.contains(tier))return true;
        }
        return false;
    }
    List<Chapter> chapters(FileConfiguration guide){
        var out=new ArrayList<Chapter>();
        for(Map<?,?> raw:guide.getMapList("chapters")){
            Object paragraphs=raw.get("text");var list=new ArrayList<String>();
            if(paragraphs instanceof List<?> l)for(Object o:l){String p=String.valueOf(o);if(!hidden(p))list.add(fill(p));}
            out.add(new Chapter(fill(String.valueOf(raw.get("title"))),String.valueOf(raw.containsKey("color")?raw.get("color"):"gold"),list,raw.containsKey("theme")?String.valueOf(raw.get("theme")):null,raw.containsKey("short")?fill(String.valueOf(raw.get("short"))):null));
        }
        return out;
    }
    /** {materials:generation.siftie.min-y}, {villagers:miner.interval-seconds/60}, {magic:books.fire.third.cooldown|60} */
    String fill(String text){
        Matcher m=PLACEHOLDER.matcher(text);var sb=new StringBuilder();
        while(m.find()){
            FileConfiguration cfg=configs.apply(m.group(1));Object value=cfg==null?null:cfg.get(m.group(2));
            String shown;
            if(value instanceof Number n){double v=n.doubleValue();if(m.group(3)!=null){double k=Double.parseDouble(m.group(4));v=m.group(3).equals("*")?v*k:v/k;}shown=number(v);}
            else if(value instanceof List<?> list){var parts=list.stream().map(o->o instanceof Number n?number(n.doubleValue()):String.valueOf(o)).toList();
                shown=parts.size()<2?String.join("",parts):String.join(", ",parts.subList(0,parts.size()-1))+" and "+parts.getLast();}   /* [10, 70, 150, 250] -> "10, 70, 150 and 250" */
            else if(value!=null&&!(value instanceof org.bukkit.configuration.ConfigurationSection))shown=String.valueOf(value);
            else if(m.group(5)!=null)shown=m.group(5);
            else{shown="?";plugin.getLogger().warning("Guide placeholder not found: "+m.group());}
            m.appendReplacement(sb,Matcher.quoteReplacement(shown));
        }
        m.appendTail(sb);return sb.toString();
    }
    static String number(double v){return Math.abs(v-Math.rint(v))<1e-9?Long.toString(Math.round(v)):String.format(Locale.ROOT,"%.2f",v).replaceAll("0+$","").replaceAll("\\.$","");}

    // ------------------------------------------------------------------ layout
    /** Pixel width of a character in Minecraft's default font (including its 1 px gap). */
    static int width(char c,boolean bold){
        int w=switch(c){case 'i','!',',','.',':',';','|','\'' ->2;case 'l','`' ->3;case 'I','t','[',']',' ' ->4;case 'f','k','<','>','(',')','{','}','"','*' ->5;default->6;};
        if(c=='·'||c=='•')w=2;if(c=='«'||c=='»')w=6;
        return w+(bold&&c!=' '?1:0);
    }
    record Styled(char c,boolean bold){}
    /** Plain characters with their bold state (tags removed). */
    static List<Styled> plain(String mini){
        var out=new ArrayList<Styled>();boolean bold=false;
        for(int i=0;i<mini.length();i++){
            char c=mini.charAt(i);
            if(c=='<'){int end=tagEnd(mini,i);if(end>i){String tag=mini.substring(i+1,end).toLowerCase(Locale.ROOT);if(tag.equals("b")||tag.equals("bold"))bold=true;else if(tag.equals("/b")||tag.equals("/bold"))bold=false;i=end;continue;}}
            if(c=='\\'&&i+1<mini.length()){out.add(new Styled(mini.charAt(++i),bold));continue;}
            out.add(new Styled(c,bold));
        }
        return out;
    }
    /** End of a tag starting at i, skipping quoted arguments (which may hold their own tags, e.g. hover text). */
    static int tagEnd(String s,int i){
        char quote=0;
        for(int j=i+1;j<s.length();j++){char c=s.charAt(j);
            if(quote!=0){if(c=='\\')j++;else if(c==quote)quote=0;continue;}
            if(c=='\''||c=='"')quote=c;else if(c=='>')return j;}
        return -1;
    }
    /** Number of book lines a MiniMessage paragraph takes. */
    static int lines(String mini){
        int lines=0;
        for(String part:mini.split("<newline>|<br>|\n",-1)){
            var chars=plain(part);int line=0,count=1;var word=new ArrayList<Styled>();
            chars.add(new Styled(' ',false));
            for(Styled s:chars){
                if(s.c()!=' '){word.add(s);continue;}
                int w=0;for(Styled x:word)w+=width(x.c(),x.bold());
                if(line>0&&line+w>WIDTH){count++;line=0;}
                while(w>WIDTH){count++;w-=WIDTH;}   // a single word longer than a line wraps by itself
                line+=w;if(line+width(' ',false)<=WIDTH)line+=width(' ',false);else{count++;line=0;}
                word.clear();
            }
            lines+=count;
        }
        return lines;
    }
    static final String TOC_LINK="<click:change_page:2><hover:show_text:'<gray>Back to the contents'><dark_gray>« Contents</dark_gray></hover></click>";
    /** All pages as MiniMessage strings (used by the book and by tests). */
    List<String> pageSource(){
        var guide=guide();var chapters=chapters(guide);
        var bodies=new ArrayList<List<String>>();   // pages per chapter
        for(Chapter ch:chapters){
            var pages=new ArrayList<String>();var page=new ArrayList<String>();int used=0;
            int number=chapters.indexOf(ch)+1;   /* "CHAPTER 01" over the title, as in the refined edition */
            String heading="<gray>CHAPTER "+(number<10?"0":"")+number+"</gray><newline><"+ch.color()+"><b>"+ch.title()+"</b></"+ch.color()+">";
            page.add(TOC_LINK);page.add(heading);used=1+lines(heading);
            for(String raw:ch.paragraphs()){
                String p=stripFigure(raw);if(p.isBlank())continue;   // pictures only exist in the open-book reader
                int need=lines(p),gap=used>0?1:0;
                if(need>LINES){   // too long for one page: split into word chunks that fit
                    for(String chunk:split(p)){if(used+1+lines(chunk)>LINES){pages.add(String.join("\n",page));page=new ArrayList<>();used=0;}if(used>0){page.add("");used++;}page.add(chunk);used+=lines(chunk);}
                    continue;
                }
                if(used+gap+need>LINES){pages.add(String.join("\n",page));page=new ArrayList<>();used=0;gap=0;}
                if(gap>0){page.add("");used++;}
                page.add(p);used+=need;
            }
            if(!page.isEmpty())pages.add(String.join("\n",page));
            bodies.add(pages);
        }
        // cover + contents (contents may need several pages)
        var contents=new ArrayList<String>();var tocPage=new ArrayList<String>();int tocLines=0;
        int tocCount=1;
        for(int pass=0;pass<2;pass++){   // pass 0 counts the contents pages, pass 1 writes them with the right page numbers
        contents.clear();tocPage=new ArrayList<>();tocLines=0;
        int start=1+tocCount+1;   // first chapter page number
        tocPage.add("<b><dark_gray>Contents</dark_gray></b>");tocLines=1;
        for(int i=0;i<chapters.size();i++){
            Chapter ch=chapters.get(i);String entry="<click:change_page:"+start+"><hover:show_text:'<gray>Go to page "+start+"'><"+ch.color()+">"+(i+1<10?"0":"")+(i+1)+". "+ch.title()+"</"+ch.color()+"></hover></click>";
            if(tocLines+lines(entry)>LINES){contents.add(String.join("\n",tocPage));tocPage=new ArrayList<>();tocLines=0;}
            tocPage.add(entry);tocLines+=lines(entry);start+=bodies.get(i).size();
        }
        contents.add(String.join("\n",tocPage));
        tocCount=contents.size();
        }
        var all=new ArrayList<String>();all.add(fill(guide.getString("cover","<b><gold>The Forging Starter</gold></b>")));all.addAll(contents);
        for(var b:bodies)all.addAll(b);
        return all;
    }
    private static List<String> split(String mini){
        String text=MiniMessage.miniMessage().stripTags(mini);var out=new ArrayList<String>();var cur=new StringBuilder();
        for(String word:text.split(" ")){String next=cur.isEmpty()?word:cur+" "+word;if(lines(next)>LINES-2&&!cur.isEmpty()){out.add(cur.toString());cur=new StringBuilder(word);}else cur=new StringBuilder(next);}
        if(!cur.isEmpty())out.add(cur.toString());return out;
    }
    List<Component> pages(){var mm=MiniMessage.miniMessage();return pageSource().stream().map(mm::deserialize).toList();}

    // ------------------------------------------------------------------ item
    public ItemStack create(){
        ItemStack book=new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class,meta->{meta.setTitle("The Forging Starter");meta.setAuthor("The Forge");meta.setGeneration(BookMeta.Generation.ORIGINAL);meta.pages(pages());meta.getPersistentDataContainer().set(GUIDE,PersistentDataType.BYTE,(byte)1);});
        return book;
    }
    public static boolean isGuide(ItemStack stack){return stack!=null&&stack.getType()==Material.WRITTEN_BOOK&&stack.hasItemMeta()&&stack.getItemMeta().getPersistentDataContainer().has(GUIDE);}
    public void give(Player player){var left=player.getInventory().addItem(create());left.values().forEach(i->player.getWorld().dropItemNaturally(player.getLocation(),i));}

    @EventHandler public void join(PlayerJoinEvent event){
        Player p=event.getPlayer();var cfg=configs.apply("config");
        if(plugin.getServer().getRecipe(RECIPE)!=null)p.discoverRecipe(RECIPE);   // shows in the recipe book
        if(!cfg.getBoolean("guide.give-on-first-join",true)||p.getPersistentDataContainer().has(RECEIVED))return;
        p.getPersistentDataContainer().set(RECEIVED,PersistentDataType.BYTE,(byte)1);   // only ever once per player
        if(!p.hasPlayedBefore()||cfg.getBoolean("guide.give-to-existing-players",false))dev.duckmc.blastforge.Tasks.entityLater(plugin,p,20,()->{if(p.isOnline())give(p);});
    }
    private GuideReader reader;private PackVisuals pack;
    /** With the resource pack, the book opens as a two-page themed reader; without it, as a normal written book. */
    public void reader(GuideReader reader,PackVisuals pack){this.reader=reader;this.pack=pack;}
    /** Always open the newest text, and update the copy in hand. */
    @EventHandler(priority=EventPriority.HIGH) public void open(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        ItemStack held=event.getPlayer().getInventory().getItemInMainHand();if(!isGuide(held))return;
        event.setCancelled(true);
        if(reader!=null&&pack!=null&&pack.loaded(event.getPlayer())){reader.open(event.getPlayer());return;}
        held.editMeta(BookMeta.class,meta->meta.pages(pages()));event.getPlayer().openBook(held);
    }
}
