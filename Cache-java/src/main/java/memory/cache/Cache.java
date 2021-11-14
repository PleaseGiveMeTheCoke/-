package memory.cache;

import memory.Memory;
import memory.cache.cacheReplacementStrategy.ReplacementStrategy;
import util.Transformer;

import java.util.Arrays;

/**
 * 高速缓存抽象类
 */
public class Cache {

    public static final boolean isAvailable = true; // 默认启用Cache

    public static final int CACHE_SIZE_B = 1024 * 1024; // 1 MB 总大小

    public static final int LINE_SIZE_B = 1024; // 1 KB 行大小

    private final CacheLinePool cache = new CacheLinePool(CACHE_SIZE_B / LINE_SIZE_B);  // 总大小1MB / 行大小1KB = 1024个行

    private int SETS;   // 组数

    private int setSize;    // 每组行数

    // 单例模式
    private static final Cache cacheInstance = new Cache();

    private Cache() {
    }

    public static Cache getCache() {
        return cacheInstance;
    }

    private ReplacementStrategy replacementStrategy;    // 替换策略

    public static boolean isWriteBack;   // 写策略

    private final Transformer transformer = new Transformer();


    /**
     * 读取[pAddr, pAddr + len)范围内的连续数据，可能包含多个数据块的内容
     *
     * @param pAddr 数据起始点(32位物理地址 = 22位块号 + 10位块内地址)
     * @param len   待读数据的字节数
     * @return 读取出的数据，以char数组的形式返回
     */
    public char[] read(String pAddr, int len) {
        char[] data = new char[len];
        int addr = Integer.parseInt(transformer.binaryToInt("0" + pAddr));
        int upperBound = addr + len;
        int index = 0;
        while (addr < upperBound) {
            int nextSegLen = LINE_SIZE_B - (addr % LINE_SIZE_B);
            if (addr + nextSegLen >= upperBound) {
                nextSegLen = upperBound - addr;
            }
            int rowNO = fetch(transformer.intToBinary(String.valueOf(addr)),null);
            char[] cache_data = cache.get(rowNO).getData();
            int i = 0;
            while (i < nextSegLen) {
                data[index] = cache_data[addr % LINE_SIZE_B + i];
                index++;
                i++;
            }
            addr += nextSegLen;
        }
        return data;
    }
    public class WriteMessage{
        String pAddr;
        int len;


        public WriteMessage(String pAddr, int len) {
            this.pAddr = pAddr;
            this.len = len;
        }
    }
    /**
     * 向cache中写入[pAddr, pAddr + len)范围内的连续数据，可能包含多个数据块的内容
     *
     * @param pAddr 数据起始点(32位物理地址 = 22位块号 + 10位块内地址)
     * @param len   待写数据的字节数
     * @param data  待写数据
     */

    public void write(String pAddr, int len, char[] data) {
        if(!Cache.isWriteBack) {
            Memory.getMemory().write(pAddr, len, data);
        }
        int addr = Integer.parseInt(transformer.binaryToInt("0" + pAddr));
        int upperBound = addr + len;
        int index = 0;
        while (addr < upperBound) {
            int nextSegLen = LINE_SIZE_B - (addr % LINE_SIZE_B);
            if (addr + nextSegLen >= upperBound) {
                nextSegLen = upperBound - addr;
            }
            int rowNO = fetch(transformer.intToBinary(String.valueOf(addr)),new WriteMessage(pAddr,len));
            if(Cache.isWriteBack) {
                if(cache.get(rowNO).validBit) {
                    cache.get(rowNO).setDirty(true);
                }
            }
            char[] cache_data = cache.get(rowNO).getData();
            int i = 0;
            while (i < nextSegLen) {
                cache_data[addr % LINE_SIZE_B + i] = data[index];
                index++;
                i++;
            }

            // TODO

            addr += nextSegLen;
        }
    }

