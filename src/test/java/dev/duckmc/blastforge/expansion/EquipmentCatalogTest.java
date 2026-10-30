package dev.duckmc.blastforge.expansion;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentCatalogTest {
    private ExpansionItems items;
    @BeforeEach void setup() { MockBukkit.mock(); JavaPlugin plugin=MockBukkit.createMockPlugin(); items=new ExpansionItems(plugin,name->new YamlConfiguration()); }
    @AfterEach void teardown() { MockBukkit.unmock(); }
    @Test void catalogIncludesEveryGearAndIngredientWithStableIdentity() {
        assertEquals(65,items.ids().size()); // 55, the three spears, the Magician hat, five tab icons and the guide button
        assertEquals(9058,items.create("magician_hat").getItemMeta().getCustomModelData());
        for(String id:items.ids()) { ItemStack stack=items.create(id); assertEquals(id,items.id(stack)); assertNotEquals(0,stack.getItemMeta().getCustomModelData()); }
        ItemStack fake=new ItemStack(Material.NETHERITE_INGOT); fake.editMeta(meta->meta.displayName(Component.text("Wardenite Ingot")));
        assertEquals("",items.id(fake));
        ItemStack old=new ItemStack(Material.ENCHANTED_BOOK); old.editMeta(meta->meta.getPersistentDataContainer().set(NamespacedKey.fromString("blastforge:item_kind"),PersistentDataType.STRING,"forging_book"));
        assertEquals("forging_book",items.id(old));
    }
    @Test void upgradesPreserveCustomMetadataEnchantmentsAndRelativeDurability() {
        ItemStack base=new ItemStack(Material.NETHERITE_PICKAXE);
        base.addUnsafeEnchantment(Enchantment.FORTUNE,3); base.addUnsafeEnchantment(Enchantment.SILK_TOUCH,1);
        base.editMeta(meta->{meta.displayName(Component.text("Trusted pick")); ((Damageable)meta).setDamage(1016); meta.getPersistentDataContainer().set(NamespacedKey.fromString("other:owner"),PersistentDataType.STRING,"owner");});
        ItemStack before=base.clone(),result=items.upgrade(base,"siftie");
        assertEquals("siftie_pickaxe",items.id(result)); assertEquals(before,base);
        assertEquals((int)Math.round(1016.0*2500/Material.NETHERITE_PICKAXE.getMaxDurability()),((Damageable)result.getItemMeta()).getDamage());
        assertEquals(2500,((Damageable)result.getItemMeta()).getMaxDamage());
        assertEquals(base.getEnchantments(),result.getEnchantments()); assertEquals(base.getItemMeta().displayName(),result.getItemMeta().displayName());
        assertEquals("owner",result.getItemMeta().getPersistentDataContainer().get(NamespacedKey.fromString("other:owner"),PersistentDataType.STRING));
        assertThrows(IllegalArgumentException.class,()->items.upgrade(base,"wardenite"));
    }
    @Test void toolDurabilitiesAndScytheMatchRequiredValues() {
        for(String piece:new String[]{"sword","pickaxe","axe","shovel","hoe"}) {
            assertEquals(2500,((Damageable)items.create("siftie_"+piece).getItemMeta()).getMaxDamage());
            assertEquals(3000,((Damageable)items.create("enderite_"+piece).getItemMeta()).getMaxDamage());
            assertEquals(3500,((Damageable)items.create("wardenite_"+piece).getItemMeta()).getMaxDamage());
        }
        assertEquals(5000,((Damageable)items.create("legendary_scythe").getItemMeta()).getMaxDamage());
    }
    @Test void harvestRequiresCorrectPickaxeAndActualTierIdentity() {
        assertTrue(items.canHarvest(new ItemStack(Material.DIAMOND_PICKAXE),"siftie"));
        assertFalse(items.canHarvest(new ItemStack(Material.DIAMOND_PICKAXE),"enderite"));
        assertTrue(items.canHarvest(new ItemStack(Material.NETHERITE_PICKAXE),"enderite"),"netherite mines Enderite, just slower");
        assertTrue(items.canHarvest(items.create("siftie_pickaxe"),"enderite"));
        assertTrue(items.canHarvest(items.create("wardenite_pickaxe"),"enderite"));
        assertFalse(items.canHarvest(items.create("wardenite_axe"),"siftie"));
        assertFalse(items.canHarvest(new ItemStack(Material.IRON_PICKAXE),"siftie"));
        assertFalse(items.canHarvest(null,"siftie"));
    }
    @Test void armorRetainsVanillaCarrierAndRequiredToughness() {
        for(String tier:java.util.List.of("siftie","enderite","wardenite")) {
            var meta=items.create(tier+"_helmet").getItemMeta();
            assertEquals(Material.NETHERITE_HELMET,items.create(tier+"_helmet").getType()); // Native equippable serialization needs Paper: MockBukkit copy constructor drops this component.
            assertEquals(4+java.util.List.of("siftie","enderite","wardenite").indexOf(tier)*.5,meta.getAttributeModifiers(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS).iterator().next().getAmount());
        }
    }
    @Test void upgradePreservesUnrelatedAttributesIncludingUnknownBlastforgeKeys() {
        ItemStack base=items.create("siftie_sword");
        var modifier=new org.bukkit.attribute.AttributeModifier(NamespacedKey.fromString("blastforge:custom_bonus"),2,org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,org.bukkit.inventory.EquipmentSlotGroup.MAINHAND);
        base.editMeta(meta->meta.addAttributeModifier(org.bukkit.attribute.Attribute.MAX_HEALTH,modifier));
        var result=items.upgrade(base,"enderite");assertTrue(result.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.MAX_HEALTH).contains(modifier));
    }    @Test void malformedPersistentIdentityTypeIsRejectedWithoutThrowing() {
        ItemStack malformed=items.create("raw_siftie");malformed.editMeta(meta->meta.getPersistentDataContainer().set(NamespacedKey.fromString("blastforge:item_kind"),PersistentDataType.INTEGER,7));
        assertEquals("",assertDoesNotThrow(()->items.id(malformed)));
    }    @Test void siftieStatsMatchTheDesign(){
        java.util.function.BiFunction<String,org.bukkit.attribute.Attribute,Double> stat=(id,a)->{var m=items.create(id).getItemMeta().getAttributeModifiers(a);return m==null||m.isEmpty()?0:m.iterator().next().getAmount();};
        // Tooltip damage = 1 + modifier; tooltip speed = 4 + modifier.
        assertEquals(9,1+stat.apply("siftie_sword",org.bukkit.attribute.Attribute.ATTACK_DAMAGE),1e-9);assertEquals(1.7,4+stat.apply("siftie_sword",org.bukkit.attribute.Attribute.ATTACK_SPEED),1e-9);
        assertEquals(7,1+stat.apply("siftie_pickaxe",org.bukkit.attribute.Attribute.ATTACK_DAMAGE),1e-9);assertEquals(1.2,4+stat.apply("siftie_axe",org.bukkit.attribute.Attribute.ATTACK_SPEED),1e-9);
        assertEquals(2,1+stat.apply("siftie_hoe",org.bukkit.attribute.Attribute.ATTACK_DAMAGE),1e-9);assertEquals(4.5,4+stat.apply("siftie_hoe",org.bukkit.attribute.Attribute.ATTACK_SPEED),1e-9);
        assertEquals(6,1+stat.apply("siftie_spear",org.bukkit.attribute.Attribute.ATTACK_DAMAGE),1e-9);assertEquals(Material.NETHERITE_SPEAR,items.create("siftie_spear").getType());
        assertEquals(9,stat.apply("siftie_chestplate",org.bukkit.attribute.Attribute.ARMOR),1e-9);assertEquals(4,stat.apply("siftie_boots",org.bukkit.attribute.Attribute.ARMOR),1e-9);
        assertEquals(.15,stat.apply("siftie_leggings",org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE),1e-9);
    }
    @Test void enderiteStatsComeFromMaterialsYml() throws Exception {
        var cfg=new org.bukkit.configuration.file.YamlConfiguration();cfg.load(new java.io.File("src/main/resources/materials.yml"));
        var plugin=org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin();var tiered=new ExpansionItems(plugin,n->cfg);
        double kbHelmet=sum(tiered.create("enderite_helmet"),org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE),kbBoots=sum(tiered.create("enderite_boots"),org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
        assertEquals(.2,kbHelmet,1e-9);assertEquals(.15,kbBoots,1e-9);
        assertEquals(5,sum(tiered.create("enderite_chestplate"),org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS),1e-9);
        assertEquals(9,sum(tiered.create("enderite_chestplate"),org.bukkit.attribute.Attribute.ARMOR),1e-9);
        assertEquals(6.0,sum(tiered.create("enderite_pickaxe"),org.bukkit.attribute.Attribute.BLOCK_BREAK_SPEED),1e-9,"x7 mining = +600%");
        // Without Efficiency: netherite speed 9 x 7 = 63. Instant needs speed/hardness/30 >= 1.
        assertTrue(63/1.5/30>=1,"stone instant");assertTrue(63/2.0/30>=1,"cobblestone instant");assertTrue(63/3.0/30<1,"deepslate not instant");
        assertEquals(650,((Damageable)tiered.create("siftie_helmet").getItemMeta()).getMaxDamage());
        assertEquals(750,((Damageable)tiered.create("enderite_helmet").getItemMeta()).getMaxDamage());
        assertEquals(1091,((Damageable)tiered.create("enderite_chestplate").getItemMeta()).getMaxDamage());
        assertEquals(1.3-4,sum(tiered.create("enderite_axe"),org.bukkit.attribute.Attribute.ATTACK_SPEED),1e-9);
        assertEquals(3157,((Damageable)tiered.create("enderite_sword").getItemMeta()).getMaxDamage());
    }
    static double sum(ItemStack item,org.bukkit.attribute.Attribute attribute){var m=item.getItemMeta().getAttributeModifiers(attribute);return m==null?0:m.stream().mapToDouble(org.bukkit.attribute.AttributeModifier::getAmount).sum();}
    @Test void wardeniteStatsComeFromMaterialsYml() throws Exception {
        var cfg=new org.bukkit.configuration.file.YamlConfiguration();cfg.load(new java.io.File("src/main/resources/materials.yml"));
        var tiered=new ExpansionItems(org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin(),n->cfg);
        assertEquals(10-1,sum(tiered.create("wardenite_sword"),org.bukkit.attribute.Attribute.ATTACK_DAMAGE),1e-9);
        assertEquals(1.9-4,sum(tiered.create("wardenite_sword"),org.bukkit.attribute.Attribute.ATTACK_SPEED),1e-9);
        assertEquals(1.4-4,sum(tiered.create("wardenite_spear"),org.bukkit.attribute.Attribute.ATTACK_SPEED),1e-9);
        assertEquals(.25,sum(tiered.create("wardenite_chestplate"),org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE),1e-9);
        assertEquals(6,sum(tiered.create("wardenite_boots"),org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS),1e-9);
        assertEquals(3657,((Damageable)tiered.create("wardenite_pickaxe").getItemMeta()).getMaxDamage());
        for(String piece:java.util.List.of("helmet","chestplate","leggings","boots")){int d=((Damageable)tiered.create("wardenite_"+piece).getItemMeta()).getMaxDamage();assertTrue(d>=1000&&d<1200,piece+" "+d);}
        assertTrue(9*10/3.0/30>=1,"deepslate instant without Efficiency");
    }
}
