package yfrp.autobili.comment;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.ChromeOptionUtil;
import yfrp.autobili.config.Config;
import yfrp.autobili.vid.BiliApi;
import yfrp.autobili.vid.VidPool;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 评论工作线程类
 * <p>
 * 负责从待评论视频池中获取视频，检查视频发布时间，然后自动发送评论
 */
public class CommentWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentWorker.class);

    // 已处理视频记录的正则表达式模式（旧版本）
    private static final Pattern commentedLineV1 = Pattern.compile("^(BV[A-Za-z0-9]+);pubdate(\\d+)$");
    // 已处理视频记录的正则表达式模式（新版本）
    private static final Pattern commentedLineV2 = Pattern.compile("^v2;(BV[A-Za-z0-9]+);(\\d+)$");

    // 已处理视频计数器
    private static final AtomicInteger commentCount = new AtomicInteger(0);

    // 待评论视频队列
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(10);
    // 系统配置
    private final Config config;

    // 待评论视频池
    private final VidPool toComment;
    // 已评论视频池
    private final VidPool commented;
    // 上次清理时间
    private long lastClearTime = 0L;

    // 冷却结束时间
    private long cooldownEndTime = 0L;

    // 是否接受新任务
    private volatile boolean accepting = true;
    // 工作线程
    private volatile Thread workerThread;
    // WebDriver 实例
    private WebDriver driver;
    // 自动评论实例
    private final AutoComment commenter;

    /**
     * 构造函数
     *
     * @param config 系统配置
     * @param commenter 自动评论实例
     * @param toComment 待评论视频池
     * @param commented 已评论视频池
     */
    public CommentWorker(Config config,
                         AutoComment commenter,
                         VidPool toComment,
                         VidPool commented) {

        this.config = config;
        this.commenter = commenter;
        this.toComment = toComment;
        this.commented = commented;

        // 配置 Chrome 浏览器选项
        ChromeOptions options = new ChromeOptions();
        ChromeOptionUtil.makeLightweight(options);
        ChromeOptionUtil.setProfile(options, "comment");

        // 启动浏览器
        driver = new ChromeDriver(options);
        LOGGER.info("评论浏览器已启动");
        driver.get(config.getUrlHomepage());
    }

    /**
     * 检查视频发布时间
     * <p>
     * 根据配置决定是否跳过该视频
     *
     * @param bvid 视频 BV 号
     * @return 是否跳过该视频
     * @throws IOException IO 异常
     * @throws InterruptedException 线程中断异常
     */
    private boolean checkPubDate(@Nonnull String bvid)
            throws IOException,
                   InterruptedException {

        // 获取视频发布时间
        long pubDate = BiliApi.getVidPubDate(config.getUrlVideoApi(bvid));

        // 发布时间无效，可能是视频被删除
        if (pubDate < 0) {

            LOGGER.info("视频 {} 发布日期为负 ({})，可能是视频被删除",
                    bvid,
                    pubDate
            );

            removeFromToComment(bvid, false);
            return true;
        }

        // 发布时间距今已超过设定的最大时间间隔
        if (config.getAutoClearDelay() > 0 &&
            pubDate < now() - config.getAutoClearDelay()) {

            LOGGER.info("视频 {} 发布日期 {} 距今已超过设定的最大时间间隔 {}d {}h",
                    bvid,
                    formatTimestamp(pubDate),
                    config.getAutoClearDelay() / 86400,
                    (config.getAutoClearDelay() % 86400) / 3600
            );

            removeFromToComment(bvid, false);
            return true;
        }

        // 发布时间早于设定的最早发布日期
        if (config.getMinPubdate() > pubDate) {

            removeFromToComment(bvid, true);

            LOGGER.info("视频 {} 发布日期 {} 早于设定的最早发布日期 {}",
                    bvid,
                    formatTimestamp(pubDate),
                    formatTimestamp(config.getMinPubdate())
            );

            return true;
        }

        return false;
    }

    /**
     * 格式化时间戳为可读字符串
     *
     * @param timestampSeconds 时间戳（秒）
     * @return 格式化后的时间字符串
     */
    private static String formatTimestamp(long timestampSeconds) {
        return Instant.ofEpochSecond(timestampSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 从待评论视频池中移除视频
     *
     * @param bvid 视频 BV 号
     * @param addToCommented 是否添加到已评论视频池
     */
    private void removeFromToComment(String bvid,
                                     boolean addToCommented) {

        // 从待评论视频池中移除
        toComment.remove(bvid);
        toComment.saveVideos();

        // 添加到已评论视频池
        if (addToCommented) {
            commented.add("v2;" + bvid + ";" + now());
            commented.saveVideos();

            LOGGER.info(
                    "视频 {} 已处理完成 | 待评论: {}, 已处理: {}",
                    bvid,
                    toComment.size(),
                    commented.size()
            );
        } else {
            LOGGER.info(
                    "已跳过视频 {} | 待评论: {}, 已处理: {}",
                    bvid,
                    toComment.size(),
                    commented.size()
            );
        }

    }

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳（秒）
     */
    private long now() {
        return Instant.now().getEpochSecond();
    }

    /**
     * 清理已处理的视频记录
     * <p>
     * 删除超过设定时间的视频记录
     */
    private void clearCommented() {

        List<String> toAdd = new ArrayList<>();

        commented.removeIf(line -> {
            var matcher = commentedLineV2.matcher(line);

            // 匹配新版本格式
            if (!matcher.find()) {
                // v1 格式
                var matcherV1 = commentedLineV1.matcher(line);

                if (matcherV1.find()) {
                    var bvid = matcherV1.group(1);
                    var processTime = now();

                    toAdd.add("v2;" + bvid + ";" + processTime);
                }

                return true;
            }

            // 匹配新版本格式
            var bvid = matcher.group(1);
            var processTime = Long.parseLong(matcher.group(2));
            var autoClearDelay = config.getAutoClearDelay();
            if (processTime < now() - autoClearDelay) {
                LOGGER.info("已删除已处理的视频 {}，视频处理距今已超过设定的最大时间间隔 {}d {}h",
                        bvid,
                        autoClearDelay / 86400,
                        (autoClearDelay % 86400) / 3600
                );
                return true;
            }

            return false;
        });

        // 添加需要转换的旧版本记录
        commented.addAll(toAdd);
    }

    /**
     * 工作线程主循环
     * <p>
     * 负责从待评论视频池中获取视频，检查视频发布时间，然后自动发送评论
     */
    @Override
    public void run() {
        this.workerThread = Thread.currentThread();

        try {

            while (accepting) {
                if (!accepting) {
                    break;
                }

                // 每小时清理一次已处理的视频记录
                if (now() - lastClearTime > 3600) {
                    clearCommented();
                    lastClearTime = now();
                }

                // 评论处理
                if (cooldownEndTime < now()) {

                    // 将最多3个视频添加到队列
                    for (int i = 0; i < 3; i++) {
                        if (toComment.isEmpty()) {
                            break;
                        }

                        var bvid = toComment.getVidFromPool();
                        if (bvid == null) {
                            continue;
                        }

                        if (!queue.offer(bvid)) {
                            break;
                        }
                    }

                    comment();
                }

                // 等待下一次评论
                try {
                    if (accepting) {

                        var interval = config.getCommentInterval();
                        var t = new Random().nextInt(750, 1251);

                        for (int i = 0; i < interval; i++) {
                            Thread.sleep(t);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } finally {
            close();
            LOGGER.info("评论线程已结束，已处理 {} 个视频", commentCount.get());
        }
    }

    /**
     * 执行评论
     * <p>
     * 从队列中获取视频，检查是否已处理，然后发送评论
     */
    private void comment() {

        while (!queue.isEmpty()) {

            String bvid;
            try {
                bvid = queue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                continue;
            }

            if (bvid == null || bvid.isBlank()) {
                continue;
            }

            // 检查是否已处理
            if (commented.hasVid(bvid)) {

                commented.remove(bvid);
                commented.saveVideos();
                LOGGER.info("视频 {} 已被处理，跳过该视频", bvid);
                continue;
            }

            try {

                // 检查视频发布时间
                if (checkPubDate(bvid)) {
                    continue;
                }

            } catch (IOException | InterruptedException e) {
                LOGGER.error("获取视频 {} 发布日期时出错", bvid, e);
                continue;
            }

            try {
                // 重新加载配置
                config.loadConfig();
                // 发送评论
                if (commenter.comment(driver, bvid, config.getUrlVideo(bvid))) {
                    LOGGER.info("已处理 {} 个视频", commentCount.addAndGet(1));
                    removeFromToComment(bvid, true);
                }

            } catch (WebDriverException e) {
                LOGGER.warn("浏览器异常，尝试恢复: {}", e.getMessage());
                recoverDriver();

            } catch (CommentCooldownException e) {
                // 触发风控，进入冷却期
                var cd = config.getCommentCooldown();
                LOGGER.warn("触发风控，暂停自动评论 {}h {}min {}s",
                        cd / 3600,
                        (cd % 3600) / 60,
                        cd % 60
                );
                cooldownEndTime = now() + cd;

            } catch (Exception e) {
                if (accepting) {
                    LOGGER.error("评论视频 {} 时异常", bvid, e);
                }
            }

            return;
        }

    }

    /**
     * 恢复浏览器
     * <p>
     * 当浏览器出现异常时，关闭当前浏览器并重新启动
     */
    private synchronized void recoverDriver() {
        close();
        try {
            // 配置 Chrome 浏览器选项
            ChromeOptions options = new ChromeOptions();
            ChromeOptionUtil.makeLightweight(options);
            ChromeOptionUtil.setProfile(options, "comment");

            // 重新启动浏览器
            driver = new ChromeDriver(options);
            driver.get(config.getUrlHomepage());
            LOGGER.info("评论浏览器已恢复");
        } catch (Exception e) {
            LOGGER.error("评论浏览器恢复失败", e);
        }
    }

    /**
     * 关闭工作线程
     */
    public void shutdown() {
        accepting = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    /**
     * 关闭浏览器
     */
    public synchronized void close() {
        if (driver != null) {
            try {
                driver.quit();
                driver = null;
                LOGGER.info("评论浏览器已关闭");
            } catch (Exception _) {
            }
        }
    }

}
