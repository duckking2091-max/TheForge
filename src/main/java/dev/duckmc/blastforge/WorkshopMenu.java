package dev.duckmc.blastforge;

import dev.duckmc.blastforge.expansion.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.*;

/** Three tabs share one inventory. All movable items are session-owned, never previews. */
final class WorkshopMenu implements Listener {
    enum Tab { FORGING,UPGRADING,RITUAL }
    // Layouts follow the vanilla screens they imitate. Forging: item + item -> result (anvil).
    // Upgrading: template, gear, ingot -> result (smithing table); the array order stays gear, template, ingot.
    // Ritual: eight ingredients in a ring around the scythe, clockwise from the top: book, Wardenite ingot,
    // Enderite stick, Dragon's Heart, Echo Shard, Warden's Heart, Bat Wing, Netherite ingot.
    static final int FIRST=20,SECOND=22,RESULT=25,CENTER=22,CONFIRM=49,TABS=45; /* tab buttons sit in the bottom-left corner: 45, 46, 47 */
    // Ritual ring slots sit at (+-1,+-2) and (+-2,+-1) grid steps from the centre: all eight are the same distance away, a true circle.
    static final int[][] INPUTS={{FIRST,SECOND},{21,20,22},{5,15,33,41,39,29,11,3}};
    private static final String[] RITUAL_LABELS={"Magic book (any element or Darkness)","Wardenite ingot","Enderite stick","Dragon's Heart","Echo Shard","Warden's Heart","Bat Wing","Netherite ingot"};
    private final JavaPlugin plugin;
    private final ExpansionItems items;
    private final Function<String,FileConfiguration> configs;
    private final PersistentStore store;
    private final Mailbox mailbox;
    private final ExtractionMenu extraction;
    private final BiConsumer<Player,Location> nativeAnvil;
    private final Predicate<Location> station;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Craft> crafts=new HashMap<>();
    private final Set<String> busy=new HashSet<>();
    private BiConsumer<Location,String> strike=(l,t)->{};
    private PackVisuals pack;
    private final class Session implements InventoryHolder {
        final UUID id=UUID.randomUUID();final Player player;final Location location;final Inventory inventory;
        final EnumMap<Tab,Map<Integer,ItemStack>> buffers=new EnumMap<>(Tab.class);
        Tab tab=Tab.FORGING;boolean closed,uncertain,transferring;UUID running;
        Session(Player player,Location location,Component title){this.player=player;this.location=location==null?null:location.clone();for(Tab t:Tab.values())buffers.put(t,new HashMap<>());inventory=Bukkit.createInventory(this,54,title);}
        @Override public Inventory getInventory(){return inventory;}
        Map<Integer,ItemStack> inputs(){return buffers.get(tab);}
        String stationKey(){return location==null?"virtual:"+player.getUniqueId():location.getWorld().getUID()+":"+location.getBlockX()+":"+location.getBlockY()+":"+location.getBlockZ();}
        List<ItemStack> owned(){var result=new ArrayList<ItemStack>();for(var values:buffers.values())values.values().forEach(i->{if(i!=null&&!i.getType().isAir())result.add(i.clone());});ItemStack cursor=player.getItemOnCursor();if(!cursor.getType().isAir())result.add(cursor.clone());return result;}
    }
    private record Craft(UUID id,UUID owner,String key,Location location,UUID scythe,List<ItemStack> consumed,List<ItemStack> outputs,int levels,Session session,BukkitTask task){}
    WorkshopMenu(JavaPlugin plugin,ExpansionItems items,Function<String,FileConfiguration> configs,PersistentStore store,Mailbox mailbox,ExtractionMenu extraction,BiConsumer<Player,Location> nativeAnvil,Predicate<Location> station){this.plugin=plugin;this.items=items;this.configs=configs;this.store=store;this.mailbox=mailbox;this.extraction=extraction;this.nativeAnvil=nativeAnvil;this.station=station;}
    void start(){plugin.getServer().getPluginManager().registerEvents(this,plugin);recover();}
    void strikeHandler(BiConsumer<Location,String> handler){strike=handler;}
    private BiConsumer<Location,Integer> heat=(l,t)->{};
    /** Rune hearth animation: ticks>0 started, 0 finished, -1 cancelled. */
    void heatHandler(BiConsumer<Location,Integer> handler){heat=handler;}
    void pack(PackVisuals value){pack=value;if(value!=null)value.onStatusChange(player->{Session session=sessions.get(player.getUniqueId());if(session!=null&&!session.closed)draw(session);});}
    void open(Player player,Location location){
        if(!player.hasPermission("blastforge.use")&&!player.isOp()){notice(player,"no-permission","You cannot use this workstation.",Map.of());return;}
        player.closeInventory();
        // The screen opens on the Forging tab with its background already in the title, not a blank chest.
        Session session=new Session(player,location,pack!=null?pack.title(player,PackVisuals.Theme.FORGING,0):Component.text("Forging",NamedTextColor.AQUA));sessions.put(player.getUniqueId(),session);draw(session);
        if(player.openInventory(session.inventory)==null){sessions.remove(player.getUniqueId());return;}
        checkpoint(session);
    }
    private Session find(InventoryView view){Session s=sessions.get(view.getPlayer().getUniqueId());return s!=null&&!s.closed&&s.inventory.equals(view.getTopInventory())?s:null;}
    private static ItemStack icon(Material type,String name,String... lore){var result=new ItemStack(type);result.editMeta(m->{m.displayName(Component.text(name,NamedTextColor.GOLD));m.lore(Arrays.stream(lore).map(l->Component.text(l,NamedTextColor.GRAY)).toList());});return result;}
    /** Custom tab icon with the resource pack, the vanilla item without it. */
    private ItemStack tab(boolean art,String id,Material fallback,String name,String... lore){
        if(!art)return icon(fallback,name,lore);
        ItemStack result=items.create(id);result.editMeta(m->{m.displayName(Component.text(name,NamedTextColor.GOLD).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));m.lore(Arrays.stream(lore).map(l->(Component)Component.text(l,NamedTextColor.GRAY)).toList());});return result;
    }
    private void draw(Session s){
        s.inventory.clear();if(pack==null||!pack.loaded(s.player)){ItemStack pane=icon(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=0;i<54;i++)s.inventory.setItem(i,pane);}
        boolean art=pack!=null&&pack.loaded(s.player);
        s.inventory.setItem(TABS,tab(art,"tab_forging",Material.ANVIL,"Forging","Combine and transfer enchantments."));s.inventory.setItem(TABS+1,tab(art,"tab_upgrading",Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,"Upgrading","Template + gear + ingot."));s.inventory.setItem(TABS+2,tab(art,"tab_ritual",Material.SOUL_LANTERN,"Dark Ritual","Forge the Legendary Scythe."));
        if(s.tab==Tab.FORGING)s.inventory.setItem(8,tab(art,"tab_rename",Material.NAME_TAG,"Rename","Opens a vanilla anvil to rename an item.")); /* Rename only on the Forging tab */
        s.inventory.setItem(53,tab(art,"tab_close",Material.BARRIER,"Close and return ingredients"));
        for(int slot:INPUTS[s.tab.ordinal()])s.inventory.setItem(slot,s.inputs().get(slot));
        if(s.tab==Tab.FORGING){
            if(!art){s.inventory.setItem(21,icon(Material.WHITE_STAINED_GLASS_PANE,"+","First item is kept; the second is consumed."));s.inventory.setItem(23,icon(Material.WHITE_STAINED_GLASS_PANE,"→"));s.inventory.setItem(24,icon(Material.WHITE_STAINED_GLASS_PANE,"→"));}
            ItemStack base=s.inputs().get(FIRST),donor=s.inputs().get(SECOND);Forge forge=forge(s);
            ItemStack result=Extraction.eligible(base,donor)?icon(Material.ENCHANTED_BOOK,"Select enchantments to extract"):forge==null?null:forge.result().clone();
            s.inventory.setItem(RESULT,result!=null?priced(result,cost(s)):art?null:icon(Material.RED_STAINED_GLASS_PANE,"Insert compatible equipment"));
        }else if(s.tab==Tab.UPGRADING){
            if(!art){s.inventory.setItem(23,icon(Material.WHITE_STAINED_GLASS_PANE,"→"));s.inventory.setItem(24,icon(Material.WHITE_STAINED_GLASS_PANE,"→"));}
            String[] hints={"Gear to upgrade","Upgrade template","Ingot"};Material[] ghosts={Material.LIGHT_GRAY_STAINED_GLASS_PANE,Material.LIGHT_GRAY_STAINED_GLASS_PANE,Material.LIGHT_GRAY_STAINED_GLASS_PANE};
            if(!art)for(int i=0;i<3;i++)if(!s.inputs().containsKey(INPUTS[1][i]))s.inventory.setItem(INPUTS[1][i],icon(ghosts[i],hints[i]));
            var recipe=WorkshopRecipes.upgrade(items,s.inputs().get(INPUTS[1][0]),s.inputs().get(INPUTS[1][1]),s.inputs().get(INPUTS[1][2]));
            s.inventory.setItem(RESULT,recipe!=null?priced(recipe.item(),cost(s)):art?null:icon(Material.RED_STAINED_GLASS_PANE,"Template + gear + ingot"));
        }else{
            ItemStack scythe=items.create("legendary_scythe");scythe.editMeta(m->m.lore(List.of(Component.text("Result of the Dark Ritual",NamedTextColor.GRAY))));s.inventory.setItem(CENTER,scythe);
            // Empty ring slots show the ingredient they need, so the circle reads like a recipe.
            for(int i=0;i<8;i++)if(!s.inputs().containsKey(INPUTS[2][i]))s.inventory.setItem(INPUTS[2][i],ritualHint(i));
        }
        int cost=cost(s);
        // Forging and upgrading confirm by clicking the result, like vanilla; only the ritual keeps a button.
        if(s.tab==Tab.RITUAL||s.running!=null)s.inventory.setItem(CONFIRM,icon(s.running==null?Material.SMITHING_TABLE:Material.CLOCK,s.running==null?"Confirm "+s.tab.name().toLowerCase(Locale.ROOT)+" ("+cost+" XP levels)":"Forging in progress","Required XP levels: "+cost,s.tab==Tab.RITUAL?"Scythe slots occupied: "+store.occupiedScythes():"Ingredients are consumed only after validation."));
        if(pack!=null&&s.player.getOpenInventory().getTopInventory()==s.inventory)pack.updateTitle(s.player,s.player.getOpenInventory(),PackVisuals.Theme.valueOf(s.tab.name()),(s.inputs().containsKey(FIRST)?1:0)|(s.inputs().containsKey(SECOND)?2:0));
    }
    private ItemStack ritualHint(int index){
        ItemStack hint=switch(index){case 0->items.create("darkness_book");case 1->items.create("wardenite_ingot");case 2->items.create("enderite_stick");case 3->items.create("dragon_heart");
            case 4->new ItemStack(Material.ECHO_SHARD);case 5->items.create("warden_heart");case 6->items.create("bat_wing");default->new ItemStack(Material.NETHERITE_INGOT);};
        // A display-only copy: it is not in the session buffer, so clicking it can never take it.
        hint.editMeta(m->{m.displayName(Component.text("Place: "+RITUAL_LABELS[index],NamedTextColor.LIGHT_PURPLE));m.lore(List.of(Component.text("Ritual ingredient slot",NamedTextColor.GRAY)));m.getPersistentDataContainer().set(new NamespacedKey("blastforge","ritual_hint"),PersistentDataType.BYTE,(byte)1);});
        return hint;
    }
    /** Whether a ritual ring slot accepts this item: each slot takes only its own ingredient. */
    private boolean ritualFits(int index,ItemStack stack){
        String id=items.id(stack);
        return switch(index){case 0->Set.of("fire_book","water_book","earth_book","wind_book","darkness_book").contains(id);case 1->id.equals("wardenite_ingot");case 2->id.equals("enderite_stick");case 3->id.equals("dragon_heart");
            case 4->stack.getType()==Material.ECHO_SHARD&&id.isEmpty();case 5->id.equals("warden_heart");case 6->id.equals("bat_wing");default->stack.getType()==Material.NETHERITE_INGOT&&id.isEmpty();};
    }
    /** Preview copy of a result showing its XP cost, like the vanilla "Enchantment Cost" line. */
    private static ItemStack priced(ItemStack result,int levels){
        ItemStack preview=result.clone();preview.editMeta(m->{var lore=new ArrayList<Component>(m.hasLore()?m.lore():List.of());lore.add(Component.empty());
            lore.add(Component.text(levels==0?"Free - click to take":"Cost: "+levels+" XP level"+(levels==1?"":"s")+" - click to take",NamedTextColor.GREEN));m.lore(lore);});return preview;
    }
    record Forge(ItemStack result,int donorUsed,int levels){}
    /** Forge tab: custom enchantment merging plus the vanilla anvil's repair-with-material and combine-two-items. */
    private Forge forge(Session s){
        ItemStack base=s.inputs().get(FIRST),donor=s.inputs().get(SECOND);if(base==null||donor==null||base.getAmount()!=1)return null;
        int enchantLevels=configs.apply("config").getInt("forging.experience-levels",4);
        ItemStack merged=donor.getAmount()==1?WorkshopRecipes.forge(items,configs.apply("scythe"),base,donor):null;
        if(!items.id(base).equals("legendary_scythe")&&base.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damage&&damage.getDamage()>0){
            int max=damage.hasMaxDamage()?damage.getMaxDamage():base.getType().getMaxDurability();
            if(max>0&&WorkshopRecipes.repairMaterial(items,base,donor)){
                // Vanilla: each material restores a quarter of the durability; only the materials needed are used.
                int quarter=Math.max(1,max/4),units=Math.min(donor.getAmount(),(damage.getDamage()+quarter-1)/quarter);
                ItemStack result=base.clone();int left=Math.max(0,damage.getDamage()-units*quarter);result.editMeta(org.bukkit.inventory.meta.Damageable.class,m->m.setDamage(left));
                return new Forge(result,units,units);
            }
            if(donor.getAmount()==1&&donor.getType()==base.getType()&&items.id(donor).equals(items.id(base))&&donor.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable other){
                // Vanilla combine: both items' remaining durability plus a 12% bonus, and any enchantments merged.
                ItemStack result=(merged!=null?merged:base).clone();int remaining=(max-damage.getDamage())+(max-other.getDamage())+max*12/100;int left=Math.max(0,max-remaining);
                result.editMeta(org.bukkit.inventory.meta.Damageable.class,m->m.setDamage(left));return new Forge(result,1,2+(merged!=null?enchantLevels:0));
            }
        }
        return merged==null?null:new Forge(merged,1,enchantLevels);
    }
    private int cost(Session s){
        if(s.tab==Tab.FORGING){if(Extraction.eligible(s.inputs().get(FIRST),s.inputs().get(SECOND)))return configs.apply("config").getInt("forging.extraction-levels",0);Forge f=forge(s);return f==null?0:f.levels();}
        if(s.tab==Tab.UPGRADING){var r=WorkshopRecipes.upgrade(items,s.inputs().get(INPUTS[1][0]),s.inputs().get(INPUTS[1][1]),s.inputs().get(INPUTS[1][2]));return r==null?0:configs.apply("materials").getInt("upgrades."+r.tier(),switch(r.tier()){case "netherite"->5;case "siftie"->8;case "enderite"->12;default->16;});}
        return configs.apply("scythe").getInt("ritual-experience-levels",0);
    }
    private boolean input(Session s,int slot){return Arrays.stream(INPUTS[s.tab.ordinal()]).anyMatch(i->i==slot);}
    private int limit(Session s,int slot,ItemStack stack){
        if(stack==null||stack.getType().isAir())return 0;
        if(s.tab==Tab.FORGING){if(slot==FIRST)return ExpansionMerge.family(stack).isEmpty()?0:1;
            // Second slot: books, a donor item, an enchanted book, or a stack of repair material.
            return stack.getMaxStackSize();}
        if(s.tab==Tab.UPGRADING)return slot==INPUTS[1][0]?(ExpansionMerge.family(stack).isEmpty()?0:1):stack.getMaxStackSize();
        for(int i=0;i<8;i++)if(INPUTS[2][i]==slot)return ritualFits(i,stack)?stack.getMaxStackSize():0;
        return 0;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event){
        Session s=find(event.getView());if(s==null)return;boolean denied=event.isCancelled();event.setCancelled(true);if(denied)return;
        if(s.transferring)return;
        int raw=event.getRawSlot();ClickType click=event.getClick();
        if(click!=ClickType.LEFT&&click!=ClickType.RIGHT&&click!=ClickType.SHIFT_LEFT&&click!=ClickType.SHIFT_RIGHT)return;
        if(raw>=TABS&&raw<=TABS+2){if(s.running!=null)return;s.tab=Tab.values()[raw-TABS];draw(s);return;}
        if(raw==53){plugin.getServer().getScheduler().runTask(plugin,()->s.player.closeInventory());return;}
        if(s.uncertain)return;
        if(raw==8){if(s.running!=null||s.tab!=Tab.FORGING)return;plugin.getServer().getScheduler().runTask(plugin,()->{if(!s.closed)nativeAnvil.accept(s.player,s.location);});return;}
        if(s.tab==Tab.RITUAL?raw==CONFIRM||raw==CENTER:raw==RESULT){confirm(s);return;} /* clicking the result works like vanilla */
        if(s.running!=null)return;
        if(event.isShiftClick()){
            if(input(s,raw)){
                ItemStack value=s.inputs().get(raw);if(value==null)return;
                var left=s.player.getInventory().addItem(value.clone());s.inputs().remove(raw);if(!left.isEmpty())s.inputs().put(raw,left.values().iterator().next());
            }else if(event.getClickedInventory()==s.player.getInventory()){
                ItemStack source=event.getCurrentItem();if(source==null)return;ItemStack left=source.clone();
                for(int slot:INPUTS[s.tab.ordinal()]){int max=limit(s,slot,left);if(max==0)continue;ItemStack existing=s.inputs().get(slot);if(existing!=null&&!existing.isSimilar(left))continue;
                    int count=Math.min(left.getAmount(),max-(existing==null?0:existing.getAmount()));if(count<=0)continue;ItemStack placed=left.clone();placed.setAmount(count+(existing==null?0:existing.getAmount()));s.inputs().put(slot,placed);left.setAmount(left.getAmount()-count);if(left.getAmount()==0)break;}
                s.player.getInventory().setItem(event.getSlot(),left.getAmount()==0?null:left);
            }else return;
        }else if(input(s,raw)){
            ItemStack value=s.inputs().get(raw),cursor=s.player.getItemOnCursor();
            if(cursor.getType().isAir()){
                if(value==null)return;int count=click==ClickType.RIGHT?(value.getAmount()+1)/2:value.getAmount();ItemStack taken=value.clone();taken.setAmount(count);s.player.setItemOnCursor(taken);decrease(s.inputs(),raw,count);
            }else{
                int max=limit(s,raw,cursor);if(max==0)return;
                if(value==null||value.isSimilar(cursor)){
                    int count=Math.min(click==ClickType.RIGHT?1:cursor.getAmount(),max-(value==null?0:value.getAmount()));if(count<=0)return;
                    ItemStack placed=cursor.clone();placed.setAmount(count+(value==null?0:value.getAmount()));s.inputs().put(raw,placed);cursor.setAmount(cursor.getAmount()-count);s.player.setItemOnCursor(cursor.getAmount()==0?null:cursor);
                }else{if(cursor.getAmount()>max)return;s.inputs().put(raw,cursor.clone());s.player.setItemOnCursor(value);}
            }
        }else if(event.getClickedInventory()==s.player.getInventory()){
            ItemStack value=event.getCurrentItem(),cursor=s.player.getItemOnCursor();
            if(cursor.getType().isAir()){if(value==null)return;int n=click==ClickType.RIGHT?(value.getAmount()+1)/2:value.getAmount();var taken=value.clone();taken.setAmount(n);s.player.setItemOnCursor(taken);value.setAmount(value.getAmount()-n);s.player.getInventory().setItem(event.getSlot(),value.getAmount()==0?null:value);}
            else if(value==null||value.getType().isAir()||value.isSimilar(cursor)){
                int n=Math.min(click==ClickType.RIGHT?1:cursor.getAmount(),cursor.getMaxStackSize()-(value==null?0:value.getAmount()));if(n<=0)return;var placed=cursor.clone();placed.setAmount(n+(value==null?0:value.getAmount()));s.player.getInventory().setItem(event.getSlot(),placed);cursor.setAmount(cursor.getAmount()-n);s.player.setItemOnCursor(cursor.getAmount()==0?null:cursor);
            }else{s.player.getInventory().setItem(event.getSlot(),cursor);s.player.setItemOnCursor(value);}
        }else return;
        checkpoint(s);draw(s);plugin.getServer().getScheduler().runTask(plugin,s.player::updateInventory);
    }
    private static void decrease(Map<Integer,ItemStack> map,int slot,int n){ItemStack value=map.get(slot);if(value==null)return;value.setAmount(value.getAmount()-n);if(value.getAmount()<=0)map.remove(slot);}
    private void checkpoint(Session s){
        String encoded=ItemCodec.encode(s.owned());String key=s.player.getUniqueId().toString();
        store.put("workshop_session",key,"U\n"+s.id+"\n"+encoded);
        try{mailbox.savePlayer(s.player);}catch(RuntimeException error){s.uncertain=true;throw error;}
        store.put("workshop_session",key,"S\n"+s.id+"\n"+encoded);
    }
    private void confirm(Session s){
        if(s.uncertain||!s.player.hasPermission("blastforge.use")){message(s.player,"This transaction is unavailable; ask an administrator to check saved data.");return;}
        if(s.running!=null||busy.contains(s.stationKey())){notice(s.player,"transaction-running","This workstation is busy.",Map.of());return;}
        if(s.location!=null&&(!station.test(s.location)||!s.player.getWorld().equals(s.location.getWorld())||s.player.getLocation().distanceSquared(s.location)>64)){message(s.player,"Return to the Forging Anvil.");return;}
        int levels=Math.max(0,cost(s));if(s.player.getLevel()<levels){notice(s.player,"not-enough-xp","You need {levels} XP levels.",Map.of("levels",levels));return;}
        if(s.tab==Tab.FORGING&&Extraction.eligible(s.inputs().get(FIRST),s.inputs().get(SECOND))){
            ItemStack base=s.inputs().get(FIRST).clone(),book=s.inputs().get(SECOND).clone();
            s.transferring=true;
            extraction.handoff(s.player,s.location==null?s.player.getLocation():s.location,base,book,s.location==null,()->{s.inputs().remove(FIRST);s.inputs().remove(SECOND);checkpoint(s);});return;
        }
        var consumed=new ArrayList<ItemStack>();ItemStack output;UUID scythe=null;String tier="";int seconds;
        if(s.tab==Tab.FORGING){Forge f=forge(s);if(f==null){message(s.player,"Insert compatible equipment, an enchanted book, repair material, or a Wardenite ingot to repair a scythe.");return;}output=f.result();consumed.add(s.inputs().get(FIRST).clone());ItemStack used=s.inputs().get(SECOND).clone();used.setAmount(f.donorUsed());consumed.add(used);seconds=0;}
        else if(s.tab==Tab.UPGRADING){var recipe=WorkshopRecipes.upgrade(items,s.inputs().get(INPUTS[1][0]),s.inputs().get(INPUTS[1][1]),s.inputs().get(INPUTS[1][2]));if(recipe==null){message(s.player,"Use the exact previous tier, matching upgrade template and ingot.");return;}output=recipe.item();tier=recipe.tier();for(int slot:INPUTS[1]){var one=s.inputs().get(slot).clone();one.setAmount(1);consumed.add(one);}seconds=Math.max(1,configs.apply("config").getInt("forging.animation-seconds",3));}
        else{ItemStack[] ring=Arrays.stream(INPUTS[2]).mapToObj(s.inputs()::get).toArray(ItemStack[]::new);if(!WorkshopRecipes.ritual(items,ring)){message(s.player,"All eight ritual ingredients must match their labelled slots.");return;}scythe=UUID.randomUUID();if(!store.reserveScythe(scythe,maximumScythes())){message(s.player,"The server's legendary scythe capacity is occupied.");return;}output=items.create("legendary_scythe");UUID uuid=scythe;output.editMeta(m->m.getPersistentDataContainer().set(new NamespacedKey("blastforge","scythe_uuid"),PersistentDataType.STRING,uuid.toString()));for(var item:ring){var one=item.clone();one.setAmount(1);consumed.add(one);}seconds=Math.max(1,configs.apply("scythe").getInt("ritual-duration",5));tier="wardenite";}
        UUID tx=UUID.randomUUID();String initial=payload("U",s.player.getUniqueId(),levels,scythe,consumed,List.of(output));store.put("transactions",tx.toString(),initial);
        int[] slots=INPUTS[s.tab.ordinal()];for(int i=0;i<slots.length;i++)decrease(s.inputs(),slots[i],consumed.get(i).getAmount());s.player.giveExpLevels(-levels);checkpoint(s);
        store.put("transactions",tx.toString(),payload("R",s.player.getUniqueId(),levels,scythe,consumed,List.of(output)));
        s.running=tx;busy.add(s.stationKey());Location effect=s.location==null?s.player.getLocation():s.location;final UUID reserved=scythe;final String material=tier;final int duration=seconds*20;
        if(s.location!=null&&duration>0)heat.accept(s.location,duration);
        final int[] tick={0};BukkitTask task=plugin.getServer().getScheduler().runTaskTimer(plugin,()->{
            Craft craft=crafts.get(tx);if(craft==null)return;
            if(s.closed||!s.player.isOnline()||s.location!=null&&!station.test(s.location)){abort(craft);return;}
            if(duration>0&&(tick[0]==0||tick[0]==duration/3||tick[0]==2*duration/3)){strike.accept(effect,material);effect.getWorld().playSound(effect,Sound.BLOCK_ANVIL_USE,0.7f,1);effect.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,effect.clone().add(.5,1,.5),12,.3,.3,.3,.03);}
            if(tick[0]++>=duration){
                store.commitCraft(reserved,tx.toString(),payload("C",s.player.getUniqueId(),levels,reserved,consumed,List.of(output)));
                crafts.remove(tx);busy.remove(s.stationKey());s.running=null;craft.task().cancel();
                store.remove("transactions",tx.toString());mailbox.deliver(s.player);if(s.location!=null)heat.accept(s.location,0);draw(s);notice(s.player,"transaction-complete","Forging complete. Your result is in your inventory or mailbox.",Map.of());
            }
        },1,1);
        crafts.put(tx,new Craft(tx,s.player.getUniqueId(),s.stationKey(),effect,reserved,consumed,List.of(output),levels,s,task));draw(s);
    }
    int maximumScythes(){String stored=store.get("settings","scythe_limit");return Math.max(0,stored==null?configs.apply("scythe").getInt("maximum-scythes",1):Integer.parseInt(stored));}
    private static String payload(String state,UUID owner,int levels,UUID scythe,List<ItemStack> inputs,List<ItemStack> output){return state+"\n"+owner+"\n"+levels+"\n"+(scythe==null?"-":scythe)+"\n"+ItemCodec.encode(inputs)+"\n"+ItemCodec.encode(output);}
    private void abort(Craft craft){
        craft.task().cancel();crafts.remove(craft.id());busy.remove(craft.key());craft.session().running=null;
        if(craft.session().location!=null)heat.accept(craft.session().location,-1);
        String authoritative=store.get("transactions",craft.id().toString());
        if(authoritative!=null&&authoritative.startsWith("C\n")){mailbox.queue(craft.owner(),craft.id(),craft.outputs(),0);store.remove("transactions",craft.id().toString());return;}
        if(craft.scythe()!=null)store.releaseReservation(craft.scythe());mailbox.queue(craft.owner(),craft.id(),craft.consumed(),craft.levels());store.remove("transactions",craft.id().toString());
    }
    private void recover(){
        for(var entry:store.entries("transactions").entrySet()){
            String[] p=entry.getValue().split("\n",6);if(p.length!=6||p[0].equals("U")){store.put("quarantine","craft:"+entry.getKey(),entry.getValue());store.remove("transactions",entry.getKey());continue;}
            UUID owner=UUID.fromString(p[1]),id=UUID.fromString(entry.getKey());boolean committed=p[0].equals("C");
            mailbox.queue(owner,id,ItemCodec.decode(p[committed?5:4]),committed?0:Integer.parseInt(p[2]));
            if(!committed&&!p[3].equals("-"))store.releaseReservation(UUID.fromString(p[3]));store.remove("transactions",entry.getKey());
        }
        mailbox.recoverSessions();
        // A RESERVED instance without even an uncertain transaction was never
        // issued: activation and committed output are written together.
        for(var entry:store.scythes().entrySet())if(entry.getValue().equals("RESERVED")){
            boolean referenced=store.entries("quarantine").values().stream().anyMatch(v->v.contains("\n"+entry.getKey()+"\n"));
            if(!referenced)store.releaseReservation(UUID.fromString(entry.getKey()));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void drag(InventoryDragEvent event){if(find(event.getView())!=null)event.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent event){Session s=find(event.getView());if(s==null)return;s.closed=true;sessions.remove(s.player.getUniqueId());if(s.running!=null){Craft craft=crafts.get(s.running);if(craft!=null)abort(craft);}List<ItemStack> returns=s.owned();s.player.setItemOnCursor(null);s.inventory.clear();if(s.uncertain)store.put("quarantine","session:"+s.player.getUniqueId()+":"+s.id,"U\n"+s.id+"\n"+ItemCodec.encode(returns));else mailbox.queue(s.player.getUniqueId(),s.id,returns,0);store.remove("workshop_session",s.player.getUniqueId().toString());}
    void shutdown(){for(var s:List.copyOf(sessions.values()))s.player.closeInventory();for(var c:List.copyOf(crafts.values()))abort(c);HandlerList.unregisterAll(this);}
    private void message(Player player,String text){notice(player,"details",text,Map.of());}
    private void notice(Player player,String key,String fallback,Map<String,?> values){ExpansionMessages.send(player,configs.apply("messages"),key,fallback,values);}
}
