package dev.duckmc.blastforge;

import java.util.Set;

final class MergeRules {
    private static final Set<String> FAMILIES = Set.of("PICKAXE","AXE","SHOVEL","HOE","SWORD","HELMET","CHESTPLATE","LEGGINGS","BOOTS","SPEAR");
    private static final Set<String> MATERIALS = Set.of("WOODEN","STONE","COPPER","IRON","GOLDEN","DIAMOND","NETHERITE","LEATHER","CHAINMAIL","TURTLE");
    static String family(String material) {
        int split = material.lastIndexOf('_');
        if (split < 0) return "";
        String prefix=material.substring(0,split), suffix=material.substring(split+1);
        return MATERIALS.contains(prefix) && FAMILIES.contains(suffix) ? suffix : "";
    }
    static int level(int left, int right, int max) {
        return Math.min(max, left == right ? left+1 : Math.max(left,right));
    }
}
