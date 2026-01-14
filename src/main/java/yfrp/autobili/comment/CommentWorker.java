package yfrp.autobili.comment;

import org.openqa.selenium.WebDriver;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class CommentWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentWorker.class);

    private static final Pattern commentedLine = Pattern.compile("^(BV[A-Za-z0-9]+);pubdate(\\d+)$");

    private static final AtomicInteger commentCount = new AtomicInteger(0);

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(10);
    private final Config config;

    private final VidPool toComment;
    private final VidPool commented;

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

        // 使用 offer 而不是无限等待,避免阻塞
        if (!queue.offer(bvid)) {
            LOGGER.warn("评论队列已满,跳过视频 {}", bvid);
        }
    }

    public void skip(String bvid, long pubdate) {
        afterComment(bvid, pubdate);
    }

    @Nonnull
    private pubdateCheckResult checkPubDate(@Nonnull String bvid) {

        boolean skip = false;
        long pubDate = -1;

        try {
            pubDate = BiliApi.getVidPubDate(bvid);

            // 2000-01-01 00:00:00
            if (config.getMinPubdate() >= 946656000 &&
                pubDate < config.getMinPubdate()) {

                LOGGER.info("视频 {} 发布日期 {} 早于设定的最早发布日期 {}，已跳过该视频",
                        bvid,
                        formatTimestamp(pubDate),
                        formatTimestamp(config.getMinPubdate())
                );
                skip = true;

            } else {
                var i = config.getMaxTimeSincePubdate();
                if (pubDate < Instant.now().getEpochSecond() - i) {
                    LOGGER.info("视频 {} 发布日期 {} 距今已超过设定的最大发布时间间隔 {}d {}h，已跳过该视频",
                            bvid,
                            formatTimestamp(pubDate),
                            i / 86400,
                            (i % 86400) / 3600
                    );
                    skip = true;
                }
            }

        } catch (IOException | InterruptedException e) {
            LOGGER.error("获取视频 {} 发布日期时出错", bvid, e);
        }

        return new pubdateCheckResult(skip, pubDate);
    }

    private record pubdateCheckResult(boolean skip, long pubdate) {
    }

    private static String formatTimestamp(long timestampSeconds) {
        return Instant.ofEpochSecond(timestampSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private void afterComment(String bvid, long pubdate) {
        toComment.remove(bvid);
        toComment.saveVideos();

        commented.add(bvid + ";pubdate" + pubdate);
        commented.saveVideos();

        LOGGER.info(
                "视频 {} 已处理完成 | 待评论: {}, 已评论: {}",
                bvid, toComment.size(), commented.size()
        );
    }

    private void clearCommented() {
        commented.removeIf(line -> {
            var matcher = commentedLine.matcher(line);

            if (!matcher.find()) {
                return true;
            }

            var bvid = matcher.group(1);
            var pubdate = Long.parseLong(matcher.group(2));
            var maxSincePub = config.getMaxTimeSincePubdate();
            if (pubdate < Instant.now().getEpochSecond() - maxSincePub) {
                LOGGER.info("已删除已评论视频记录 {}，视频发布距今已超过设定的最大发布时间间隔 {}d {}h",
                        bvid,
                        maxSincePub / 86400,
                        (maxSincePub % 86400) / 3600
                );
                return true;
            }

            return false;
        });
    }

    @Override
    public void run() {
        this.workerThread = Thread.currentThread();
        try {
            while (accepting || !queue.isEmpty()) {
                if (!accepting) {
                    break;
                }

                clearCommented();

                String bvid;
                try {
                    bvid = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    break;
                }

                if (bvid == null || bvid.isBlank()) {
                    continue;
                }

                var check = checkPubDate(bvid);
                if (check.skip()) {
                    skip(bvid, check.pubdate());
                    continue;
                }

                try {
                    LOGGER.info("开始评论视频 {}", bvid);
                    commenter.commentAt(driver, bvid);
                    afterComment(bvid, check.pubdate());
                    LOGGER.info("已评论 {} 个视频", commentCount.addAndGet(1));
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

    public void shutdown() {
        accepting = false;
        close();
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
