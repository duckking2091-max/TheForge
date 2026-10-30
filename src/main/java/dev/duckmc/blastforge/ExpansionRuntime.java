package dev.duckmc.blastforge;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.duckmc.blastforge.expansion.*;
import io.papermc.paper.command.brigadier.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Owns expansion modules; the original plugin and saved identities remain intact. */
final class ExpansionRuntime implements AutoCloseable {
    private final BlastForge plugin;
    private Settings settings;private PersistentStore store;private ExpansionItems items;private Mailbox mailbox;
    private ExpansionProtection protection;private MaterialSystem materials;private ScytheLifecycle scythes;private WorkshopMenu workshop;
    private MagicSystem magic;
    /** Model-only parts of the anvil and the villager outfits: not real items, so not offered by /forge give. */
    static final java.util.Set<String> DISPLAY_ONLY=java.util.Set.of("anvil_hammer","anvil_tools","magician_hat","magician_robes","miner_backpack","miner_helmet","tab_forging","tab_upgrading","tab_ritual","tab_rename","tab_close","guide_button");
    private PackVisuals pack;private StationVisuals visuals;private GuideBook guide;
    private VillagerSystem villagers;
    private org.bukkit.scheduler.BukkitTask diagnostics;
    private boolean closing;
    ExpansionRuntime(BlastForge plugin){this.plugin=plugin;}
    void start(ExtractionMenu extraction,ForgeMenu nativeAnvil){
        settings=new Settings(plugin);settings.reload();
        store=new PersistentStore(plugin.getDataFolder().toPath().resolve("blastforge.db"));
        items=new ExpansionItems(plugin,settings::get);protection=new ExpansionProtection(plugin);mailbox=new Mailbox(plugin,store);mailbox.start();
        protection.passiveTargets(()->settings.get("magic").getBoolean("target-passive-mobs",false));
        extraction.persistentReturns(mailbox,store);
        extraction.experienceCost(()->settings.get("config").getInt("forging.extraction-levels",0));
        nativeAnvil.normalOperationsOnly();
        workshop=new WorkshopMenu(plugin,items,settings::get,store,mailbox,extraction,nativeAnvil::open,location->plugin.stations().isStation(location.getBlock()));workshop.start();
        plugin.stations().opener(workshop::open);
        materials=new MaterialSystem(plugin,items,settings::get,store);materials.start();
        scythes=new ScytheLifecycle(plugin,items,settings::get,store);scythes.start();
        magic=new MagicSystem(plugin,items,settings::get,store,protection);magic.start();
        startVisuals();
        startVillagers();
        guide=new GuideBook(plugin,settings::get);guide.start();guideReader=new GuideReader(plugin,guide,items);guideReader.start();guide.reader(guideReader,pack);
        diagnostics=plugin.getServer().getScheduler().runTaskTimer(plugin,()->{
            var watchers=plugin.getServer().getOnlinePlayers().stream().filter(p->"true".equals(store.get("debug",p.getUniqueId().toString()))).toList();
            boolean console="true".equals(store.get("debug","console"));if(watchers.isEmpty()&&!console)return;
            String text="The Forge: scythes "+store.occupiedScythes()+"/"+scythes.maximum()+", pending deliveries "+store.entries("mailbox").size()+", recovery records "+store.entries("quarantine").size();
            watchers.forEach(p->p.sendActionBar(Component.text(text)));if(console)plugin.getLogger().info(text);
        },100,100);
    }
    boolean isOre(org.bukkit.block.Block block){return materials!=null&&materials.isOre(block);}
    private void startVisuals(){pack=new PackVisuals(plugin,items,settings::get);pack.start();visuals=new StationVisuals(plugin,items,settings::get);visuals.pack(pack);visuals.start();workshop.pack(pack);workshop.strikeHandler(visuals::strike);workshop.heatHandler(visuals::heat);plugin.stations().visuals(visuals::placed,visuals::removed);if(guide!=null){guideReader=new GuideReader(plugin,guide,items);guideReader.start();guide.reader(guideReader,pack);}}
    private GuideReader guideReader;
    private void closeVisuals(){if(guideReader!=null){guideReader.close();guideReader=null;}if(visuals!=null)visuals.close();if(pack!=null)pack.close();}
    private void startVillagers(){villagers=new VillagerSystem(plugin,items,settings::get,store,protection);villagers.visuals(pack);villagers.start();}
    boolean canMine(org.bukkit.block.Block block,ItemStack tool){return materials==null||materials.canMine(block,tool);}
    void commands(LiteralArgumentBuilder<CommandSourceStack> root){
        root.then(Commands.literal("reload").requires(s->allowed(s,"reload")).executes(ctx->{
            try{settings.reload();plugin.reloadConfig();workshop.shutdown();villagers.close();closeVisuals();workshop.start();magic.close();magic=new MagicSystem(plugin,items,settings::get,store,protection);magic.start();materials.close();materials=new MaterialSystem(plugin,items,settings::get,store);materials.start();startVisuals();startVillagers();ExpansionMessages.send(ctx.getSource().getSender(),settings.get("messages"),"reload-success","The Forge settings reloaded.",Map.of());return 1;}
            catch(RuntimeException e){ExpansionMessages.send(ctx.getSource().getSender(),settings.get("messages"),"reload-failed","Reload failed: {reason}",Map.of("reason",String.valueOf(e.getMessage())));return 0;}
        }));
        // /forge guide [player]: The Forging Starter (everyone for themselves; giving others needs blastforge.give)
        var guideCmd=Commands.literal("guide").requires(s->allowed(s,"guide")).executes(ctx->{if(!(ctx.getSource().getSender() instanceof Player p)){ctx.getSource().getSender().sendMessage("Players only; use /forge guide <player>.");return 0;}guide.give(p);p.sendMessage(Component.text("You received The Forging Starter.",net.kyori.adventure.text.format.NamedTextColor.GOLD));return 1;});
        guideCmd.then(Commands.argument("player",StringArgumentType.word()).requires(s->allowed(s,"give")).suggests((ctx,builder)->{plugin.getServer().getOnlinePlayers().forEach(p->builder.suggest(p.getName()));return builder.buildFuture();}).executes(ctx->{Player target=plugin.getServer().getPlayerExact(StringArgumentType.getString(ctx,"player"));if(target==null){ctx.getSource().getSender().sendMessage("Player must be online.");return 0;}guide.give(target);target.sendMessage(Component.text("You received The Forging Starter.",net.kyori.adventure.text.format.NamedTextColor.GOLD));ctx.getSource().getSender().sendMessage("Gave The Forging Starter to "+target.getName()+".");return 1;}));
        root.then(guideCmd);
        var give=Commands.literal("give").requires(s->allowed(s,"give"));
        var target=Commands.argument("player",StringArgumentType.word()).suggests((ctx,builder)->{plugin.getServer().getOnlinePlayers().forEach(p->builder.suggest(p.getName()));return builder.buildFuture();});
        var item=Commands.argument("item",StringArgumentType.word()).suggests((ctx,builder)->{items.ids().stream().filter(id->!DISPLAY_ONLY.contains(id)).forEach(builder::suggest);return builder.buildFuture();});
        item.executes(ctx->give(ctx.getSource().getSender(),StringArgumentType.getString(ctx,"player"),StringArgumentType.getString(ctx,"item"),1));
        item.then(Commands.argument("amount",IntegerArgumentType.integer(1,64)).executes(ctx->give(ctx.getSource().getSender(),StringArgumentType.getString(ctx,"player"),StringArgumentType.getString(ctx,"item"),IntegerArgumentType.getInteger(ctx,"amount"))));
        target.then(item);give.then(target);root.then(give);
        var open=Commands.literal("open").requires(s->allowed(s,"open")).executes(ctx->open(ctx.getSource().getSender(),null));
        open.then(Commands.argument("player",StringArgumentType.word()).suggests((ctx,builder)->{plugin.getServer().getOnlinePlayers().forEach(p->builder.suggest(p.getName()));return builder.buildFuture();}).executes(ctx->open(ctx.getSource().getSender(),StringArgumentType.getString(ctx,"player"))));root.then(open);
        var scythe=Commands.literal("scythe").requires(s->allowed(s,"scythe.admin"));
        scythe.then(Commands.literal("status").executes(ctx->{var sender=ctx.getSource().getSender();
            sender.sendMessage("Legendary Scythes: "+store.occupiedScythes()+" / "+scythes.maximum()+" - Dark Ritual "+(scythes.available()?"AVAILABLE":"LOCKED"));
            for(var e:store.scythes().entrySet()){String where=store.get("scythe_location",e.getKey());sender.sendMessage(" "+e.getKey()+"  "+e.getValue()+(where!=null?"  (last: "+where+")":""));}return 1;}));
        // Recovery when a scythe is gone but its destruction could not be confirmed automatically (e.g. a server wipe, /clear, a deleted world).
        scythe.then(Commands.literal("destroy").then(Commands.argument("id",StringArgumentType.word()).suggests((ctx,b)->{store.scythes().forEach((k,v)->{if(!v.equals("DESTROYED"))b.suggest(k);});return b.buildFuture();}).executes(ctx->{
            var sender=ctx.getSource().getSender();UUID id;try{id=UUID.fromString(StringArgumentType.getString(ctx,"id"));}catch(IllegalArgumentException bad){sender.sendMessage("Not a scythe id. See /forge scythe status.");return 0;}
            if(!scythes.destroy(id,"marked destroyed by "+sender.getName())){sender.sendMessage("That scythe is not active.");return 0;}
            sender.sendMessage("Scythe "+id+" marked as destroyed. Its copies stop working; the Dark Ritual is "+(scythes.available()?"available again.":"still locked."));return 1;})));
        scythe.then(Commands.literal("restore").then(Commands.argument("id",StringArgumentType.word()).suggests((ctx,b)->{store.scythes().forEach((k,v)->{if(v.equals("DESTROYED"))b.suggest(k);});return b.buildFuture();}).executes(ctx->{
            var sender=ctx.getSource().getSender();UUID id;try{id=UUID.fromString(StringArgumentType.getString(ctx,"id"));}catch(IllegalArgumentException bad){sender.sendMessage("Not a scythe id.");return 0;}
            if(!scythes.restore(id)){sender.sendMessage("Cannot restore: it is not destroyed, or another scythe already fills the limit.");return 0;}
            sender.sendMessage("Scythe "+id+" is active again.");return 1;})));
        scythe.then(Commands.literal("limit").then(Commands.argument("number",IntegerArgumentType.integer(0,10000)).executes(ctx->{int limit=IntegerArgumentType.getInteger(ctx,"number");store.put("settings","scythe_limit",Integer.toString(limit));ctx.getSource().getSender().sendMessage("Scythe capacity is now "+limit+". Existing issued scythes remain registered.");return 1;})));root.then(scythe);
        var debug=Commands.literal("debug").requires(s->allowed(s,"debug")).executes(ctx->{var sender=ctx.getSource().getSender();String key=sender instanceof Player p?p.getUniqueId().toString():"console";boolean enabled=!"true".equals(store.get("debug",key));store.put("debug",key,Boolean.toString(enabled));sender.sendMessage("The Forge debug: "+enabled+"; pending deliveries "+store.entries("mailbox").size()+", quarantined boundaries "+store.entries("quarantine").size());return 1;});
        debug.then(Commands.literal("serialization").executes(ctx->{
            int count=0;for(String id:items.ids()){ItemStack original=items.create(id);ItemStack recovered=ItemCodec.decode(ItemCodec.encode(List.of(original))).getFirst();if(!original.equals(recovered))throw new IllegalStateException("Item serialization mismatch: "+id);count++;}
            ctx.getSource().getSender().sendMessage("Native item serialization passed: "+count+" catalog items.");return 1;
        }));root.then(debug);
        root.then(Commands.literal("miner").requires(s->allowed(s,"miner.spawn")).then(Commands.literal("spawn").executes(ctx->{
            if(!(ctx.getSource().getSender() instanceof Player player)){ctx.getSource().getSender().sendMessage("Use this command in-game at the desired area.");return 0;}
            boolean spawned=villagers.spawnMiner(player);player.sendMessage(spawned?"A Wandering Miner arrived nearby.":"No safe Miner spawn was available, or this area already has one.");return spawned?1:0;
        })));
    }
    private boolean allowed(CommandSourceStack source,String suffix){return source.getSender().hasPermission("blastforge.admin")||source.getSender().hasPermission("blastforge."+suffix);}
    private int give(CommandSender sender,String name,String id,int amount){
        Player player=plugin.getServer().getPlayerExact(name);if(player==null){sender.sendMessage("Player must be online: "+name);return 0;}
        if(!items.ids().contains(id)||DISPLAY_ONLY.contains(id)){sender.sendMessage("Unknown item: "+id);return 0;}
        var outputs=new ArrayList<ItemStack>();
        for(int i=0;i<amount;i++){
            ItemStack stack=id.equals("legendary_scythe")?scythes.issue():items.create(id);
            if(stack==null){sender.sendMessage("Scythe capacity is occupied; issued "+outputs.size()+".");break;}outputs.add(stack);
        }
        if(outputs.isEmpty())return 0;
        mailbox.queue(player.getUniqueId(),UUID.randomUUID(),outputs,0);sender.sendMessage("Queued "+outputs.size()+" "+id+" for "+player.getName()+".");return 1;
    }
    private int open(CommandSender sender,String name){Player player=name==null?(sender instanceof Player p?p:null):plugin.getServer().getPlayerExact(name);if(player==null){sender.sendMessage("Specify an online player.");return 0;}workshop.open(player,null);return 1;}
    @Override public void close(){if(guide!=null)guide.close();
        if(closing)return;closing=true;
        if(diagnostics!=null)diagnostics.cancel();
        if(workshop!=null)workshop.shutdown();if(villagers!=null)villagers.close();closeVisuals();if(magic!=null)magic.close();if(materials!=null)materials.close();if(scythes!=null)scythes.close();if(mailbox!=null)HandlerList.unregisterAll(mailbox);if(store!=null)store.close();
    }
}
