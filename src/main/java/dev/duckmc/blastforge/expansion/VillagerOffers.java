package dev.duckmc.blastforge.expansion;

import org.bukkit.*;
import org.bukkit.configuration.file.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.util.*;

/** Stable, persistable offer identities; native merchants remain responsible for consumption. */
final class VillagerOffers {
    record Offer(String id,int level,MerchantRecipe recipe){}
    private final ExpansionItems items;private final FileConfiguration config;
    VillagerOffers(ExpansionItems items,FileConfiguration config){this.items=items;this.config=config;}
    static final List<String> BOOKS=List.of("fire_book","water_book","earth_book","wind_book");
    static final List<String> UTILITIES=List.of("experience_bottle","lapis_lazuli","enchanted_book","ender_pearl","echo_shard","amethyst_shard");
    private static final Map<String,int[]> DEFAULTS=Map.of(   // fixed level, emeralds, amount, max-uses, random min-level
        "fire_book",new int[]{1,24,1,3,1},"water_book",new int[]{2,28,1,3,1},"earth_book",new int[]{3,32,1,3,1},"wind_book",new int[]{4,40,1,3,1},
        "experience_bottle",new int[]{1,3,1,12,1},"lapis_lazuli",new int[]{1,2,8,16,1},"enchanted_book",new int[]{2,20,1,6,1},
        "ender_pearl",new int[]{3,5,2,12,1},"echo_shard",new int[]{5,32,1,3,5},"amethyst_shard",new int[]{2,3,4,12,1});
    /** Fixed (non-random) Magician offers up to this level, as in older versions. */
    List<Offer> magician(int level){
        var out=new ArrayList<Offer>();
        for(String id:concat()){String path="magician.trades."+id+".";if(!config.getBoolean(path+"enabled",true))continue;
            int unlocked=Math.clamp(config.getInt(path+"level",DEFAULTS.get(id)[0]),1,5);if(unlocked<=level)out.add(offer(id,unlocked));}
        return out;
    }
    private static List<String> concat(){var all=new ArrayList<>(BOOKS);all.addAll(UTILITIES);return all;}
    /**
     * This Magician's offers up to {@code level}. With magician.random-trades.enabled, every Magician gets its own
     * plan from its saved seed: per level, books-per-level[n] elemental books plus trades to fill
     * trades-per-level[n] (default 2 per level, 1 book at levels 1-4, so all four books by level 4; never the
     * Darkness Book). Items only appear from their min-level (Echo Shards: 5). Same seed = same plan, always.
     */
    List<Offer> magician(int level,long seed){
        if(!config.getBoolean("magician.random-trades.enabled",true))return magician(level);
        var out=new ArrayList<Offer>();
        for(var planned:plan(seed))if(planned.getValue()<=level)out.add(offer(planned.getKey(),planned.getValue()));
        return out;
    }
    /** id -> level it unlocks at, in order. */
    List<Map.Entry<String,Integer>> plan(long seed){
        Random random=new Random(seed);boolean duplicates=config.getBoolean("magician.random-trades.allow-duplicates",false);
        List<Integer> trades=list("magician.random-trades.trades-per-level",List.of(2,2,2,2,2)),books=list("magician.random-trades.books-per-level",List.of(1,1,1,1,0));
        var bookPool=new ArrayList<String>();for(String b:BOOKS)if(config.getBoolean("magician.trades."+b+".enabled",true))bookPool.add(b);
        var itemPool=new ArrayList<String>();for(String u:UTILITIES)if(config.getBoolean("magician.trades."+u+".enabled",true))itemPool.add(u);
        Collections.shuffle(bookPool,random);Collections.shuffle(itemPool,random);
        var out=new ArrayList<Map.Entry<String,Integer>>();var used=new HashSet<String>();
        for(int level=1;level<=5;level++){
            int total=Math.clamp(trades.size()>=level?trades.get(level-1):0,0,10),bookCount=Math.clamp(books.size()>=level?books.get(level-1):0,0,total);int added=0;
            for(int k=0;k<bookCount;k++){String b=take(bookPool,used,level,random,duplicates);if(b==null)break;out.add(Map.entry(b,level));added++;}
            while(added<total){String u=take(itemPool,used,level,random,duplicates);if(u==null)break;out.add(Map.entry(u,level));added++;}
        }
        // any book still missing joins the last level that has trades, so all four are always reachable
        int last=1;for(int level=1;level<=5;level++)if(trades.size()>=level&&trades.get(level-1)>0)last=level;
        for(String b:bookPool)if(!used.contains(b)){out.add(Map.entry(b,last));used.add(b);}
        return out;
    }
    private String take(List<String> pool,Set<String> used,int level,Random random,boolean duplicates){
        var eligible=pool.stream().filter(id->(duplicates||!used.contains(id))&&minLevel(id)<=level).toList();if(eligible.isEmpty())return null;
        String id=eligible.get(random.nextInt(eligible.size()));used.add(id);return id;
    }
    private int minLevel(String id){return Math.clamp(config.getInt("magician.trades."+id+".min-level",DEFAULTS.get(id)[4]),1,5);}
    private List<Integer> list(String path,List<Integer> fallback){var l=config.getIntegerList(path);return l.isEmpty()?fallback:l;}
    private Offer offer(String id,int unlocked){
        String path="magician.trades."+id+".";int[] d=DEFAULTS.get(id);
        ItemStack result;if(items.ids().contains(id))result=items.create(id);else result=new ItemStack(Material.valueOf(id.toUpperCase(Locale.ROOT)));
        result.setAmount(Math.clamp(config.getInt(path+"amount",d[2]),1,result.getMaxStackSize()));
        if(id.equals("enchanted_book")){
            var meta=(EnchantmentStorageMeta)result.getItemMeta();Enchantment enchantment=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(config.getString(path+"enchantment","unbreaking")));
            if(enchantment==null)enchantment=Enchantment.UNBREAKING;
            meta.addStoredEnchant(enchantment,Math.clamp(config.getInt(path+"enchantment-level",3),1,enchantment.getMaxLevel()),false);result.setItemMeta(meta);
        }
        MerchantRecipe recipe=recipe(result,config.getInt(path+"max-uses",d[3]),config.getInt(path+"villager-xp",5));
        recipe.addIngredient(new ItemStack(Material.EMERALD,Math.clamp(config.getInt(path+"emeralds",d[1]),1,64)));return new Offer(id,unlocked,recipe);
    }
    /** Every Miner sells exactly: miner.offers.minerals mineral trades (default 9), miner.offers.pickaxes enchanted
     * pickaxes (default 2) and one Book of Forging. Offers are rolled once when the Miner spawns and stored with it,
     * so quantities and prices never change when the screen is reopened; restocking only refills uses. */
    /** Exactly minerals + pickaxes + 1 offers (default 9 + 2 + 1 = 12), rolled once when the Miner spawns. */
    List<Offer> miner(Random random){
        var out=new ArrayList<Offer>();int uses=Math.clamp(config.getInt("miner.max-uses",12),1,1024);
        int mineralCount=Math.clamp(config.getInt("miner.offers.minerals",9),0,32),pickaxeCount=Math.clamp(config.getInt("miner.offers.pickaxes",2),0,8);
        int qMin=Math.clamp(config.getInt("miner.offers.quantity-min",1),1,64),qMax=Math.clamp(config.getInt("miner.offers.quantity-max",5),qMin,64);
        // ---- minerals: every (item, direction) is a candidate; one item never appears in both directions ----
        record Mineral(String id,Material material,boolean bulk){}
        var pool=new ArrayList<Mineral>();
        // Ingots and raw metals trade 1-5 at a time; the rest trade in their item form (never ore blocks).
        for(String id:BULK)if(config.getBoolean("miner.minerals."+id+".enabled",true))pool.add(new Mineral(id,Material.valueOf(id.toUpperCase(Locale.ROOT)),true));
        for(String id:ITEMS.keySet())if(config.getBoolean("miner.minerals."+id+".enabled",true))pool.add(new Mineral(id,Material.valueOf(id.toUpperCase(Locale.ROOT)),false));
        Collections.shuffle(pool,random);
        // The first offer is always the Miner SELLING an ingot or raw metal.
        for(int i=0;i<pool.size();i++)if(pool.get(i).bulk()){pool.add(0,pool.remove(i));break;}
        int count=Math.min(mineralCount,pool.size());
        // Directions: offer 0 sells; the rest are at least a third buying and a third selling, shuffled.
        var directions=new ArrayList<Boolean>();for(int i=1;i<count;i++)directions.add(i<=count/3?Boolean.TRUE:i<=2*(count/3)-1?Boolean.FALSE:random.nextBoolean());
        Collections.shuffle(directions,random);directions.add(0,Boolean.FALSE);
        for(int i=0;i<count;i++){
            Mineral m=pool.get(i);
            boolean buy=directions.get(i);
            double[] unit=DEFAULT_UNIT.get(m.id());
            int quantity=m.bulk()?qMin+random.nextInt(qMax-qMin+1):Math.clamp(config.getInt("miner.minerals."+m.id()+".amount",ITEMS.get(m.id())),1,64);
            double buyUnit=config.getDouble("miner.minerals."+m.id()+".buy-price",unit[0]),sellUnit=Math.max(buyUnit,config.getDouble("miner.minerals."+m.id()+".sell-price",unit[1]));
            int emeralds=buy?(int)Math.floor(buyUnit*quantity):(int)Math.ceil(sellUnit*quantity);
            emeralds=Math.clamp(emeralds,1,64);
            String id=(buy?"buy_":"sell_")+m.id();
            if(config.isInt("miner.trades."+id+".emeralds"))emeralds=Math.clamp(config.getInt("miner.trades."+id+".emeralds"),1,64);
            if(buy)addMiner(out,id,new ItemStack(Material.EMERALD,emeralds),new ItemStack(m.material(),quantity),uses);
            else addMiner(out,id,new ItemStack(m.material(),quantity),new ItemStack(Material.EMERALD,emeralds),uses);
        }
        // ---- enchanted pickaxes (weighted materials, valid enchantments, never two identical) ----
        var seen=new HashSet<String>();int made=0;
        for(int attempt=0;made<pickaxeCount&&attempt<100;attempt++){
            ItemStack pick=pickaxe(random);if(pick==null)break;
            String key=pick.getType()+":"+pick.getEnchantments();if(!seen.add(key))continue;
            String id="pickaxe_"+(++made);
            addMiner(out,id,pick,new ItemStack(Material.EMERALD,pickaxePrice(pick)),Math.clamp(config.getInt("miner.pickaxe-max-uses",3),1,1024));
        }
        MerchantRecipe forging=recipe(items.create("forging_book"),Math.clamp(config.getInt("miner.forging-book-max-uses",3),1,1024),0);
        forging.addIngredient(new ItemStack(Material.EMERALD,Math.clamp(config.getInt("miner.forging-book-emeralds",30),1,64)));forging.addIngredient(new ItemStack(Material.ANVIL));out.add(new Offer("forging_book",1,forging));return out;
    }
    /** Metals traded 1-5 at a time (miner.offers.quantity-min/max). */
    static final List<String> BULK=List.of("raw_iron","iron_ingot","raw_gold","gold_ingot","raw_copper","copper_ingot");
    /** Other minerals in their item form, with the default number per trade. */
    static final Map<String,Integer> ITEMS=new LinkedHashMap<>(){{put("coal",8);put("redstone",8);put("lapis_lazuli",6);put("diamond",1);put("amethyst_shard",4);put("quartz",8);}};
    /** Default emeralds per item: {Miner pays, Miner charges}. */
    static final Map<String,double[]> DEFAULT_UNIT=Map.ofEntries(Map.entry("raw_iron",new double[]{.6,1.0}),Map.entry("iron_ingot",new double[]{.8,1.3}),Map.entry("raw_gold",new double[]{.9,1.5}),Map.entry("gold_ingot",new double[]{1.2,2.0}),
        Map.entry("raw_copper",new double[]{.3,.5}),Map.entry("copper_ingot",new double[]{.4,.7}),Map.entry("coal",new double[]{.15,.3}),Map.entry("redstone",new double[]{.15,.3}),
        Map.entry("lapis_lazuli",new double[]{.25,.5}),Map.entry("diamond",new double[]{4,7}),Map.entry("amethyst_shard",new double[]{.3,.6}),Map.entry("quartz",new double[]{.15,.3}));
    static final Map<String,Integer> PICK_WEIGHT=Map.of("stone",40,"iron",30,"golden",20,"diamond",10);
    static final Map<String,Integer> PICK_BASE=Map.of("stone",2,"iron",5,"golden",6,"diamond",14);
    static final Map<String,double[]> ENCHANT_DEFAULTS=new LinkedHashMap<>(){{put("efficiency",new double[]{5,2});put("unbreaking",new double[]{3,1.5});put("fortune",new double[]{3,4});put("silk_touch",new double[]{1,6});put("mending",new double[]{1,10});}};
    private ItemStack pickaxe(Random random){
        int total=0;var weights=new LinkedHashMap<String,Integer>();
        for(var e:PICK_WEIGHT.entrySet()){int w=Math.max(0,config.getInt("miner.pickaxes."+e.getKey()+".weight",e.getValue()));if(w>0){weights.put(e.getKey(),w);total+=w;}}
        if(total==0)return null;int roll=random.nextInt(total);String tier="stone";for(var e:weights.entrySet()){if(roll<e.getValue()){tier=e.getKey();break;}roll-=e.getValue();}
        ItemStack pick=new ItemStack(Material.valueOf(tier.toUpperCase(Locale.ROOT)+"_PICKAXE"));
        var names=new ArrayList<String>();for(String n:ENCHANT_DEFAULTS.keySet())if(config.getBoolean("miner.enchantments."+n+".enabled",true))names.add(n);
        if(names.isEmpty())return pick;
        Collections.shuffle(names,random);int wanted=1+random.nextInt(Math.clamp(config.getInt("miner.enchantments-per-pickaxe",3),1,names.size()));
        for(String n:names){
            if(pick.getEnchantments().size()>=wanted)break;
            Enchantment enchantment=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(n));if(enchantment==null)continue;
            if(pick.getEnchantments().keySet().stream().anyMatch(enchantment::conflictsWith))continue;   // e.g. Fortune vs Silk Touch
            int max=Math.clamp(config.getInt("miner.enchantments."+n+".max-level",(int)ENCHANT_DEFAULTS.get(n)[0]),1,enchantment.getMaxLevel());
            pick.addEnchantment(enchantment,1+random.nextInt(max));
        }
        return pick;
    }
    private int pickaxePrice(ItemStack pick){
        String tier=pick.getType().name().replace("_PICKAXE","").toLowerCase(Locale.ROOT);
        double price=config.getDouble("miner.pickaxes."+tier+".base-price",PICK_BASE.getOrDefault(tier,2));
        for(var e:pick.getEnchantments().entrySet()){String n=e.getKey().getKey().getKey();price+=e.getValue()*config.getDouble("miner.enchantments."+n+".price-per-level",ENCHANT_DEFAULTS.containsKey(n)?ENCHANT_DEFAULTS.get(n)[1]:2);}
        int floor=Math.clamp(config.getInt("miner.pickaxe-min-price",1),1,64),top=Math.clamp(config.getInt("miner.pickaxe-max-price",64),floor,64);
        return Math.clamp((int)Math.round(price),floor,top);
    }
    private void addMiner(List<Offer> offers,String id,ItemStack result,ItemStack ingredient,int uses){
        String path="miner.trades."+id+".";if(!config.getBoolean(path+"enabled",true))return;
        ingredient.setAmount(Math.clamp(config.getInt(path+"input-amount",ingredient.getAmount()),1,ingredient.getMaxStackSize()));
        result.setAmount(Math.clamp(config.getInt(path+"amount",result.getAmount()),1,result.getMaxStackSize()));
        MerchantRecipe recipe=recipe(result,config.getInt(path+"max-uses",uses),0);recipe.addIngredient(ingredient);offers.add(new Offer(id,1,recipe));
    }
    /** Default per-item emerald bands; miner.ore-/pickaxe- min/max prices still bound every roll. */
    static final Map<String,int[]> ORE_BANDS=Map.of("coal",new int[]{1,2},"copper",new int[]{1,2},"redstone",new int[]{1,3},"lapis",new int[]{1,3},"iron",new int[]{2,4},"gold",new int[]{3,6},"diamond",new int[]{6,9});
    static final Map<String,int[]> PICKAXE_BANDS=Map.of("wooden",new int[]{1,1},"stone",new int[]{1,2},"golden",new int[]{2,4},"iron",new int[]{3,6},"diamond",new int[]{10,20});
    /** Rolls within the item's band, then clamps to the configured global range; miner.trades.<id>.emeralds fixes a price. */
    private int tradePrice(Random random,String id,String type,int low,int high,int ceiling){
        int floor=Math.clamp(config.getInt("miner."+type+"-min-price",1),1,64),top=Math.clamp(config.getInt("miner."+type+"-max-price",ceiling),floor,64);
        if(config.isInt("miner.trades."+id+".emeralds"))return Math.clamp(config.getInt("miner.trades."+id+".emeralds"),1,64);
        int min=Math.clamp(low,floor,top),max=Math.clamp(high,min,top);return min+random.nextInt(max-min+1);
    }
    static MerchantRecipe recipe(ItemStack output,int uses,int xp){var recipe=new MerchantRecipe(output,0,Math.clamp(uses,1,1024),true,Math.clamp(xp,0,1000),0);recipe.setIgnoreDiscounts(true);return recipe;}
    /** CraftMerchantRecipe stores live state outside MerchantRecipe's private fields.
     * Its base-class copy constructor silently loses that state: use virtual getters. */
    static MerchantRecipe copy(MerchantRecipe source){
        var result=new MerchantRecipe(source.getResult().clone(),source.getUses(),source.getMaxUses(),source.hasExperienceReward(),source.getVillagerExperience(),source.getPriceMultiplier(),source.getDemand(),source.getSpecialPrice(),source.shouldIgnoreDiscounts());
        result.setIngredients(source.getIngredients().stream().map(ItemStack::clone).toList());return result;
    }
    static boolean ordinary(ItemStack stack,ExpansionItems items){return stack!=null&&!stack.getType().isAir()&&items.id(stack).isEmpty()&&(!stack.hasItemMeta()||!stack.getItemMeta().getPersistentDataContainer().has(ExpansionItems.KIND));}
    static String encode(List<Offer> offers){
        var data=new YamlConfiguration();int index=0;for(Offer offer:offers){String p="offers."+(index++)+".";MerchantRecipe r=offer.recipe();data.set(p+"id",offer.id());data.set(p+"level",offer.level());data.set(p+"uses",r.getUses());data.set(p+"max",r.getMaxUses());data.set(p+"xp",r.getVillagerExperience());data.set(p+"reward",r.hasExperienceReward());data.set(p+"multiplier",r.getPriceMultiplier());data.set(p+"demand",r.getDemand());data.set(p+"special",r.getSpecialPrice());data.set(p+"ignore",r.shouldIgnoreDiscounts());var stacks=new ArrayList<ItemStack>();stacks.add(r.getResult());stacks.addAll(r.getIngredients());data.set(p+"items",ItemCodec.encode(stacks));}return data.saveToString();
    }
    static List<Offer> decode(String encoded){
        var data=new YamlConfiguration();try{data.loadFromString(encoded);}catch(InvalidConfigurationException e){throw new IllegalArgumentException("Invalid merchant offers",e);}
        var out=new ArrayList<Offer>();var section=data.getConfigurationSection("offers");if(section==null)return out;
        if(section.getKeys(false).size()>128)throw new IllegalArgumentException("Too many merchant offers");
        for(String key:section.getKeys(false)){String p="offers."+key+".";var stacks=ItemCodec.decode(data.getString(p+"items",""));if(stacks.size()<2||stacks.size()>3)throw new IllegalArgumentException("Invalid merchant ingredients");
            var recipe=new MerchantRecipe(stacks.getFirst(),Math.clamp(data.getInt(p+"uses"),0,1024),Math.clamp(data.getInt(p+"max"),1,1024),data.getBoolean(p+"reward",true),data.getInt(p+"xp"),(float)data.getDouble(p+"multiplier"),data.getInt(p+"demand"),data.getInt(p+"special"),data.getBoolean(p+"ignore",true));recipe.setIngredients(stacks.subList(1,stacks.size()));out.add(new Offer(data.getString(p+"id"),data.getInt(p+"level",1),recipe));}
        return out;
    }
}
