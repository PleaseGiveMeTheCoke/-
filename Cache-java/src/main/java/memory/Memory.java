package memory;

import memory.cache.Cache;
import util.Transformer;

/**
 * 内存抽象类
 */

public class Memory {

    private static final int MEM_SIZE_B = 32 * 1024 * 1024;      // 32 MB

    private static final char[] memory = new char[MEM_SIZE_B];

    private static final Memory memoryInstance = new Memory();

    private Memory() {
    }

    public static Memory getMemory() {
        return memoryInstance;
    }

    public char[] read(String pAddr, int len) {
        char[] data = new char[len];
        for (int ptr = 0; ptr < len; ptr++) {
            int indexInMemory = Integer.parseInt(new Transformer().binaryToInt(pAddr)) + ptr;
            data[ptr] = memory[indexInMemory];
        }
        return data;
    }

    public void write(String pAddr, int len, char[] data) {
        // 通知Cache缓存失效
        Cache.getCache().invalid(pAddr, len);
        // 更新数据
        for (int ptr = 0; ptr < len; ptr++) {
            memory[Integer.parseInt(new Transformer().binaryToInt(pAddr)) + ptr] = data[ptr];
        }
    }

    public static void main(String[] args) {
        System.out.println(new Transformer().binaryToInt("0101"));
    }
}




















































