package dev.duckmc.blastforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.Predicate;
import dev.duckmc.blastforge.expansion.Mailbox;
import dev.duckmc.blastforge.expansion.PersistentStore;
import dev.duckmc.blastforge.expansion.ItemCodec;

final class ExtractionMenu implements Listener {
    private final JavaPlugin plugin;
    private final Predicate<Block> station;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private Mailbox mailbox;
    private PersistentStore store;
    private java.util.function.IntSupplier experienceCost=()->0;
    private static final int PAGE_SIZE=45;
    private static final class Session implements InventoryHolder {
        final UUID id=UUID.randomUUID();
        final Player player;
        final Location location;
        final ItemStack original,books;
        final List<Enchantment> options;
        final Set<Enchantment> selected=new HashSet<>();
        final Inventory inventory;
        int page;
        boolean finished;
        boolean locked;
        boolean virtual;
        Session(Player player,Location location,ItemStack original,ItemStack books) {
            this.player=player;this.location=location.clone();this.original=original.clone();this.books=books.clone();
            options=original.getEnchantments().keySet().stream().sorted(Comparator.comparing(e->e.getKey().toString())).toList();
            inventory=Bukkit.createInventory(this,54,Component.text("Choose enchantments to extract"));
        }
        @Override public Inventory getInventory(){return inventory;}
    }
    ExtractionMenu(JavaPlugin plugin,Predicate<Block> station){this.plugin=plugin;this.station=station;}
    void experienceCost(java.util.function.IntSupplier cost){this.experienceCost=cost;}
    void persistentReturns(Mailbox mailbox,PersistentStore store){
        this.mailbox=mailbox;this.store=store;
        for(var entry:store.entries("extraction_session").entrySet()){
            String[] parts=entry.getValue().split("\n",3);
            if(parts.length==3&&(parts[0].equals("S")||parts[0].equals("C")))mailbox.queue(UUID.fromString(entry.getKey()),UUID.fromString(parts[1]),ItemCodec.decode(parts[2]),0);
            else store.put("quarantine","extraction:"+entry.getKey()+":"+(parts.length>1?parts[1]:UUID.randomUUID()),entry.getValue());
            store.remove("extraction_session",entry.getKey());
        }
    }
    // Caller has removed both inputs from the anvil; this menu now owns them until
    // exactly one confirmation or cancellation returns the held items.
    void open(Player player,Location location,ItemStack item,ItemStack books) {
        open(player,location,item,books,false);
    }
    void open(Player player,Location location,ItemStack item,ItemStack books,boolean virtual) {
        player.closeInventory();
        Session previous=sessions.get(player.getUniqueId());if(previous!=null)cancel(previous);
        Session s=new Session(player,location,item,books);
        s.virtual=virtual;
        sessions.put(player.getUniqueId(),s);
        if(store!=null)store.put("extraction_session",player.getUniqueId().toString(),"S\n"+s.id+"\n"+ItemCodec.encode(List.of(s.original,s.books)));
        try {
            draw(s);
            if(player.openInventory(s.inventory)==null) cancel(s);
        } catch(RuntimeException ex) { cancel(s); throw ex; }
    }
    // Establish ownership synchronously; only the screen change waits until after
    // the inventory callback. Pending selectors are included in shutdown refunds.
    void handoff(Player player,Location location,ItemStack item,ItemStack books,boolean virtual,Runnable releaseSource){
        if(sessions.containsKey(player.getUniqueId()))throw new IllegalStateException("An extraction selector already owns this player's inputs");
        Inventory source=player.getOpenInventory().getTopInventory();
        Session s=new Session(player,location,item,books);s.virtual=virtual;
        String key=player.getUniqueId().toString(),encoded=ItemCodec.encode(List.of(s.original,s.books));
        if(store!=null)store.put("extraction_session",key,"U\n"+s.id+"\n"+encoded);
        releaseSource.run();
        if(store!=null)store.put("extraction_session",key,"S\n"+s.id+"\n"+encoded);
        sessions.put(player.getUniqueId(),s);
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if(s.finished||sessions.get(player.getUniqueId())!=s)return;
            if(!player.isOnline()||!player.getOpenInventory().getTopInventory().equals(source)){cancel(s);return;}
            player.closeInventory();draw(s);
            if(player.openInventory(s.inventory)==null)cancel(s);
        });
    }
    private Session session(InventoryView view) {
        Session s=sessions.get(view.getPlayer().getUniqueId());
        return s!=null && !s.finished && view.getTopInventory().equals(s.inventory) ? s : null;
    }
    private static ItemStack button(Material material,String name,String description) {
        ItemStack item=new ItemStack(material);
        item.editMeta(m->{
            m.displayName(Component.text(name,NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false));
            m.lore(List.of(Component.text(description,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
        });
        return item;
    }
    private void draw(Session s) {
        s.inventory.clear();
        for(int slot=0;slot<PAGE_SIZE;slot++) {
            int index=s.page*PAGE_SIZE+slot;if(index>=s.options.size())break;
            Enchantment enchant=s.options.get(index);
            boolean selected=s.selected.contains(enchant);
            ItemStack icon=new ItemStack(Material.ENCHANTED_BOOK);
            icon.editMeta(EnchantmentStorageMeta.class,m->{
                m.addStoredEnchant(enchant,s.original.getEnchantmentLevel(enchant),true);
                m.displayName(Component.text(selected ? "Selected: " : "Select: ",selected?NamedTextColor.GREEN:NamedTextColor.YELLOW)
                    .append(enchant.displayName(s.original.getEnchantmentLevel(enchant))).decoration(TextDecoration.ITALIC,false));
                m.lore(List.of(Component.text(selected?"Click to keep this enchantment on the item.":"Click to move this enchantment to the book.",NamedTextColor.GRAY)));
            });
            s.inventory.setItem(slot,icon);
        }
        s.inventory.setItem(45,button(Material.LIME_DYE,"Select all","Select every enchantment on this item."));
        s.inventory.setItem(46,button(Material.GRAY_DYE,"Clear selection","Keep every enchantment on this item."));
        if(s.page>0)s.inventory.setItem(48,button(Material.ARROW,"Previous page","Show previous enchantments."));
        if((s.page+1)*PAGE_SIZE<s.options.size())s.inventory.setItem(50,button(Material.ARROW,"Next page","Show more enchantments."));
        s.inventory.setItem(49,button(Material.PAPER,"Selected: "+s.selected.size(),"One book receives all selected enchantments. No XP refund."));
        s.inventory.setItem(52,button(Material.BARRIER,"Cancel","Return both original inputs unchanged."));
        s.inventory.setItem(53,button(Material.EMERALD,"Extract selected ("+s.selected.size()+")","Consume 1 book and "+Math.max(0,experienceCost.getAsInt())+" XP levels."));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        Session s=session(event.getView());if(s==null)return;
        boolean denied=event.isCancelled();event.setCancelled(true);
        if(s.locked)return;
        if(denied || (event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT))return;
        int slot=event.getRawSlot();
        if(slot>=0 && slot<PAGE_SIZE) {
            int index=s.page*PAGE_SIZE+slot;
            if(index<s.options.size()) {var e=s.options.get(index);if(!s.selected.add(e))s.selected.remove(e);}
        } else switch(slot) {
            case 45 -> s.selected.addAll(s.options);
            case 46 -> s.selected.clear();
            case 48 -> s.page=Math.max(0,s.page-1);
            case 50 -> s.page=Math.min((s.options.size()-1)/PAGE_SIZE,s.page+1);
            case 52 -> {plugin.getServer().getScheduler().runTask(plugin,()->{if(!s.finished)s.player.closeInventory();});return;}
            case 53 -> {plugin.getServer().getScheduler().runTask(plugin,()->confirm(s));return;}
            default -> {return;}
        }
        draw(s);
    }
    private void confirm(Session s) {
        if(s.finished || s.locked || session(s.player.getOpenInventory())!=s)return;
        if(!s.virtual&&(!s.player.getWorld().equals(s.location.getWorld()) || s.player.getLocation().distanceSquared(s.location)>64
            || !station.test(s.location.getBlock()))) {
            s.player.sendMessage(Component.text("Return to The Forging Anvil, or cancel to recover your items.",NamedTextColor.RED));return;
        }
        var result=Extraction.extract(s.original,s.selected);if(result==null)return;
        int levels=Math.max(0,experienceCost.getAsInt());
        if(s.player.getLevel()<levels){s.player.sendMessage(Component.text("You need "+levels+" XP levels.",NamedTextColor.RED));return;}
        var returns=new ArrayList<ItemStack>();returns.add(result.item());returns.add(result.book());
        if(s.books.getAmount()>1){ItemStack left=s.books.clone();left.setAmount(left.getAmount()-1);returns.add(left);}
        s.locked=true;
        try{
            if(store!=null)store.put("extraction_session",s.player.getUniqueId().toString(),"U:"+levels+"\n"+s.id+"\n"+ItemCodec.encode(List.of(s.original,s.books)));
            s.player.giveExpLevels(-levels);
            if(store!=null){mailbox.savePlayer(s.player);store.put("extraction_session",s.player.getUniqueId().toString(),"C\n"+s.id+"\n"+ItemCodec.encode(returns));}
        }catch(RuntimeException error){
            s.inventory.clear();s.player.closeInventory();
            s.player.sendMessage(Component.text("Extraction paused for administrator recovery; saved data could not be verified.",NamedTextColor.RED));
            plugin.getLogger().severe("Extraction boundary retained for "+s.player.getUniqueId()+": "+error);return;
        }
        s.finished=true;sessions.remove(s.player.getUniqueId(),s);
        s.player.closeInventory();
        if(mailbox!=null){mailbox.queue(s.player.getUniqueId(),s.id,returns,0);store.remove("extraction_session",s.player.getUniqueId().toString());}
        else returns.forEach(item->give(s.player,item));
        s.player.playSound(s.location,Sound.BLOCK_GRINDSTONE_USE,1,1);
    }
    private void cancel(Session s) {
        if(s.finished)return;
        s.finished=true;sessions.remove(s.player.getUniqueId(),s);
        s.inventory.clear();
        if(s.locked){if(store!=null){String value=store.get("extraction_session",s.player.getUniqueId().toString());if(value!=null)store.put("quarantine","extraction:"+s.player.getUniqueId()+":"+s.id,value);}return;}
        if(mailbox!=null){mailbox.queue(s.player.getUniqueId(),s.id,List.of(s.original,s.books),0);store.remove("extraction_session",s.player.getUniqueId().toString());}
        else{give(s.player,s.original);give(s.player,s.books);}
    }
    private static void give(Player player,ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left->player.getWorld().dropItemNaturally(player.getLocation(),left));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){if(session(event.getView())!=null)event.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent event){Session s=session(event.getView());if(s!=null)cancel(s);}
    void shutdown(){for(Session s:List.copyOf(sessions.values())){s.player.closeInventory();cancel(s);}}
}