    /**
     * 查询{@link Cache#cache}表以确认包含pAddr的数据块是否在cache内
     * 如果目标数据块不在Cache内，则将其从内存加载到Cache
     *
     * @param pAddr 数据起始点(32位物理地址 = 22位块号 + 10位块内地址)
     * @return 数据块在Cache中的对应行号
     */

    private int fetch(String pAddr,WriteMessage wm) {


        int blockNO = Integer.parseInt(transformer.binaryToInt(pAddr.substring(0, 22)));
        // TODO
        if(map(blockNO)==-1){
            //未命中,根据pAddr中的信息将数据写入到Cache
            int bitOfSet = (int)(Math.log(SETS)/Math.log(2));
            //取出对应组号
            int SetNum = blockNO%(int)Math.pow(2,bitOfSet);
            int lineNum = SetNum*setSize;
            CacheLine cacheLine = cache.get(lineNum);
            int flag = 0;

            while (!cacheLine.isEmpty&&cacheLine.validBit) {
                //TODO 替换策略
                flag++;
                lineNum++;
                if (flag == setSize) {
                    lineNum = replacementStrategy.getReplacedLine(lineNum - setSize, lineNum - 1, cache);
                    cacheLine = cache.get(lineNum);
                    break;
                }
                cacheLine = cache.get(lineNum);

            }
            if(cacheLine.isDirty()&&cacheLine.validBit){
                //do write back
                //1.得到tag
                char[] tag = cacheLine.tag;
                int bitOfTag = 22-(10-(int)(Math.log(setSize)/Math.log(2)));
                //tag的0-bitOfTag位区分的是映射到同一组的不同块
                //bitOfTag-22为区分的是映射时所对应的Cache中的组
                char[] zeroToBt = new char[bitOfTag];
                for (int i = 0; i < bitOfTag; i++) {
                    zeroToBt[i] = tag[i];
                }

                //解析出组号
                //int setNum = Integer.parseInt(transformer.binaryToInt(String.valueOf(btTo22)));
                //int setNum = lineNum/setSize;
                int sequence = Integer.parseInt(transformer.binaryToInt(String.valueOf(zeroToBt)));
                //行号%组数 = 组号
                int memoryLine = (sequence*SETS+SetNum)*1024;
                Memory.getMemory().write(transformer.intToBinary(""+memoryLine),1024,cacheLine.data);

            }
            int bitOfTag = 22-(10-(int)(Math.log(setSize)/Math.log(2)));
            for (int i = 0; i < bitOfTag; i++) {
                    cacheLine.tag[i] = '0';
            }
            //0-bitOfTag:
            int tagOfBlockNO = blockNO/(int)Math.pow(2,22-bitOfTag);
            Memory memory = Memory.getMemory();
            //由公式:块号%组数=组号,现已知组数和组号,和tag,求块号

            int blockNOInMem = tagOfBlockNO*SETS+SetNum;
            //取出对应块内的数据
            int ptr = blockNOInMem*1024;
            char[] read = memory.read(transformer.intToBinary(String.valueOf(ptr)), 1024);
            //更新cache中的数据以及tag
            System.arraycopy(read,0,cacheLine.getData(),0,1024);
            String s = transformer.intToBinary("" + tagOfBlockNO);
            String substring = s.substring(32-bitOfTag);
            char[] newTag = substring.toCharArray();
            //char[] newTag =pAddr.substring(0,22).toCharArray();
            Arrays.fill(cacheLine.getTag(),'0');
            System.arraycopy(newTag,0,cacheLine.getTag(),0,newTag.length);
            cacheLine.validBit = true;
            cacheLine.isEmpty = false;
            cacheLine.setTimeStamp(System.currentTimeMillis());
            cacheLine.visited=1;
            cacheLine.setTimeStampForLRU(System.currentTimeMillis());
            cacheLine.setWriteMessage(wm);
            return lineNum;
        }else{
            int lineNum = map(blockNO);
            CacheLine cacheLine = cache.get(lineNum);
            cacheLine.visited++;
            cacheLine.setTimeStampForLRU(System.currentTimeMillis());
            return lineNum;
        }
    }

