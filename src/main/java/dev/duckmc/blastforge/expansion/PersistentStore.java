package dev.duckmc.blastforge.expansion;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Small synchronized SQLite state boundary. Server gameplay calls are synchronous. */
public final class PersistentStore implements AutoCloseable {
    private final Connection connection;
    public PersistentStore(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Class.forName("org.sqlite.JDBC");
            connection=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
            try(var s=connection.createStatement()){
                s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA busy_timeout=5000");
                s.execute("CREATE TABLE IF NOT EXISTS state(scope TEXT NOT NULL,k TEXT NOT NULL,v TEXT NOT NULL,PRIMARY KEY(scope,k))");
                s.execute("CREATE TABLE IF NOT EXISTS scythes(id TEXT PRIMARY KEY,status TEXT NOT NULL CHECK(status IN ('RESERVED','ACTIVE','DESTROYED')))");
                s.execute("PRAGMA user_version=1");
            }
        } catch(Exception ex){throw new IllegalStateException("Cannot open The Forge state database",ex);}
    }
    public synchronized void put(String scope,String key,String value){
        try(var p=connection.prepareStatement("INSERT INTO state(scope,k,v) VALUES(?,?,?) ON CONFLICT(scope,k) DO UPDATE SET v=excluded.v")){
            p.setString(1,scope);p.setString(2,key);p.setString(3,value);p.executeUpdate();
        }catch(SQLException e){throw fail(e);}
    }
    public synchronized String get(String scope,String key){
        try(var p=connection.prepareStatement("SELECT v FROM state WHERE scope=? AND k=?")){
            p.setString(1,scope);p.setString(2,key);try(var r=p.executeQuery()){return r.next()?r.getString(1):null;}
        }catch(SQLException e){throw fail(e);}
    }
    /** Group state writes; callers must complete this before applying world edits. */
    public synchronized void batch(Runnable writes){
        boolean owner=false;
        try{
            owner=connection.getAutoCommit();if(owner)connection.setAutoCommit(false);
            writes.run();if(owner)connection.commit();
        }catch(SQLException|RuntimeException error){
            if(owner)try{connection.rollback();}catch(SQLException suppressed){error.addSuppressed(suppressed);}
            throw new IllegalStateException("State batch failed",error);
        }finally{if(owner)try{connection.setAutoCommit(true);}catch(SQLException error){throw fail(error);}}
    }
    public synchronized void remove(String scope,String key){
        try(var p=connection.prepareStatement("DELETE FROM state WHERE scope=? AND k=?")){p.setString(1,scope);p.setString(2,key);p.executeUpdate();}catch(SQLException e){throw fail(e);}
    }
    public synchronized Map<String,String> entries(String scope){
        var entries=new LinkedHashMap<String,String>();
        try(var p=connection.prepareStatement("SELECT k,v FROM state WHERE scope=? ORDER BY k")){
            p.setString(1,scope);try(var r=p.executeQuery()){while(r.next())entries.put(r.getString(1),r.getString(2));}
        }catch(SQLException e){throw fail(e);}return Collections.unmodifiableMap(entries);
    }
    public synchronized long cooldown(UUID player,String type){String value=get("cooldowns",player+":"+type);return value==null?0:Long.parseLong(value);}
    public synchronized void setCooldown(UUID player,String type,long expiryMillis){put("cooldowns",player+":"+type,Long.toString(expiryMillis));}
    public synchronized int occupiedScythes(){
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT count(*) FROM scythes WHERE status!='DESTROYED'")){return r.getInt(1);}catch(SQLException e){throw fail(e);}
    }
    public synchronized boolean reserveScythe(UUID id,int limit){
        if(limit<=0)return false;
        try {
            connection.setAutoCommit(false);
            if(occupiedScythes()>=limit || scythes().containsKey(id.toString())){connection.rollback();return false;}
            try(var p=connection.prepareStatement("INSERT INTO scythes(id,status) VALUES(?,'RESERVED')")){p.setString(1,id.toString());p.executeUpdate();}
            connection.commit();return true;
        }catch(SQLException e){try{connection.rollback();}catch(SQLException suppressed){e.addSuppressed(suppressed);}throw fail(e);}
        finally{try{connection.setAutoCommit(true);}catch(SQLException e){throw fail(e);}}
    }
    public synchronized void activateScythe(UUID id){
        try(var p=connection.prepareStatement("UPDATE scythes SET status='ACTIVE' WHERE id=? AND status='RESERVED'")){
            p.setString(1,id.toString());if(p.executeUpdate()!=1)throw new IllegalStateException("Missing scythe reservation: "+id);
        }catch(SQLException e){throw fail(e);}
    }
    public synchronized void commitCraft(UUID scythe,String transaction,String payload){
        String[] fields=payload.split("\n",6);
        if(fields.length!=6||!fields[0].equals("C"))throw new IllegalArgumentException("Invalid committed craft payload");
        String existing=get("transactions",transaction);
        if(existing!=null&&existing.startsWith("C\n"))return;
        try{
            connection.setAutoCommit(false);
            if(scythe!=null)activateScythe(scythe);
            put("transactions",transaction,payload);
            String delivery=fields[1]+":"+transaction;
            if(get("mailbox",delivery)==null&&get("mailbox_done",delivery)==null&&get("quarantine",delivery)==null)put("mailbox",delivery,"P\n0\n"+fields[5]);
            connection.commit();
        }catch(SQLException|RuntimeException e){try{connection.rollback();}catch(SQLException suppressed){e.addSuppressed(suppressed);}throw new IllegalStateException("Craft commit failed",e);}
        finally{try{connection.setAutoCommit(true);}catch(SQLException e){throw fail(e);}}
    }
    public synchronized void releaseReservation(UUID id){
        try(var p=connection.prepareStatement("DELETE FROM scythes WHERE id=? AND status='RESERVED'")){p.setString(1,id.toString());p.executeUpdate();}catch(SQLException e){throw fail(e);}
    }
    public synchronized boolean destroyScythe(UUID id){
        try(var p=connection.prepareStatement("UPDATE scythes SET status='DESTROYED' WHERE id=? AND status='ACTIVE'")){p.setString(1,id.toString());return p.executeUpdate()==1;}catch(SQLException e){throw fail(e);}
    }
    /** Admin recovery: DESTROYED -> ACTIVE, atomically and only while the limit has room. */
    public synchronized boolean reactivateScythe(UUID id,int limit){
        try(var p=connection.prepareStatement("UPDATE scythes SET status='ACTIVE' WHERE id=? AND status='DESTROYED' AND (SELECT count(*) FROM scythes WHERE status!='DESTROYED')<?")){
            p.setString(1,id.toString());p.setInt(2,limit);return p.executeUpdate()==1;}catch(SQLException e){throw fail(e);}
    }
    public synchronized Map<String,String> scythes(){
        var result=new LinkedHashMap<String,String>();
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT id,status FROM scythes ORDER BY id")){while(r.next())result.put(r.getString(1),r.getString(2));}catch(SQLException e){throw fail(e);}return Collections.unmodifiableMap(result);
    }
    private IllegalStateException fail(SQLException e){return new IllegalStateException("The Forge persistent state operation failed",e);}
    @Override public synchronized void close(){try{connection.close();}catch(SQLException e){throw fail(e);}}
}
