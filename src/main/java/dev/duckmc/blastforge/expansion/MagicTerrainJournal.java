package dev.duckmc.blastforge.expansion;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable reservations separate identity from block-data equality. No Bukkit objects are retained. */
final class MagicTerrainJournal {
    record Position(UUID world,int x,int y,int z) {
        String key(){return world+":"+x+":"+y+":"+z;}
        static Position parse(String key){var p=key.split(":");return new Position(UUID.fromString(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));}
    }
    record Entry(UUID operation,UUID owner,String original,String expected,long expiry,String phase) {
        Entry phase(String value){return new Entry(operation,owner,original,expected,expiry,value);}
        String encode(){return operation+"|"+owner+"|"+b64(original)+"|"+b64(expected)+"|"+expiry+"|"+phase;}
        static Entry decode(String value){var p=value.split("\\|",-1);return new Entry(UUID.fromString(p[0]),UUID.fromString(p[1]),un64(p[2]),un64(p[3]),Long.parseLong(p[4]),p[5]);}
        private static String b64(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
        private static String un64(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    }
    private final PersistentStore store;
    private final Map<Position,Entry> records=new LinkedHashMap<>();
    MagicTerrainJournal(PersistentStore store){
        this.store=store;
        for(var e:store.entries("magic.terrain").entrySet())try{records.put(Position.parse(e.getKey()),Entry.decode(e.getValue()));}
        catch(RuntimeException invalid){store.put("magic.terrain-quarantine",e.getKey(),e.getValue());store.remove("magic.terrain",e.getKey());}
    }
    Map<Position,Entry> records(){return Collections.unmodifiableMap(records);}
    void batch(Runnable writes){Map<Position,Entry> before=new LinkedHashMap<>(records);try{store.batch(writes);}catch(RuntimeException failure){records.clear();records.putAll(before);throw failure;}}
    boolean reserve(Position pos,UUID operation,UUID owner,String original,String expected,long expiry){
        if(records.containsKey(pos))return false;
        write(pos,new Entry(operation,owner,original,expected,expiry,"PREPARED"));return true;
    }
    private void write(Position pos,Entry entry){store.put("magic.terrain",pos.key(),entry.encode());records.put(pos,entry);}
    void applied(Position pos,UUID operation){Entry e=records.get(pos);if(e!=null && e.operation.equals(operation))write(pos,e.phase("APPLIED"));}
    void invalidate(Position pos){Entry e=records.get(pos);if(e!=null)write(pos,e.phase("INVALIDATED"));}
    void quarantine(Position pos){Entry e=records.get(pos);if(e!=null){store.put("magic.terrain-quarantine",pos.key(),e.encode());forget(pos);}}
    boolean mayRestore(Position pos,UUID operation,String current){Entry e=records.get(pos);return e!=null && e.phase.equals("APPLIED") && e.operation.equals(operation) && same(e.expected,current);}
    /** Fire ages while it burns; its block states change but it is still the spell's fire. */
    static boolean same(String expected,String current){if(expected.equals(current))return true;String a=expected.split("\\[")[0],b=current.split("\\[")[0];return a.equals("minecraft:fire") && a.equals(b);}
    int count(UUID owner){return (int)records.values().stream().filter(e->e.owner.equals(owner) && !e.phase.equals("INVALIDATED")).count();}
    void forget(Position pos){store.remove("magic.terrain",pos.key());records.remove(pos);}
}