    /**
     * 根据目标数据内存地址前22位的int表示，进行映射
     *
     * @param blockNO 数据在内存中的块号
     * @return 返回cache中所对应的行，-1表示未命中
     */
    private int map(int blockNO) {
        //可以用来表示组号的位数
        int bitOfSet = (int)(Math.log(SETS)/Math.log(2));
        //取出对应组号
        int setNum = blockNO%(int)Math.pow(2,bitOfSet);
        //在组内进行遍历,拿到cache对应行的数据
        for (int i = 0; i < setSize; i++) {
            CacheLine cacheLine = cache.get(setNum*setSize+i);
            //将cache行数据的tag与该tag进行比较
            //取出cache行的tag
            // (2^n)-路组关联映射: 22-(10-n) 位
            char[] tagCharsOfCache = cacheLine.tag;

            //取出blockNo中的tag
            // (2^n)-路组关联映射: 22-(10-n) 位
            //先找出是几路,即setSize
            int bitOfTag = 22-(10-(int)(Math.log(setSize)/Math.log(2)));
            int tagOfBlockNO = blockNO/(int)Math.pow(2,22-bitOfTag);
            char[] tagToBeCompared = new char[bitOfTag];
            for (int j = 0; j < bitOfTag ; j++) {
                tagToBeCompared[j] = tagCharsOfCache[j];
            }
            int tagOfCache = Integer.parseInt(transformer.binaryToInt(String.valueOf(tagToBeCompared)));
            if(tagOfCache==tagOfBlockNO&&!cacheLine.isEmpty&&cacheLine.validBit){
                //命中,返回对应行号
                return setNum*setSize+i;
            }
            if(!cacheLine.validBit){
                cacheLine.visited = 0;
            }
        }
        return -1;

    }

    /**
     * 更新cache
     *
     * @param rowNO 需要更新的cache行号
     * @param tag   待更新数据的Tag
     * @param input 待更新的数据
     */
    public void update(int rowNO, char[] tag, char[] input) {
        // TODO
    }

    /**
     * 从32位物理地址(22位块号 + 10位块内地址)获取目标数据在内存中对应的块号
     *
     * @param pAddr 32位物理地址
     * @return 数据在内存中的块号
     */
    private int getBlockNO(String pAddr) {
        return Integer.parseInt(transformer.binaryToInt("0" + pAddr.substring(0, 22)));
    }


    /**
     * 该方法会被用于测试，请勿修改
     * 使用策略模式，设置cache的替换策略
     *
     * @param replacementStrategy 替换策略
     */
    public void setReplacementStrategy(ReplacementStrategy replacementStrategy) {
        this.replacementStrategy = replacementStrategy;
    }

    /**
     * 该方法会被用于测试，请勿修改
     *
     * @param SETS 组数
     */
    public void setSETS(int SETS) {
        this.SETS = SETS;
    }

    /**
     * 该方法会被用于测试，请勿修改
     *
     * @param setSize 每组行数
     */
    public void setSetSize(int setSize) {
        this.setSize = setSize;
    }

    /**
     * 告知Cache某个连续地址范围内的数据发生了修改，缓存失效
     * 该方法仅在memory类中使用，请勿修改
     *
     * @param pAddr 发生变化的数据段的起始地址
     * @param len   数据段长度
     */
    public void invalid(String pAddr, int len) {
        int from = getBlockNO(pAddr);
        Transformer t = new Transformer();
        int to = getBlockNO(t.intToBinary(String.valueOf(Integer.parseInt(t.binaryToInt("0" + pAddr)) + len - 1)));

        for (int blockNO = from; blockNO <= to; blockNO++) {
            int rowNO = map(blockNO);
            if (rowNO != -1) {
                cache.get(rowNO).validBit = false;
                cache.get(rowNO).visited = 0;
            }
        }
    }

