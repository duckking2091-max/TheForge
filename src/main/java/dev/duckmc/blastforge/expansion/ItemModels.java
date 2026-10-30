package dev.duckmc.blastforge.expansion;
import java.util.*;
/** Stable resource-pack model numbers. Never reorder released entries. */
public final class ItemModels {
    private static final List<String> IDS;
    static {
        var ids=new ArrayList<>(List.of("enchanting_anvil","forging_book","raw_siftie","siftie_ingot","siftie_ore","siftie_template","enderite_scrap","enderite_ingot","enderite_ore","enderite_template","enderite_stick","wardenite_scrap","wardenite_ingot","wardenite_template","dragon_heart","warden_heart","bat_wing","fire_book","water_book","earth_book","wind_book","darkness_book","legendary_scythe"));
        for(String tier:List.of("siftie","enderite","wardenite"))for(String type:List.of("sword","pickaxe","axe","shovel","hoe","helmet","chestplate","leggings","boots"))ids.add(tier+"_"+type);
        ids.addAll(List.of("anvil_hammer","anvil_tools","magician_robes","miner_helmet","miner_backpack"));
        // Added later: appended so earlier model numbers never change.
        for(String tier:List.of("siftie","enderite","wardenite"))ids.add(tier+"_spear");
        ids.add("magician_hat");
        ids.addAll(List.of("tab_forging","tab_upgrading","tab_ritual","tab_rename","tab_close"));
        ids.add("guide_button");IDS=List.copyOf(ids);
    }
    private ItemModels(){}
    public static List<String> ids(){return IDS;}
    public static int number(String id){int index=IDS.indexOf(id);if(index<0)throw new IllegalArgumentException("Unknown model: "+id);return 9000+index;}
}
