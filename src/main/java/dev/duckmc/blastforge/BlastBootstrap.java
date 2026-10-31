package dev.duckmc.blastforge;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.event.WritableRegistry;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Registers the Blast enchantment on every Paper 1.21.x.
 * <ul>
 *   <li>The enchantment registry hook is {@code compose()} on new servers and {@code freeze()} on old ones; both events
 *       offer the same {@code registry()} and {@code getOrCreateTag(...)}, so whichever exists is used.</li>
 *   <li>The tag hooks ({@code LifecycleEvents.TAGS}) are missing on the oldest 1.21 builds. There Blast applies to every
 *       mining tool (Blast mining itself only works with pickaxes and shovels) and comes from librarian books instead
 *       of enchanting tables.</li>
 * </ul>
 */
public final class BlastBootstrap implements PluginBootstrap {
    public static final TypedKey<Enchantment> BLAST = TypedKey.create(RegistryKey.ENCHANTMENT,Key.key("blastforge:blast"));
    private static final TagKey<ItemType> BLAST_TOOLS=TagKey.create(RegistryKey.ITEM,Key.key("blastforge:blast_tools"));

    @SuppressWarnings({"unchecked","rawtypes"})
    @Override public void bootstrap(BootstrapContext context) {
        LifecycleEventManager<BootstrapContext> lifecycle=context.getLifecycleManager();
        Object tags=tagEvents();
        boolean customTag=tags!=null && registerTags(lifecycle,tags);

        // compose() (new servers) or freeze() (old servers): the same event under two names
        LifecycleEventType type=null;
        for(String name:new String[]{"compose","freeze"})try{
            type=(LifecycleEventType)publicMethod(RegistryEvents.ENCHANTMENT.getClass(),name).invoke(RegistryEvents.ENCHANTMENT);break;
        }catch(ReflectiveOperationException ignored){}
        if(type==null){context.getLogger().error("This Paper version cannot register custom enchantments; Blast is disabled.");return;}
        TagKey<ItemType> tools=customTag?BLAST_TOOLS:ItemTypeTagKeys.ENCHANTABLE_MINING;
        lifecycle.registerEventHandler(type,(LifecycleEventHandler)event->{
            try{
                var registry=(WritableRegistry<Enchantment,EnchantmentRegistryEntry.Builder>)publicMethod(event.getClass(),"registry").invoke(event);
                Tag<ItemType> items=(Tag<ItemType>)publicMethod(event.getClass(),"getOrCreateTag",TagKey.class).invoke(event,tools);
                registry.register(BLAST,b->b.description(Component.text("Blast"))
                    .supportedItems(items).primaryItems(items)
                    .anvilCost(4).maxLevel(3).weight(1)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(10,12))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(60,10))
                    .activeSlots(EquipmentSlotGroup.MAINHAND));
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Blast registration failed",e);}
        });
    }
    /** LifecycleEvents.TAGS, or null where this Paper build has no tag events. */
    private static Object tagEvents(){
        try{return Class.forName("io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents").getField("TAGS").get(null);}
        catch(ReflectiveOperationException|LinkageError e){return null;}
    }
    /** Pickaxes + shovels as blastforge:blast_tools, and Blast in enchanting tables. Returns false if unavailable. */
    @SuppressWarnings({"unchecked","rawtypes"})
    private static boolean registerTags(LifecycleEventManager<BootstrapContext> lifecycle,Object tags){
        try{
            Method postFlatten=publicMethod(tags.getClass(),"postFlatten",RegistryKey.class);
            LifecycleEventType items=(LifecycleEventType)postFlatten.invoke(tags,RegistryKey.ITEM);
            LifecycleEventType enchantments=(LifecycleEventType)postFlatten.invoke(tags,RegistryKey.ENCHANTMENT);
            lifecycle.registerEventHandler(items,(LifecycleEventHandler)event->{
                try{Object registrar=publicMethod(event.getClass(),"registrar").invoke(event);Method get=publicMethod(registrar.getClass(),"getTag",TagKey.class);
                    var tools=new HashSet<Object>((Collection<Object>)get.invoke(registrar,ItemTypeTagKeys.PICKAXES));tools.addAll((Collection<Object>)get.invoke(registrar,ItemTypeTagKeys.SHOVELS));
                    publicMethod(registrar.getClass(),"setTag",TagKey.class,Collection.class).invoke(registrar,BLAST_TOOLS,tools);
                }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
            lifecycle.registerEventHandler(enchantments,(LifecycleEventHandler)event->{
                try{Object registrar=publicMethod(event.getClass(),"registrar").invoke(event);Method add=publicMethod(registrar.getClass(),"addToTag",TagKey.class,Collection.class);
                    add.invoke(registrar,EnchantmentTagKeys.IN_ENCHANTING_TABLE,Set.of(BLAST));
                    // Librarian Blast offers are generated by ForgeItems with weighted levels; vanilla's uniform choice must not bypass them.
                    add.invoke(registrar,EnchantmentTagKeys.NON_TREASURE,Set.of(BLAST));
                }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
            return true;
        }catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    /** A public method found through the public interfaces of a (non-public) implementation class. */
    private static Method publicMethod(Class<?> type,String name,Class<?>... parameters) throws NoSuchMethodException{
        for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Class<?> i:allInterfaces(c))try{return i.getMethod(name,parameters);}catch(NoSuchMethodException ignored){}
        Method m=type.getMethod(name,parameters);m.setAccessible(true);return m;
    }
    private static Set<Class<?>> allInterfaces(Class<?> c){var out=new LinkedHashSet<Class<?>>();var stack=new ArrayDeque<Class<?>>(List.of(c.getInterfaces()));while(!stack.isEmpty()){Class<?> i=stack.pop();if(out.add(i))stack.addAll(List.of(i.getInterfaces()));}return out;}
}
