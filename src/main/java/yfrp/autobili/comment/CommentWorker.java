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

    // 提交评论任务
    public void submit(String bvid) {
        if (!accepting) {
            LOGGER.debug("请求停止，不执行 {} 的评论任务", bvid);
            return;
        }

        if (!queue.offer(bvid)) {
            LOGGER.warn("评论队列已满,跳过视频 {}", bvid);
        }
    }

    public void skip(String bvid) {
        afterComment(bvid);
    }

    @Nonnull
    private pubdateCheckResult checkPubDate(@Nonnull String bvid) {
        try {

            boolean skip = false;
            long pubDate = BiliApi.getVidPubDate(bvid);

            // 2000-01-01 00:00:00
            if (config.getMinPubdate() >= 946656000 &&
                pubDate < config.getMinPubdate()) {

                LOGGER.info("视频 {} 发布日期 {} 早于设定的最早发布日期 {}，已跳过该视频",
                        bvid,
                        formatTimestamp(pubDate),
                        formatTimestamp(config.getMinPubdate())
                );
                skip = true;

            }

            return new pubdateCheckResult(skip, pubDate);

        } catch (IOException | InterruptedException e) {
            LOGGER.error("获取视频 {} 发布日期时出错", bvid, e);
            return new pubdateCheckResult(true, -1);
        }
    }

    private record pubdateCheckResult(boolean skip, long pubdate) {
    }

    private static String formatTimestamp(long timestampSeconds) {
        return Instant.ofEpochSecond(timestampSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private void afterComment(String bvid) {
        toComment.remove(bvid);
        toComment.saveVideos();

        commented.add("v2;" + bvid + ";" + now());
        commented.saveVideos();

        LOGGER.info(
                "视频 {} 已处理完成 | 待评论: {}, 已评论: {}",
                bvid, toComment.size(), commented.size()
        );
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
                config.loadConfig();

                if (!accepting) {
                    break;
                }

                if (now() - lastClearTime > 3600) {
                    clearCommented();
                    lastClearTime = now();
                }

                String bvid;
                try {
                    bvid = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    break;
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

                if (checkPubDate(bvid).skip()) {
                    skip(bvid);
                    continue;
                }

                try {
                    LOGGER.info("开始评论视频 {}", bvid);
                    if (commenter.commentAt(driver, bvid)) {
                        afterComment(bvid);
                        LOGGER.info("已评论 {} 个视频", commentCount.addAndGet(1));
                    }
                } catch (WebDriverException e) {
                    LOGGER.warn("浏览器异常，尝试恢复: {}", e.getMessage());
                    recoverDriver();
                } catch (Exception e) {
                    if (accepting) {
                        LOGGER.error("评论视频 {} 时异常", bvid, e);
                    }
                }

                try {
                    if (accepting) {
                        Thread.sleep(config.getCommentInterval() * 1000L);
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
