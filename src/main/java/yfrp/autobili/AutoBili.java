package yfrp.autobili;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.ChromeOptionUtil;
import yfrp.autobili.comment.CommentWorker;
import yfrp.autobili.config.Config;
import yfrp.autobili.vid.AutoSearch;
import yfrp.autobili.vid.BiliApi;
import yfrp.autobili.vid.VidPool;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoBili {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoBili.class);

    private static final VidPool BVIDS_TO_COMMENT = new VidPool("bvids_to_comment.txt");
    private static final VidPool BVIDS_COMMENTED = new VidPool("bvids_commented.txt");
    private static final AtomicInteger COMMENT_COUNT = new AtomicInteger(0);

    private final ScheduledExecutorService scheduler;
    private final ExecutorService commentExecutor;
    private final CountDownLatch shutdownLatch;

    private final CommentWorker commentWorker;
    private final Thread commentThread;

    private final Config config;

    public AutoBili(Config config) {
        this.config = config;

        this.scheduler = Executors.newScheduledThreadPool(2, this::createThread);
        this.commentExecutor = Executors.newCachedThreadPool(this::createThread);
        this.shutdownLatch = new CountDownLatch(1);

        this.commentWorker = new CommentWorker(config, config.autoCommentInstance());
        this.commentThread = new Thread(commentWorker, "Comment-Worker");

        // 注册 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup, "Shutdown-Hook"));
    }

    private Thread createThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(false);
        return t;
    }

    static void main() {
        var config = Config.getInstance();

        if (config.isLoginEnabled()) {
            login();
        } else {
            new AutoBili(Config.getInstance()).start();
        }
    }

    public void start() {
        try {
            initialize();
            scheduleJobs();
            listenForStopCommand();
            awaitShutdown();
        } catch (Exception e) {
            LOGGER.error("运行过程中发生错误", e);
        } finally {
            shutdown();
        }
    }

    private void initialize() {
        LOGGER.info("正在初始化...");

        try {
            BVIDS_TO_COMMENT.loadVideos();
            BVIDS_COMMENTED.loadVideos();
            LOGGER.info("已加载待评论视频 {} 个，已评论视频 {} 个",
                    BVIDS_TO_COMMENT.size(), BVIDS_COMMENTED.size());
        } catch (IOException e) {
            LOGGER.error("加载视频列表时出错", e);
            throw new RuntimeException("初始化失败", e);
        }

        this.commentThread.start();
    }

    private void scheduleJobs() {
        // 搜索
        if (config.isSearchEnabled()) {
            scheduler.scheduleAtFixedRate(
                    new SearchTask(),
                    0,
                    config.getSearchInterval(),
                    TimeUnit.SECONDS
            );
        }

        // 评论
        scheduler.scheduleAtFixedRate(
                new CommentTask(),
                0,
                config.getCommentInterval(),
                TimeUnit.SECONDS
        );

        LOGGER.info("已启动定时任务: 搜索[间隔 {}s], 评论[间隔 {}s]", config.getSearchInterval(), config.getCommentInterval());
    }

    private void listenForStopCommand() {
        Thread inputThread = new Thread(() -> {
            LOGGER.info("输入 'stop' 停止程序");
            while (true) {
                String line = IO.readln();
                if ("stop".equalsIgnoreCase(line)) {
                    LOGGER.info("正在终止运行...");
                    shutdownLatch.countDown();
                    break;
                }
            }
        }, "Console-Listener");
        inputThread.setDaemon(true);
        inputThread.start();
    }

    private void awaitShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("等待关闭信号时被中断", e);
        }
    }

    private void shutdown() {
        LOGGER.info("开始关闭服务...");

        // 停止调度器，不再接受新任务
        scheduler.shutdown();

        try {
            // 等待当前任务完成
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 关闭评论执行器
        commentExecutor.shutdown();
        try {
            if (!commentExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                commentExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            commentExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("已结束运行");
        LOGGER.info("总计评论 {} 个视频", COMMENT_COUNT.get());
        LOGGER.info("服务已关闭");
    }

    private void cleanup() {
        LOGGER.info("执行清理操作...");
    }

    // 搜索任务
    private static class SearchTask implements Runnable {
        @Override
        public void run() {
            try {
                LOGGER.debug("开始执行视频搜索任务");
                AutoSearch.search(BVIDS_TO_COMMENT, BVIDS_COMMENTED);
            } catch (Exception e) {
                LOGGER.error("搜索视频时出错", e);
            }
        }
    }

    // 评论任务
    private class CommentTask implements Runnable {
        @Override
        public void run() {
            try {
                processComment();
            } catch (Exception e) {
                LOGGER.error("评论任务执行失败", e);
            }
        }

        private void processComment() {
            String bvid = BVIDS_TO_COMMENT.getVidFromPool();

            if (bvid == null) {
                LOGGER.warn("未获取到可评论的视频");
                return;
            }

            LOGGER.info("开始评论视频 {}", bvid);

            // 检查发布日期
            if (!checkPubDate(bvid)) {
                return;
            }

            // 评论
            commentWorker.submit(bvid);
        }

        private boolean checkPubDate(String bvid) {
            // 2000-01-01 00:00:00
            if (config.getMinPubdate() < 946656000) {
                return true;
            }

            try {
                long pubDate = BiliApi.getVidPubDate(bvid);
                if (pubDate < config.getMinPubdate()) {
                    LOGGER.info("视频 {} 发布日期 {} 早于设定的最早发布日期 {}，已跳过该视频",
                            bvid, formatTimestamp(pubDate), formatTimestamp(config.getMinPubdate()));
                    afterComment(bvid);
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.error("获取视频 {} 发布日期时出错", bvid, e);
                return false;
            }
            return true;
        }

        // private void sendComment(String bvid) {
        //     try {
        //         if (config.autoCommentInstance().commentAt(bvid)) {
        //             int count = COMMENT_COUNT.incrementAndGet();
        //             LOGGER.info("成功评论视频 {}，当前进度: {}", bvid, count);
        //             afterComment(bvid);
        //         } else {
        //             LOGGER.warn("评论视频 {} 失败", bvid);
        //         }
        //     } catch (Exception e) {
        //         LOGGER.error("发送评论时出错: {}", bvid, e);
        //     }
        // }
    }

    private static void afterComment(@Nonnull String bvid) {
        BVIDS_TO_COMMENT.remove(bvid);
        BVIDS_TO_COMMENT.saveVideos();
        BVIDS_COMMENTED.add(bvid);
        BVIDS_COMMENTED.saveVideos();

        LOGGER.info("已保存视频列表 - 待评论: {}, 已评论: {}",
                BVIDS_TO_COMMENT.size(), BVIDS_COMMENTED.size());
    }

    private static String formatTimestamp(long timestampSeconds) {
        return Instant.ofEpochSecond(timestampSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static void login() {
        var options = new ChromeOptions();
        ChromeOptionUtil.setProfile(options, "comment");

        WebDriver driver = new ChromeDriver(options);

        try {
            LOGGER.info("正在打开浏览器...");
            LOGGER.info("请在登录完成后按 Enter 以保存登录状态和网站设置");
            LOGGER.info("请勿直接关闭浏览器！");

            driver.get("https://www.bilibili.com");
            IO.readln();

            Thread.sleep(1000);

            LOGGER.info("登录状态已保存");
        } catch (InterruptedException e) {
            LOGGER.error("登录时出错", e);
            Thread.currentThread().interrupt();
        } finally {
            driver.quit();
        }
    }
}
