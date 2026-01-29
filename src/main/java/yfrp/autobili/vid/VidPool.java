package yfrp.autobili.vid;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * 视频池类
 * <p>
 * 用于管理视频 BV 号的集合，支持添加、删除、查询、保存和加载等操作
 */
public class VidPool {

    // 视频池，存储视频 BV 号
    private final Set<String> vidPool = new HashSet<>();
    // 文件名
    private final String filename;

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
    public boolean removeIf(Predicate<String> filter) {
        return vidPool.removeIf(filter);
    }

    /**
     * 从视频池中随机获取一个视频 BV 号
     *
     * @return 视频 BV 号，如果视频池为空则返回 null
     */
    @Nullable
    public String getVidFromPool() {
        if (vidPool.isEmpty()) {
            return null;
        }

        return vidPool.stream()
                .skip(new Random().nextInt(vidPool.size()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取视频池大小
     *
     * @return 视频池中的视频数量
     */
    public int size() {
        return vidPool.size();
    }

    /**
     * 添加视频 BV 号到视频池
     *
     * @param bvids 视频 BV 号数组
     */
    public void add(String... bvids) {

        vidPool.addAll(Arrays.asList(bvids));
    }

    /**
     * 添加视频 BV 号集合到视频池
     *
     * @param bvids 视频 BV 号集合
     */
    public void addAll(Collection<String> bvids) {

        vidPool.addAll(bvids);
    }

    /**
     * 从视频池中删除指定的视频 BV 号
     *
     * @param bvids 视频 BV 号数组
     */
    public void remove(String... bvids) {

        for (var bv : bvids) {
            vidPool.removeIf(line -> line.equals(bv) || line.contains(bv));
        }
    }

    /**
     * 检查视频池是否为空
     *
     * @return 视频池是否为空
     */
    public boolean isEmpty() {
        return vidPool.isEmpty();
    }

    /**
     * 检查视频池中是否包含指定的视频 BV 号
     *
     * @param bvid 视频 BV 号
     * @return 是否包含该视频
     */
    public boolean hasVid(String bvid) {
        return vidPool.stream()
                .anyMatch(line -> line.equals(bvid) || line.contains(bvid));
    }

    /**
     * 保存视频池到文件
     */
    public void saveVideos() {

        try (FileWriter fileWriter = new FileWriter(filename);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

            for (String bv : vidPool) {
                bufferedWriter.write(bv);
                bufferedWriter.newLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从文件加载视频池
     *
     * @throws IOException IO 异常
     */
    public void loadVideos()
            throws IOException {

        Path path = Path.of(filename);
        if (Files.notExists(path)) {
            Files.createFile(path);
            return;
        }

        try (FileReader fileReader = new FileReader(filename);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            vidPool.clear();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                vidPool.add(line);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
