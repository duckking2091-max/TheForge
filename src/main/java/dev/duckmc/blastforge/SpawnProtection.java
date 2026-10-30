package dev.duckmc.blastforge;

final class SpawnProtection {
    static boolean blocked(boolean primaryWorld, boolean hasOperators, boolean operator, int radius, int dx, int dz) {
        return primaryWorld && hasOperators && !operator && radius>0
            && Math.max(Math.abs((long)dx),Math.abs((long)dz))<=radius;
    }
}
