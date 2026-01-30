package yfrp.autobili;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.comment.CommentWorker;
import yfrp.autobili.config.Config;
import yfrp.autobili.vid.SearchWorker;
import yfrp.autobili.vid.VidPool;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // 标志服务是否正在关闭
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

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
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Shutdown-Hook"));
    }

    /**
     * 程序入口点
     * <p>
     * 根据配置决定是登录还是直接启动主程序
     */
    static void main() {
        var config = Config.getInstance();

        new AutoBili(config).start();
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

        // 执行初始化
        initialize();

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
            BVIDS_TO_COMMENT.loadVideos(null);
            BVIDS_COMMENTED.loadVideos(CommentWorker::upgradeCommentedLine);
            LOGGER.info("已加载视频列表 | 待评论: {}, 已处理: {}",
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
     * 关闭系统
     * <p>
     * 停止所有服务和线程，确保资源正确释放
     */
    private void shutdown() {

        if (!isShuttingDown.compareAndSet(false, true)) {
            // 已经在关闭过程中，直接返回
            return;
        }

        LOGGER.info("准备关闭服务...");

        // 关闭搜索工作器
        if (searchWorker != null) {
            searchWorker.shutdown();
        }
        // 关闭评论工作器
        if (commentWorker != null) {
            commentWorker.shutdown();
        }

        // 等待工作线程结束，最多等待 2s
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

        LOGGER.info("服务已关闭\n\n");
    }
}
