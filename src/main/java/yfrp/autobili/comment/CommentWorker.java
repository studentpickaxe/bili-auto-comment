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

public class CommentWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentWorker.class);

    private static final Pattern commentedLineV1 = Pattern.compile("^(BV[A-Za-z0-9]+);pubdate(\\d+)$");
    private static final Pattern commentedLineV2 = Pattern.compile("^v2;(BV[A-Za-z0-9]+);(\\d+)$");

    private static final AtomicInteger commentCount = new AtomicInteger(0);

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(10);
    private final Config config;

    private final VidPool toComment;
    private final VidPool commented;
    private long lastClearTime = 0L;

    private long cooldownEndTime = 0L;

    private volatile boolean accepting = true;
    private volatile Thread workerThread;
    private WebDriver driver;
    private final AutoComment commenter;

    public CommentWorker(Config config,
                         AutoComment commenter,
                         VidPool toComment,
                         VidPool commented) {

        this.config = config;
        this.commenter = commenter;
        this.toComment = toComment;
        this.commented = commented;

        ChromeOptions options = new ChromeOptions();
        ChromeOptionUtil.makeLightweight(options);
        ChromeOptionUtil.setProfile(options, "comment");

        driver = new ChromeDriver(options);
        LOGGER.info("评论浏览器已启动");
        driver.get("https://www.bilibili.com");
    }

    private boolean checkPubDate(@Nonnull String bvid)
            throws IOException,
                   InterruptedException {

        long pubDate = BiliApi.getVidPubDate(bvid);

        if (config.getAutoClearDelay() > 0 &&
            pubDate < now() - config.getAutoClearDelay()) {

            removeFromToComment(bvid, false);

            LOGGER.info("视频 {} 发布日期 {} 距今已超过设定的最大时间间隔 {}d {}h",
                    bvid,
                    formatTimestamp(pubDate),
                    config.getAutoClearDelay() / 86400,
                    (config.getAutoClearDelay() % 86400) / 3600
            );

            return true;
        }

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

    private static String formatTimestamp(long timestampSeconds) {
        return Instant.ofEpochSecond(timestampSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private void removeFromToComment(String bvid,
                                     boolean addToCommented) {

        toComment.remove(bvid);
        toComment.saveVideos();

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

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private void clearCommented() {

        List<String> toAdd = new ArrayList<>();

        commented.removeIf(line -> {
            var matcher = commentedLineV2.matcher(line);

            if (!matcher.find()) {
                // v1
                var matcherV1 = commentedLineV1.matcher(line);

                if (matcherV1.find()) {
                    var bvid = matcherV1.group(1);
                    var processTime = now();

                    toAdd.add("v2;" + bvid + ";" + processTime);
                }

                return true;
            }

            var bvid = matcher.group(1);
            var processTime = Long.parseLong(matcher.group(2));
            var autoClearDelay = config.getAutoClearDelay();
            if (processTime < now() - autoClearDelay) {
                LOGGER.info("已删除已评论视频记录 {}，视频处理距今已超过设定的最大时间间隔 {}d {}h",
                        bvid,
                        autoClearDelay / 86400,
                        (autoClearDelay % 86400) / 3600
                );
                return true;
            }

            return false;
        });

        commented.addAll(toAdd);
    }

    @Override
    public void run() {
        this.workerThread = Thread.currentThread();

        try {

            while (accepting) {
                if (!accepting) {
                    break;
                }

                if (now() - lastClearTime > 3600) {
                    clearCommented();
                    lastClearTime = now();
                }

                // COMMENT
                if (cooldownEndTime < now()) {

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
            LOGGER.info("评论线程已结束，已评论 {} 个视频", commentCount.get());
        }
    }

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

            if (commented.hasVid(bvid)) {

                commented.remove(bvid);
                commented.saveVideos();
                LOGGER.info("视频 {} 已被处理，跳过该视频", bvid);
                continue;
            }

            try {

                if (checkPubDate(bvid)) {
                    continue;
                }

            } catch (IOException | InterruptedException e) {
                LOGGER.error("获取视频 {} 发布日期时出错", bvid, e);
                continue;
            }

            try {
                config.loadConfig();
                if (commenter.comment(driver, bvid)) {
                    LOGGER.info("已处理 {} 个视频", commentCount.addAndGet(1));
                    removeFromToComment(bvid, true);
                }

            } catch (WebDriverException e) {
                LOGGER.warn("浏览器异常，尝试恢复: {}", e.getMessage());
                recoverDriver();

            } catch (CommentCooldownException e) {
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

    private synchronized void recoverDriver() {
        close();
        try {
            ChromeOptions options = new ChromeOptions();
            ChromeOptionUtil.makeLightweight(options);
            ChromeOptionUtil.setProfile(options, "comment");

            driver = new ChromeDriver(options);
            driver.get("https://www.bilibili.com");
            LOGGER.info("评论浏览器已恢复");
        } catch (Exception e) {
            LOGGER.error("评论浏览器恢复失败", e);
        }
    }

    public void shutdown() {
        accepting = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

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
