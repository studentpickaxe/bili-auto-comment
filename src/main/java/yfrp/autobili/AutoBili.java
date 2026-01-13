package yfrp.autobili;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.ChromeOptionUtil;
import yfrp.autobili.comment.CommentWorker;
import yfrp.autobili.config.Config;
import yfrp.autobili.vid.BiliApi;
import yfrp.autobili.vid.SearchWorker;
import yfrp.autobili.vid.VidPool;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoBili {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoBili.class);

    private static final VidPool BVIDS_TO_COMMENT = new VidPool("bvids_to_comment.txt");
    private static final VidPool BVIDS_COMMENTED = new VidPool("bvids_commented.txt");

    private final ScheduledExecutorService scheduler;
    private final CountDownLatch shutdownLatch;

    private final CommentWorker commentWorker;
    private final Thread commentThread;
    private final SearchWorker searchWorker;
    private final Thread searchThread;

    private final Config config;

    public AutoBili(Config config) {
        this.config = config;

        this.scheduler = Executors.newScheduledThreadPool(2, this::createThread);
        this.shutdownLatch = new CountDownLatch(1);

        this.commentWorker = new CommentWorker(
                config,
                config.autoCommentInstance(),
                BVIDS_TO_COMMENT,
                BVIDS_COMMENTED
        );
        this.commentThread = new Thread(commentWorker, "Comment-Worker");

        this.searchWorker = config.isSearchEnabled()
                            ? new SearchWorker(config, BVIDS_TO_COMMENT, BVIDS_COMMENTED)
                            : null;
        this.searchThread = searchWorker != null
                            ? new Thread(searchWorker, "Search-Worker")
                            : null;

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
            LOGGER.info("已加载视频 | 待评论: {}, 已评论: {}",
                    BVIDS_TO_COMMENT.size(), BVIDS_COMMENTED.size());
        } catch (IOException e) {
            LOGGER.error("加载视频列表时出错", e);
            throw new RuntimeException("初始化失败", e);
        }

        this.commentThread.start();
        if (searchThread != null) {
            searchThread.start();
        }

    }

    private void scheduleJobs() {

        scheduler.scheduleAtFixedRate(
                new CommentTask(),
                0,
                config.getCommentInterval(),
                TimeUnit.SECONDS
        );

        LOGGER.info(
                "任务已启动: 搜索[间隔 {}s], 评论[间隔 {}s]",
                config.getSearchInterval(),
                config.getCommentInterval()
        );
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
        LOGGER.info("准备关闭服务...");

        // 1. 停止调度器（不再产生新任务）
        scheduler.shutdownNow();

        // 2. 停止搜索线程
        if (searchWorker != null) {
            searchWorker.shutdown();   // 不再接新搜索
        }
        if (searchThread != null) {
            searchThread.interrupt();  // 打断 sleep
        }

        // 3. 停止评论线程（优雅）
        if (commentWorker != null) {
            commentWorker.shutdown();  // 不再接新评论
        }
        if (commentThread != null) {
            commentThread.interrupt(); // 打断 poll / sleep
        }

        // 4. 等待搜索线程结束
        if (searchThread != null) {
            try {
                searchThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 5. 等待评论线程结束（等当前评论完成）
        if (commentThread != null) {
            try {
                commentThread.join();  // 不设 timeout，确保收尾
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 6. 安全关闭浏览器（线程已退出）
        try {
            if (searchWorker != null) {
                searchWorker.close();
            }
            if (commentWorker != null) {
                commentWorker.close();
            }
        } catch (Exception e) {
            LOGGER.debug("关闭浏览器时出现异常", e);
        }

        LOGGER.info("服务已完全关闭");
    }


    private void cleanup() {
        LOGGER.info("执行清理操作...");
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

            // 检查发布日期
            if (!checkPubDate(bvid)) {
                // 跳过视频
                commentWorker.skip(bvid);
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
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.error("获取视频 {} 发布日期时出错", bvid, e);
                return false;
            }
            return true;
        }
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
            LOGGER.info("请登录并进行网站设置（例如自动播放、分辨率）");
            LOGGER.info("请勿直接关闭浏览器！");
            LOGGER.info("按下 Enter 保存登录状态和网站设置");

            driver.get("https://www.bilibili.com");
            IO.readln();

            Thread.sleep(1000);

            LOGGER.info("已保存登陆状态和网站设置");
            LOGGER.info("可将配置中的 'login.enable' 设为 NO 以跳过此步骤");
        } catch (InterruptedException e) {
            LOGGER.error("登录时出错", e);
            Thread.currentThread().interrupt();
        } finally {
            driver.quit();
        }
    }
}
