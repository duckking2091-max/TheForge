package dev.duckmc.blastforge;

import org.bukkit.Material;
import org.bukkit.Tag;

final class BlastTools {
    static boolean supported(Material tool) {
        return Tag.ITEMS_PICKAXES.isTagged(tool) || Tag.ITEMS_SHOVELS.isTagged(tool);
    }
    static boolean mineable(Material tool,Material block) {
        if(Tag.ITEMS_PICKAXES.isTagged(tool))return Tag.MINEABLE_PICKAXE.isTagged(block);
        if(Tag.ITEMS_SHOVELS.isTagged(tool))return Tag.MINEABLE_SHOVEL.isTagged(block);
        return false;
    }
}
