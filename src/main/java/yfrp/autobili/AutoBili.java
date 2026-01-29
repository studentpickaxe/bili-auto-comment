package yfrp.autobili;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.Login;
import yfrp.autobili.comment.CommentWorker;
import yfrp.autobili.config.Config;
import yfrp.autobili.vid.SearchWorker;
import yfrp.autobili.vid.VidPool;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Bilibili 自动评论系统主类
 * <p>
 * 负责协调搜索、评论等核心功能模块的运行
 */
public class AutoBili {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoBili.class);

    // 待评论视频池，存储需要评论的视频ID
    private static final VidPool BVIDS_TO_COMMENT = new VidPool("bvids_to_comment.txt");
    // 已评论视频池，记录已经评论过的视频ID，避免重复评论
    private static final VidPool BVIDS_COMMENTED = new VidPool("bvids_commented.txt");

    // 定时任务调度器，用于执行周期性任务
    private final ScheduledExecutorService scheduler;
    // 用于优雅关闭的同步工具
    private final CountDownLatch shutdownLatch;

    // 评论工作线程相关
    private final CommentWorker commentWorker;
    private final Thread commentThread;
    // 搜索工作线程相关
    private final SearchWorker searchWorker;
    private final Thread searchThread;

    // 系统配置
    private final Config config;

    /**
     * 构造函数
     * <p>
     * 初始化系统所需的各种组件和线程
     *
     * @param config 系统配置
     */
    public AutoBili(Config config) {
        this.config = config;

        // 创建定时任务调度器，使用自定义线程工厂
        this.scheduler = Executors.newScheduledThreadPool(2, this::createThread);
        // 初始化关闭同步器
        this.shutdownLatch = new CountDownLatch(1);

        // 初始化评论工作器
        this.commentWorker = new CommentWorker(
                config,
                config.autoCommentInstance(),
                BVIDS_TO_COMMENT,
                BVIDS_COMMENTED
        );
        this.commentThread = new Thread(commentWorker, "Comment-Worker");

        // 根据配置决定是否启用搜索功能
        this.searchWorker = config.isSearchEnabled()
                            ? new SearchWorker(config, BVIDS_TO_COMMENT, BVIDS_COMMENTED)
                            : null;
        this.searchThread = searchWorker != null
                            ? new Thread(searchWorker, "Search-Worker")
                            : null;

        // 注册 JVM 关闭钩子，确保程序优雅退出
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> LOGGER.info("\n\n"),
                "Shutdown-Hook"
        ));
    }

    /**
     * 创建线程的工厂方法
     * <p>
     * 设置线程为非守护线程
     *
     * @param r 线程要执行的任务
     * @return 配置好的线程
     */
    private Thread createThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(false);
        return t;
    }

    /**
     * 程序入口点
     * <p>
     * 根据配置决定是登录还是直接启动主程序
     */
    static void main() {
        var config = Config.getInstance();

        if (config.isLoginEnabled()) {
            // 如果启用了登录功能，先执行登录
            Login.login();
        } else {
            // 否则直接启动主程序
            new AutoBili(Config.getInstance()).start();
        }
    }

    /**
     * 启动主程序
     * <p>
     * 包括初始化、监听停止命令、等待关闭等步骤
     */
    public void start() {

        // 记录启动信息
        LOGGER.info(
                "任务已启动: 搜索[间隔 {}s], 评论[间隔 {}s]",
                config.getSearchInterval(),
                config.getCommentInterval()
        );
        LOGGER.info("配置的部分参数可修改后自动重载");

        try {
            // 执行初始化
            initialize();
            // 监听停止命令
            listenForStopCommand();
            // 等待关闭信号
            awaitShutdown();

        } catch (Exception e) {
            // 捕获并记录运行时异常
            LOGGER.error("运行过程中发生错误", e);
        } finally {
            // 无论成功还是失败，都要执行关闭操作
            shutdown();
        }

    }

    /**
     * 初始化系统
     * <p>
     * 加载视频列表，启动工作线程
     */
    private void initialize() {
        LOGGER.info("正在初始化...");

        try {
            // 从文件加载待评论和已评论的视频列表
            BVIDS_TO_COMMENT.loadVideos();
            BVIDS_COMMENTED.loadVideos();
            LOGGER.info("已加载视频 | 待评论: {}, 已处理: {}",
                    BVIDS_TO_COMMENT.size(), BVIDS_COMMENTED.size());
        } catch (IOException e) {
            // 加载失败时记录错误并抛出运行时异常
            LOGGER.error("加载视频列表时出错", e);
            throw new RuntimeException("初始化失败", e);
        }

        // 启动评论工作线程
        this.commentThread.start();
        // 如果启用了搜索功能，也启动搜索工作线程
        if (searchThread != null) {
            searchThread.start();
        }

    }

    /**
     * 监听控制台输入，等待用户输入停止命令
     */
    private void listenForStopCommand() {
        Thread inputThread = new Thread(() -> {
            LOGGER.info("输入 'stop' 停止程序");
            while (true) {
                // 读取用户输入
                String line = IO.readln();
                if ("stop".equalsIgnoreCase(line)) {
                    // 收到停止命令，记录日志并触发关闭
                    LOGGER.info("正在终止运行...");
                    shutdownLatch.countDown();
                    break;
                }
            }
        }, "Console-Listener");
        // 设置为守护线程，不阻止JVM退出
        inputThread.setDaemon(true);
        inputThread.start();
    }

    /**
     * 等待关闭信号
     * <p>
     * 阻塞当前线程直到收到关闭信号
     */
    private void awaitShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            // 如果等待被中断，恢复中断状态并记录警告
            Thread.currentThread().interrupt();
            LOGGER.warn("等待关闭信号时被中断", e);
        }
    }

    /**
     * 关闭系统
     * <p>
     * 停止所有服务和线程，确保资源正确释放
     */
    private void shutdown() {
        LOGGER.info("准备关闭服务...");

        // 停止定时任务调度器
        scheduler.shutdownNow();

        // 关闭搜索工作器
        if (searchWorker != null) {
            searchWorker.shutdown();
        }
        // 关闭评论工作器
        if (commentWorker != null) {
            commentWorker.shutdown();
        }

        // 等待工作线程结束，最多等待2秒
        try {
            if (searchThread != null) {
                searchThread.join(2000);
            }
            if (commentThread != null) {
                commentThread.join(2000);
            }
        } catch (InterruptedException e) {
            // 如果等待被中断，恢复中断状态
            Thread.currentThread().interrupt();
        }

        LOGGER.info("服务已关闭");
    }
}
