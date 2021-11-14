package memory.cache.cacheReplacementStrategy;

import memory.Memory;
import memory.cache.Cache;

/**
 * TODO 先进先出算法
 */
public class FIFOReplacement implements ReplacementStrategy {

    @Override
    public int getReplacedLine(int start, int end, Cache.CacheLinePool pool) {
        int min = start;
        for (int i = start; i <= end; i++) {
            if(pool.get(i).getTimeStamp()<pool.get(min).getTimeStamp()){
                min = i;
            }
        }
        return min;
    }

    @Override
    public void hit(int rowNO) {
    }

    @Override
    public int replace(int start, int end, char[] addrTag, char[] input) {
        return -1;
    }

}
