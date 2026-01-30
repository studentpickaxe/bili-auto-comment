package yfrp.autobili.vid;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 视频池类
 * <p>
 * 用于管理视频 BVID 的集合，支持添加、删除、查询、保存和加载等操作
 */
public class VidPool {

    // 视频池，存储视频 BVID 
    private final Map<String, String> vidMap = new ConcurrentHashMap<>();
    // 文件名
    private final String filename;
    // 用于保护文件 IO 操作的锁对象
    private final Object fileLock = new Object();

    /**
     * 构造函数
     * <p>
     * 创建视频池，并确保文件存在
     *
     * @param filename 文件名
     */
    public VidPool(String filename) {

        this.filename = filename;

        var path = Path.of(filename);

        if (Files.notExists(path)) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * 根据条件删除视频
     *
     * @param filter 过滤条件
     * @return 是否有元素被删除
     */
    public boolean removeIf(Function<Map.Entry<String, String>, Boolean> filter) {

        if (filter == null || this.isEmpty()) {
            return false;
        }

        return vidMap.entrySet()
                .removeIf(filter::apply);
    }

    /**
     * 从视频池中随机获取一个视频 BVID
     *
     * @return 视频 BVID，如果视频池为空则返回 null
     */
    @Nullable
    public String getVidFromPool() {

        if (this.isEmpty()) {
            return null;
        }

        return vidMap.entrySet()
                .stream()
                .skip(ThreadLocalRandom.current().nextInt(vidMap.size()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 获取视频池大小
     *
     * @return 视频池中的视频数量
     */
    public int size() {
        return vidMap.size();
    }

    /**
     * 添加视频信息映射到视频池
     *
     * @param bvid 视频 BVID
     * @param info 视频信息
     */
    public void put(String bvid, String info) {
        if (bvid != null) {
            vidMap.put(bvid, info);
        }
    }

    /**
     * 添加视频 BVID 到视频池
     *
     * @param bvid 视频 BVID
     */
    public void add(String bvid) {
        if (bvid != null) {
            vidMap.put(bvid, "");
        }
    }

    /**
     * 从视频池中删除指定的视频 BVID
     *
     * @param bvids 视频 BVID 数组
     */
    public void remove(String... bvids) {
        if (bvids == null || bvids.length == 0 || this.isEmpty()) {
            return;
        }

        for (String bvid : bvids) {
            vidMap.remove(bvid);
        }
    }

    /**
     * 检查视频池是否为空
     *
     * @return 视频池是否为空
     */
    public boolean isEmpty() {
        return vidMap.isEmpty();
    }

    /**
     * 检查视频池中是否包含指定的视频 BVID
     *
     * @param bvid 视频 BVID
     * @return 是否包含该视频
     */
    public boolean hasVid(String bvid) {
        if (bvid == null || this.isEmpty()) {
            return false;
        }

        return vidMap.containsKey(bvid);
    }

    /**
     * 保存视频池到文件
     */
    public void saveVideos() {

        synchronized (fileLock) {

            try (FileWriter fileWriter = new FileWriter(filename);
                 BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

                for (Map.Entry<String, String> entry : vidMap.entrySet()) {
                    bufferedWriter.write("v3|" + entry.getKey() + "|" + entry.getValue());
                    bufferedWriter.newLine();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 从文件加载视频池
     *
     * @param onUpgrade 升级函数
     *                  <p>
     *                  v3 格式: 索引|信息
     *                  <p>
     *                  例: BV0123456789|1767196800
     * @throws IOException IO 异常
     */
    public void loadVideos(@Nullable Function<String, String> onUpgrade)
            throws IOException {

        Path path = Path.of(filename);
        if (Files.notExists(path)) {
            Files.createFile(path);
            return;
        }

        synchronized (fileLock) {

            try {
                List<String> lines = Files.readAllLines(path);
                vidMap.clear();

                if (lines.isEmpty()) {
                    return;
                }

                lines.stream()
                        .filter(Objects::nonNull)
                        .filter(line -> !line.isBlank())
                        .map(line -> line.startsWith("v3|") ? line.substring(3) : onUpgrade == null ? line : onUpgrade.apply(line))
                        .forEach(this::addToVidMap);

            } catch (IOException e) {
                throw new IOException(e);
            }
        }
    }

    private void addToVidMap(String line) {
        var kv = line.split("\\|", 2);
        if (kv.length == 2) {
            vidMap.put(kv[0], kv[1]);
        } else if (kv.length == 1) {
            vidMap.put(kv[0], "");
        }
    }

}
