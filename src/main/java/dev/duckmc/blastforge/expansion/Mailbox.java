package dev.duckmc.blastforge.expansion;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Durable returns. Ambiguous crash windows are quarantined instead of retried blindly. */
public final class Mailbox implements Listener {
    private final JavaPlugin plugin;
    private final PersistentStore store;
    private final NamespacedKey stamp;
    private final java.util.function.Consumer<Player> save;
    public Mailbox(JavaPlugin plugin,PersistentStore store){this(plugin,store,PlayerSaveCheckpoint::save);}
    public Mailbox(JavaPlugin plugin,PersistentStore store,java.util.function.Consumer<Player> save){this.plugin=plugin;this.store=store;this.save=save;stamp=new NamespacedKey("blastforge","delivery_batch");}
    public void savePlayer(Player player){save.accept(player);}
    public void start(){plugin.getServer().getPluginManager().registerEvents(this,plugin);dev.duckmc.blastforge.Tasks.globalTimer(plugin,100,100,()->{for(Player p:plugin.getServer().getOnlinePlayers())dev.duckmc.blastforge.Tasks.now(plugin,p,()->deliver(p));});}
    public void queue(UUID player,UUID transaction,List<ItemStack> items,int levels){
        String key=player+":"+transaction;
        if(store.get("mailbox",key)!=null||store.get("mailbox_done",key)!=null||store.get("quarantine",key)!=null)return;
        store.put("mailbox",key,"P\n"+levels+"\n"+ItemCodec.encode(items));
        Player online=plugin.getServer().getPlayer(player);if(online!=null)deliver(online);
    }
    @EventHandler public void join(PlayerJoinEvent event){dev.duckmc.blastforge.Tasks.entity(plugin,event.getPlayer(),()->deliver(event.getPlayer()));}
    public void deliver(Player player){
        for(var entry:store.entries("mailbox").entrySet()){
            if(!entry.getKey().startsWith(player.getUniqueId()+":"))continue;
            if(store.get("mailbox_done",entry.getKey())!=null||store.get("quarantine",entry.getKey())!=null){store.remove("mailbox",entry.getKey());continue;}
            String[] parts=entry.getValue().split("\n",3);if(parts.length!=3){quarantine(entry.getKey(),entry.getValue());continue;}
            String tx=entry.getKey().substring(entry.getKey().indexOf(':')+1);
            NamespacedKey receipt=new NamespacedKey("blastforge","receipt_"+tx);
            if(player.getPersistentDataContainer().has(receipt,PersistentDataType.BYTE)){
                try{savePlayer(player);finish(player,entry.getKey(),receipt,tx);}catch(RuntimeException error){plugin.getLogger().severe("Delivery acknowledgement retained: "+entry.getKey()+": "+error);return;}continue;
            }
            if(!parts[0].equals("P")){quarantine(entry.getKey(),entry.getValue());player.sendMessage(Component.text("The Forge is holding an interrupted delivery for admin review."));continue;}
            List<ItemStack> items;
            try{items=ItemCodec.decode(parts[2]);}catch(RuntimeException error){quarantine(entry.getKey(),entry.getValue());continue;}
            var capacity=Bukkit.createInventory(null,36);
            capacity.setContents(player.getInventory().getStorageContents());
            boolean fits=true;
            for(var item:items){ItemStack probe=item.clone();probe.editMeta(m->m.getPersistentDataContainer().set(stamp,PersistentDataType.STRING,tx));if(!capacity.addItem(probe).isEmpty()){fits=false;break;}}
            if(!fits){player.sendActionBar(Component.text("Make room for your mailbox items from The Forge."));continue;}
            store.put("mailbox",entry.getKey(),"D\n"+parts[1]+"\n"+parts[2]);
            try{
                for(ItemStack item:items){ItemStack delivered=item.clone();delivered.editMeta(m->m.getPersistentDataContainer().set(stamp,PersistentDataType.STRING,tx));player.getInventory().addItem(delivered);}
                player.giveExpLevels(Integer.parseInt(parts[1]));
                player.getPersistentDataContainer().set(receipt,PersistentDataType.BYTE,(byte)1);
                savePlayer(player);
                finish(player,entry.getKey(),receipt,tx);
            }catch(RuntimeException e){plugin.getLogger().severe("Delivery interrupted; retained record "+entry.getKey()+": "+e);return;}
        }
    }
    private void finish(Player player,String record,NamespacedKey receipt,String tx){
        store.put("mailbox_done",record,"delivered");
        store.remove("mailbox",record);
        for(ItemStack item:player.getInventory().getContents())if(item!=null && item.hasItemMeta()
            && tx.equals(item.getItemMeta().getPersistentDataContainer().get(stamp,PersistentDataType.STRING)))item.editMeta(m->m.getPersistentDataContainer().remove(stamp));
        player.getPersistentDataContainer().remove(receipt);savePlayer(player);
    }
    private void quarantine(String key,String value){store.put("quarantine",key,value);store.remove("mailbox",key);plugin.getLogger().severe("Ambiguous delivery quarantined (no automatic reissue): "+key);}
    public void recoverSessions(){
        for(var entry:store.entries("workshop_session").entrySet()){
            // Recovery records are confirmed after a player inventory save. Unconfirmed
            // boundaries are quarantined to prevent duplicating a player's saved inventory.
            String[] fields=entry.getValue().split("\n",3);
            if(fields.length==3 && fields[0].equals("S"))queue(UUID.fromString(entry.getKey()),UUID.fromString(fields[1]),ItemCodec.decode(fields[2]),0);
            else quarantine("session:"+entry.getKey()+":"+(fields.length>1?fields[1]:UUID.randomUUID()),entry.getValue());
            store.remove("workshop_session",entry.getKey());
        }
    }
}
