package memory.cache.cacheReplacementStrategy;

import memory.cache.Cache;

/**
 * TODO 最近不经常使用算法
 */
public class LFUReplacement implements ReplacementStrategy {

    @Override
    public int getReplacedLine(int start, int end, Cache.CacheLinePool pool) {
        int min = start;
        for (int i = start; i <= end; i++) {
            if(pool.get(i).getVisited()<pool.get(min).getVisited()){
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
