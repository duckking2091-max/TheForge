package dev.duckmc.blastforge.expansion;

import java.util.HashMap;
import java.util.Map;

/** Exact advance (pixels) of every glyph in Minecraft 1.21.11's ascii.png, measured the way the client does. Generated. */
final class GuideFont {
    private GuideFont(){}
    private static final int[] CODES={32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,163,170,171,172,176,177,178,186,187,247,402,8319,8709,8712,8729,8730,8776,8801,8804,8805,8992,8993,9472,9474,9484,9488,9492,9496,9500,9508,9516,9524,9532,9552,9553,9554,9555,9556,9557,9558,9559,9560,9561,9562,9563,9564,9565,9566,9567,9568,9569,9570,9571,9572,9573,9574,9575,9576,9577,9578,9579,9580,9600,9604,9608,9612,9616,9617,9618,9619,9632};
    private static final byte[] WIDTHS={4,2,4,6,6,6,6,2,4,4,4,6,2,6,2,6,6,6,6,6,6,6,6,6,6,6,2,2,5,6,5,6,7,6,6,6,6,6,6,6,6,4,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,4,6,4,6,6,3,6,6,6,6,6,5,6,6,2,6,5,3,6,6,6,6,6,6,6,4,6,6,6,6,6,6,4,2,4,7,6,5,7,6,5,6,5,5,7,6,6,5,8,6,6,7,7,7,6,6,8,5,9,6,9,6,9,6,9,6,9,9,9,9,8,9,9,9,6,8,8,9,9,9,6,8,8,9,9,9,6,8,8,9,9,9,9,9,9,9,9,9,9,9,9,5,9,8,9,9,6};
    private static final Map<Character,Integer> TABLE=new HashMap<>();
    static{for(int i=0;i<CODES.length;i++)TABLE.put((char)CODES[i],(int)WIDTHS[i]);}
    static boolean has(char c){return TABLE.containsKey(c);}
    static int width(char c){return TABLE.getOrDefault(c,6);}
    static int width(String s){int w=0;for(int i=0;i<s.length();i++)w+=width(s.charAt(i));return w;}
}
