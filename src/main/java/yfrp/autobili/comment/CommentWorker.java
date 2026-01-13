package yfrp.autobili.comment;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.ChromeOptionUtil;
import yfrp.autobili.config.Config;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CommentWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentWorker.class);

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Config config;

    private volatile boolean running = true;
    private WebDriver driver;
    private AutoComment commenter;

    public CommentWorker(Config config, AutoComment commenter) {
        this.config = config;
        this.commenter = commenter;
    }

    // 启动 Chrome（只调用一次）
    private void initBrowser() {
        var options = new ChromeOptions();
        ChromeOptionUtil.makeLightweight(options);
        ChromeOptionUtil.setProfile(options, "comment");

        driver = new ChromeDriver(options);
        driver.get("https://www.bilibili.com");
        LOGGER.info("评论浏览器已启动");
    }

    // 提交评论任务
    public void submit(String bvid) {
        queue.offer(bvid);
    }

    // 停止
    public void shutdown() {
        running = false;
    }

    @Override
    public void run() {
        initBrowser();

        while (running) {
            try {
                String bvid = queue.poll(1, TimeUnit.SECONDS);
                if (bvid == null) {
                    continue;
                }

                LOGGER.info("开始评论视频 {}", bvid);
                boolean success = commenter.commentAt(driver, bvid);

                if (success) {
                    LOGGER.info("评论成功 {}", bvid);
                } else {
                    LOGGER.warn("评论失败 {}", bvid);
                }

                // ★ 评论最小间隔
                Thread.sleep(config.getCommentInterval() * 1000L);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("评论线程异常", e);
            }
        }

        if (driver != null) {
            driver.quit();
        }
        LOGGER.info("评论线程已退出");
    }
}
