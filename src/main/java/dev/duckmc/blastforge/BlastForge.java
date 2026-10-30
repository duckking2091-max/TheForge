package dev.duckmc.blastforge;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlastForge extends JavaPlugin {
    private ForgeStations stations;
    private ForgeMenu menu;
    private ExtractionMenu extraction;
    private ExpansionRuntime expansion;
    ForgeStations stations() { return stations; }
    boolean customOre(org.bukkit.block.Block block){return expansion!=null&&expansion.isOre(block);}
    boolean customHarvest(org.bukkit.block.Block block,ItemStack tool){return expansion==null||expansion.canMine(block,tool);}
    /** Renamed from BlastForge to The Forge: keep the old settings and database by moving plugins/BlastForge once. */
    @Override public void onLoad() {
        java.io.File old=new java.io.File(getDataFolder().getParentFile(),"BlastForge");
        java.io.File now=getDataFolder();String[] inside=now.list();
        if(old.isDirectory() && (!now.exists() || now.isDirectory() && inside!=null && inside.length==0)){
            if(now.exists() && !now.delete())throw new IllegalStateException("Could not replace the empty plugins/"+now.getName()+" folder.");
            try{java.nio.file.Files.move(old.toPath(),getDataFolder().toPath());getLogger().info("Moved plugins/BlastForge to plugins/"+getDataFolder().getName()+" (all settings and data kept).");}
            catch(java.io.IOException e){throw new IllegalStateException("Could not move plugins/BlastForge to plugins/"+getDataFolder().getName()+"; move it by hand while the server is stopped.",e);}
        }
    }
    @Override public void onEnable() {
        saveDefaultConfig();
        if(Rarity.migrate(getConfig()))saveConfig();
        var blast=RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).getOrThrow(BlastBootstrap.BLAST);
        ForgeItems items=new ForgeItems(this,blast);
        extraction=new ExtractionMenu(this,block -> stations.isStation(block));
        menu=new ForgeMenu(this,extraction);
        stations=new ForgeStations(this,items,menu);
        var manager=getServer().getPluginManager();
        manager.registerEvents(items,this);
        manager.registerEvents(menu,this);
        manager.registerEvents(extraction,this);
        manager.registerEvents(stations,this);
        manager.registerEvents(new BlastMining(this,blast),this);
        items.registerRecipe();
        expansion=new ExpansionRuntime(this);expansion.start(extraction,menu);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,event -> {
            var root=Commands.literal("theforge");
            root.then(Commands.literal("book").requires(s->s.getSender().hasPermission("blastforge.admin")).executes(ctx -> give(ctx.getSource().getSender(),items.book())));
            root.then(Commands.literal("anvil").requires(s->s.getSender().hasPermission("blastforge.admin")).executes(ctx -> give(ctx.getSource().getSender(),items.anvil())));
            for(int i=1;i<=3;i++) {
                final int level=i;
                root.then(Commands.literal("blast"+i).requires(s->s.getSender().hasPermission("blastforge.admin")).executes(ctx -> give(ctx.getSource().getSender(),items.blastBook(level))));
            }
            expansion.commands(root);
            event.registrar().register(root.build(),"The Forge: guide, anvil, items and administration",java.util.List.of("forge","blastforge"));
        });
        getLogger().info("The Forge enabled for Paper 1.21.11.");
    }
    private int give(org.bukkit.command.CommandSender sender,ItemStack item) {
        if(!(sender instanceof Player player)) { sender.sendMessage("Use this command in-game."); return 0; }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(),left));
        return 1;
    }
    @Override public void onDisable() {
        if(menu!=null) menu.shutdown();
        if(extraction!=null) extraction.shutdown();
        if(expansion!=null)expansion.close();
    }
}
