package dev.duckmc.blastforge;

import org.bukkit.configuration.file.FileConfiguration;

final class Rarity {
    static final double FORGING_CHANCE=0.02, BLAST_CHANCE=0.04;
    private static final int[] WEIGHTS={80,17,3};
    static int level(FileConfiguration config,double roll) {
        long[] weights=new long[3];long total=0;
        for(int i=0;i<3;i++) {
            weights[i]=Math.max(0,config.getInt("blast-level-weights."+(i+1),WEIGHTS[i]));
            total+=weights[i];
        }
        if(total==0){weights=new long[]{80,17,3};total=100;}
        double position=roll*total;
        long boundary=0;
        for(int i=0;i<3;i++){boundary+=weights[i];if(position<boundary)return i+1;}
        return 3;
    }
    static boolean migrate(FileConfiguration config) {
        if(config.isSet("rarity-version"))return false;
        upgradeChance(config,"forging-book-trade-chance",FORGING_CHANCE);
        upgradeChance(config,"blast-book-trade-chance",BLAST_CHANCE);
        for(int i=0;i<3;i++)if(!config.isSet("blast-level-weights."+(i+1)))config.set("blast-level-weights."+(i+1),WEIGHTS[i]);
        config.set("rarity-version",1);
        return true;
    }
    private static void upgradeChance(FileConfiguration config,String key,double value) {
        if(!config.isSet(key) || config.getDouble(key)==0.15)config.set(key,value);
    }
}
