package dev.duckmc.blastforge.expansion;

import org.bukkit.Location;
import org.bukkit.HeightMap;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.*;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.util.UUID;
import java.util.function.Consumer;

/** Supply only the two missing native presentation/despawn setters in MockBukkit.
 * Real world/entity registration, inventory/recipe, PDC and service behavior remain in use. */
final class VisualTestWorld extends WorldMock {
    private final ServerMock server;
    boolean stopAtSurface;
    static final class SurfaceProbe extends RuntimeException {}
    @Override public int getHighestBlockYAt(int x,int z,HeightMap map){if(stopAtSurface)throw new SurfaceProbe();return super.getHighestBlockYAt(x,z,map);}
    VisualTestWorld(ServerMock server,String name){this.server=server;setName(name);server.addWorld(this);}
    @Override public <T extends Entity> T spawn(Location location,Class<T> type,Consumer<? super T> consumer,SpawnReason reason,boolean randomize,boolean callEvent){
        EntityMock entity;
        if(type==ItemDisplay.class)entity=new ItemDisplayMock(server,UUID.randomUUID()){
            private boolean visible=true;private Billboard billboard=Billboard.FIXED;
            @Override public void setVisibleByDefault(boolean value){visible=value;}
            @Override public boolean isVisibleByDefault(){return visible;}
            @Override public void setBillboard(Billboard value){billboard=value;}
            @Override public Billboard getBillboard(){return billboard;}
        };
        else if(type==Villager.class)entity=new VillagerMock(server,UUID.randomUUID()){
            private boolean remove;
            private final java.util.Map<Integer,MerchantRecipe> nativeRecipes=new java.util.HashMap<>();
            @Override public void setRemoveWhenFarAway(boolean value){remove=value;}
            @Override public boolean getRemoveWhenFarAway(){return remove;}
            // CraftMerchantRecipe exposes its live fields through getters. MockBukkit's
            // normal get/setRecipe clone via base fields, so retain only subclass fixtures.
            @Override public void setRecipe(int index,MerchantRecipe recipe){super.setRecipe(index,recipe);if(recipe.getClass()!=MerchantRecipe.class)nativeRecipes.put(index,recipe);else nativeRecipes.remove(index);}
            @Override public MerchantRecipe getRecipe(int index){return nativeRecipes.containsKey(index)?nativeRecipes.get(index):super.getRecipe(index);}
            @Override public void setRecipes(java.util.List<MerchantRecipe> recipes){nativeRecipes.clear();super.setRecipes(recipes);}
        };
        else return super.spawn(location,type,consumer,reason,randomize,callEvent);
        entity.setLocation(location);entity.setSpawnReason(reason);T cast=type.cast(entity);if(consumer!=null)consumer.accept(cast);server.registerEntity(entity);return cast;
    }
}
