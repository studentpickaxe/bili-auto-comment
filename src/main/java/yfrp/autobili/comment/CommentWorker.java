package yfrp.autobili.comment;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.ChromeOptionUtil;
import yfrp.autobili.config.Config;
import yfrp.autobili.vid.VidPool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CommentWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentWorker.class);

    private static final AtomicInteger commentCount = new AtomicInteger(0);

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Config config;

    private final VidPool toComment;
    private final VidPool commented;

    private volatile boolean accepting = true;
    private final WebDriver driver;
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
        driver.get("https://www.bilibili.com");
    }

    // 提交评论任务
    public void submit(String bvid) {
        if (!accepting) {
            LOGGER.debug("请求停止，不执行 {} 的评论任务", bvid);
            return;
        }
        queue.offer(bvid);
    }

    // 停止
    public void shutdown() {
        accepting = false;
    }

    public void skip(String bvid) {
        afterComment(bvid);
    }

    private void afterComment(String bvid) {
        toComment.remove(bvid);
        toComment.saveVideos();

        commented.add(bvid);
        commented.saveVideos();

        LOGGER.info(
                "视频 {} 已处理完成 | 待评论: {}, 已评论: {}",
                bvid, toComment.size(), commented.size()
        );
    }

    @Override
    public void run() {
        try {
            while (true) {

                // 不再接任务 且 队列空 → 正常退出
                if (!accepting && queue.isEmpty()) {
                    break;
                }

                String bvid;
                try {
                    bvid = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // stop 触发，直接检查退出条件
                    continue;
                }

                if (bvid == null) {
                    continue;
                }

                LOGGER.info("开始评论视频 {}", bvid);

                try {
                    // 执行评论
                    commenter.commentAt(driver, bvid);
                    afterComment(bvid);
                    LOGGER.info("已评论 {} 个视频", commentCount.addAndGet(1));

                } catch (InterruptedException e) {
                    LOGGER.info("评论线程收到停止信号，结束当前评论");
                    break;

                } catch (Exception e) {
                    LOGGER.error("评论视频 {} 时异常", bvid, e);
                }

                // 评论间隔（可被 stop 打断）
                try {
                    Thread.sleep(config.getCommentInterval() * 1000L);
                } catch (InterruptedException e) {
                    // stop 信号 → 直接退出
                    break;
                }
            }
        } finally {
            LOGGER.info("评论线程已结束");
            LOGGER.info("已评论 {} 个视频", commentCount.get());
        }
    }

    public void close() {
        if (driver != null) {
            try {
                driver.quit();
                LOGGER.info("评论浏览器已关闭");
            } catch (Exception e) {
                LOGGER.debug("关闭浏览器时异常", e);
            }
        }
    }

}