    /**
     * 清除Cache全部缓存
     * 该方法会被用于测试，请勿修改
     */
    public void clear() {
        for (CacheLine line : cache.clPool) {
            if (line != null) {
                line.validBit = false;
            }
        }
    }

    /**
     * 输入行号和对应的预期值，判断Cache当前状态是否符合预期
     * 这个方法仅用于测试，请勿修改
     *
     * @param lineNOs     行号
     * @param validations 有效值
     * @param tags        tag
     * @return 判断结果
     */
    public boolean checkStatus(int[] lineNOs, boolean[] validations, char[][] tags) {
        if (lineNOs.length != validations.length || validations.length != tags.length) {
            return false;
        }
        for (int i = 0; i < lineNOs.length; i++) {
            CacheLine line = cache.get(lineNOs[i]);
            if (line.validBit != validations[i]) {
                System.out.println(line.validBit);
                return false;
            }
            if (!Arrays.equals(line.getTag(), tags[i])) {
                System.out.println(line.getTag());
                return false;
            }
        }
        return true;
    }


    /**
     * 负责对CacheLine进行动态初始化
     */
    public static class CacheLinePool {

        public final CacheLine[] clPool;

        /**
         * @param lines Cache的总行数
         */
        CacheLinePool(int lines) {
            clPool = new CacheLine[lines];
        }

        public CacheLine get(int rowNO) {
            CacheLine l = clPool[rowNO];
            if (l == null) {
                clPool[rowNO] = new CacheLine();
                l = clPool[rowNO];
            }
            return l;
        }
    }


    /**
     * Cache行，每行长度为(1+22+{@link Cache#LINE_SIZE_B})
     */
    public static class CacheLine {
        public Long getTimeStampForLRU() {
            return timeStampForLRU;
        }

        public void setTimeStampForLRU(Long timeStampForLRU) {
            this.timeStampForLRU = timeStampForLRU;
        }

        boolean isEmpty = true;

        WriteMessage writeMessage;

        public WriteMessage getWriteMessage() {
            return writeMessage;
        }

        public void setWriteMessage(WriteMessage writeMessage) {
            this.writeMessage = writeMessage;
        }

        // 有效位，标记该条数据是否有效
        boolean validBit = false;

        // 脏位，标记该条数据是否被修改
        boolean dirty = false;

        public boolean isEmpty() {
            return isEmpty;
        }

        public void setEmpty(boolean empty) {
            isEmpty = empty;
        }

        public boolean isValidBit() {
            return validBit;
        }

        public void setValidBit(boolean validBit) {
            this.validBit = validBit;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public int getVisited() {
            return visited;
        }

        public void setVisited(int visited) {
            this.visited = visited;
        }

        public Long getTimeStamp() {
            return timeStamp;
        }

        public void setTimeStamp(Long timeStamp) {
            this.timeStamp = timeStamp;
        }

        public void setData(char[] data) {
            this.data = data;
        }

        // 用于LFU算法，记录该条cache使用次数
        int visited = 0;

        // 用于FIFO算法，记录该条数据时间戳
        Long timeStamp = 0L;
        Long timeStampForLRU = 0L;

        // 标记，占位长度为22位，有效长度取决于映射策略：
        // 直接映射: 12 位
        // 全关联映射: 22 位
        // (2^n)-路组关联映射: 22-(10-n) 位
        // 注意，tag在物理地址中用高位表示，如：直接映射(32位)=tag(12位)+行号(10位)+块内地址(10位)，
        // 那么对于值为0b1111的tag应该表示为0000000011110000000000，其中前12位为有效长度
        char[] tag = new char[22];

        public void setTag(char[] tag) {
            this.tag = tag;
        }

        // 数据
        char[] data = new char[LINE_SIZE_B];

        char[] getData() {
            return this.data;
        }

        char[] getTag() {
            return this.tag;
        }

    }

}
