package dev.duckmc.blastforge;

import java.util.ArrayList;
import java.util.List;

final class MiningShape {
    record Cell(int x, int y, int z) {}
    static List<Cell> offsets(int depth, int axis, int sign) {
        if (depth < 1 || depth > 3 || axis < 0 || axis > 2 || Math.abs(sign) != 1)
            throw new IllegalArgumentException("Invalid mining shape");
        var result = new ArrayList<Cell>();
        // Center first so subsequent layers always extend away from the struck face.
        for (int d=0; d<depth; d++) for (int a=-1; a<=1; a++) for (int b=-1; b<=1; b++) {
            int[] c = new int[3];
            c[axis] = d*sign; c[(axis+1)%3]=a; c[(axis+2)%3]=b;
            result.add(new Cell(c[0],c[1],c[2]));
        }
        return List.copyOf(result);
    }
}
