package dev.duckmc.blastforge.expansion;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validates all files before replacing the active configuration snapshot. */
public final class Settings {
    public static final List<String> FILES=List.of("config","materials","magic","villagers","scythe","messages");
    private final JavaPlugin plugin;
    private Map<String,FileConfiguration> active=Map.of();
    public Settings(JavaPlugin plugin){this.plugin=plugin;}
    public synchronized FileConfiguration get(String name){var config=active.get(name);if(config==null)throw new IllegalArgumentException("Unknown settings file "+name);return config;}
    public synchronized void reload() {
        var next=new HashMap<String,FileConfiguration>();
        for(String name:FILES){
            var file=new File(plugin.getDataFolder(),name+".yml");
            if(!file.exists())plugin.saveResource(name+".yml",false);
            var config=new YamlConfiguration();
            try{config.load(file);}catch(Exception e){throw new IllegalArgumentException("Invalid "+file.getName()+": "+e.getMessage(),e);}
            try(var stream=plugin.getResource(name+".yml")){
                if(stream==null)throw new IllegalStateException("Missing bundled "+name+".yml");
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8)));
            }catch(IOException e){throw new IllegalStateException(e);}
            validate(name,config);next.put(name,config);
        }
        active=Map.copyOf(next);
    }
    private void validate(String name,FileConfiguration config){
        var defaults=config.getDefaults();
        if(defaults!=null)for(String key:defaults.getKeys(true)){
            Object expected=defaults.get(key),actual=config.get(key);
            if(expected instanceof Number&&!(actual instanceof Number)||expected instanceof Boolean&&!(actual instanceof Boolean)||expected instanceof String&&!(actual instanceof String)||expected instanceof List&&!(actual instanceof List))
                throw new IllegalArgumentException(name+"."+key+" has an invalid value type");
        }
        for(String key:config.getKeys(true)){
            Object value=config.get(key);
            if(value instanceof Number n && (!Double.isFinite(n.doubleValue()) || n.doubleValue()<0 && !key.endsWith("min-y") && !key.endsWith("max-y")))
                throw new IllegalArgumentException(name+"."+key+" must be finite and nonnegative");
        }
        if(name.equals("materials"))for(String tier:List.of("siftie","enderite")){
            if(config.getInt("generation."+tier+".min-y")>config.getInt("generation."+tier+".max-y"))throw new IllegalArgumentException("Invalid "+tier+" ore height range");
        }
        if(name.equals("config")){
            String hash=config.getString("resource-pack.sha1","");
            if(!hash.isBlank()&&!hash.matches("[a-fA-F0-9]{40}"))throw new IllegalArgumentException("resource-pack.sha1 must contain 40 hexadecimal characters");
        }
    }
}
