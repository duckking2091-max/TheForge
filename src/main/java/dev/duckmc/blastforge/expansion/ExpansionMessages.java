package dev.duckmc.blastforge.expansion;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;

/** Configurable markup with literal, non-markup substitutions for dynamic values. */
public final class ExpansionMessages {
    private ExpansionMessages(){}
    public static Component text(FileConfiguration config,String key,String fallback,Map<String,?> values){
        var parser=MiniMessage.miniMessage();String configured=config.getString(key);
        Component result=configured==null?Component.text(fallback):parser.deserialize(configured);
        for(var entry:values.entrySet())result=result.replaceText(builder->builder.matchLiteral("{"+entry.getKey()+"}").replacement(Component.text(String.valueOf(entry.getValue()))));
        return parser.deserialize(config.getString("prefix","")).append(result);
    }
    public static void send(CommandSender target,FileConfiguration config,String key,String fallback,Map<String,?> values){target.sendMessage(text(config,key,fallback,values));}
}
