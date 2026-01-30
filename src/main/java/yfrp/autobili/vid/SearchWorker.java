package yfrp.autobili.vid;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.ChromeOptionUtil;
import yfrp.autobili.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索工作线程类
 * <p>
 * 负责根据关键词搜索视频，并将搜索到的视频添加到待评论视频池中
 */
public class SearchWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchWorker.class);

    // 系统配置
    private final Config config;
    // 待评论视频池
    private final VidPool toComment;
    // 已评论视频池
    private final VidPool commented;

    // 搜索关键词列表
    private final List<String> keywords;
    // 当前关键词索引
    private int keywordIndex = 0;

    // 是否接受新任务
    private volatile boolean accepting = true;
    // 工作线程
    private volatile Thread workerThread;
    // WebDriver 实例
    private WebDriver driver;

    /**
     * 构造函数
     *
     * @param config 系统配置
     * @param toComment 待评论视频池
     * @param commented 已评论视频池
     */
    public SearchWorker(Config config,
                        VidPool toComment,
                        VidPool commented) {

        this.config = config;
        this.toComment = toComment;
        this.commented = commented;
        this.keywords = config.getSearchKeywords();

        // 配置 Chrome 浏览器选项
        ChromeOptions options = new ChromeOptions();
        ChromeOptionUtil.makeLightweight(options);
        ChromeOptionUtil.setProfile(options, "search");

        // 启动浏览器
        this.driver = new ChromeDriver(options);
        LOGGER.info("搜索浏览器已启动");
        driver.get(config.getUrlHomepage());
    }

    /**
     * 工作线程主循环
     * <p>
     * 负责根据关键词搜索视频
     */
    @Override
    public void run() {
        this.workerThread = Thread.currentThread();

        while (accepting) {
            try {
                // 重新加载配置
                config.loadConfig();
                // 获取下一个关键词
                String keyword = nextKeyword();
                // 执行一次搜索
                searchOnce(keyword);

                // 等待下一次搜索
                var interval = config.getSearchInterval();
                var t = new Random().nextInt(750, 1251);
                for (int i = 0; i < interval; i++) {
                    Thread.sleep(t);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;

            } catch (WebDriverException e) {
                LOGGER.warn("搜索浏览器被关闭，尝试恢复: {}", e.getMessage());
                recoverDriver();

            } catch (Exception e) {
                if (accepting) {
                    LOGGER.error("搜索线程异常", e);
                }
            }
        }

        close();
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
            ChromeOptionUtil.setProfile(options, "search");

            // 重新启动浏览器
            driver = new ChromeDriver(options);
            driver.get(config.getUrlHomepage());
            LOGGER.info("浏览器已恢复");
        } catch (Exception e) {
            LOGGER.error("浏览器恢复失败", e);
        }
    }

    /**
     * 执行一次搜索
     * <p>
     * 根据关键词搜索视频，并将搜索到的视频添加到待评论视频池中
     *
     * @param keyword 搜索关键词
     * @throws InterruptedException 线程中断异常
     */
    private void searchOnce(String keyword)
            throws InterruptedException {

        LOGGER.info("开始搜索关键词 '{}'", keyword);

        // 构建搜索 URL
        driver.get(config.getUrlSearch(keyword));

        // 等待页面加载
        Thread.sleep(3000);

        // 提取搜索结果中的视频 BV 号
        var bvids = extractBVIDs(driver);
        // 过滤掉已评论的视频，并添加到待评论视频池
        bvids.stream()
                .filter(bv -> !commented.hasVid(bv))
                .forEach(toComment::add);
        // 保存待评论视频池
        toComment.saveVideos();

        LOGGER.info("根据关键词 '{}' 搜索到 {} 个视频 | 待评论: {}, 已处理: {}",
                keyword,
                bvids.size(),
                toComment.size(),
                commented.size()
        );
    }

    /**
     * 提取 BVID 的方法
     * <p>
     * 从搜索结果页面中提取所有视频的 BV 号
     *
     * @param driver WebDriver 实例
     * @return 视频 BV 号列表
     */
    public static List<String> extractBVIDs(WebDriver driver) {
        List<String> bvids = new ArrayList<>();
        Pattern pattern = Pattern.compile("BV[a-zA-Z0-9]+");

        // 查找所有视频卡片链接
        List<WebElement> videoLinks = driver.findElements(By.cssSelector("a[href*='/video/BV']"));

        for (WebElement link : videoLinks) {
            String href = link.getAttribute("href");
            if (href != null) {
                Matcher matcher = pattern.matcher(href);
                if (matcher.find()) {
                    String bvid = matcher.group();
                    if (!bvids.contains(bvid)) {  // 去重
                        bvids.add(bvid);
                    }
                }
            }
        }

        // fuck bishi
        bvids.remove("BV1Xx411c7cH");

        return bvids;
    }

    /**
     * 获取下一个关键词
     * <p>
     * 按顺序循环使用关键词列表中的关键词
     *
     * @return 下一个关键词
     */
    private String nextKeyword() {
        if (keywords.isEmpty()) {
            throw new IllegalStateException("搜索关键词为空");
        }

        String keyword = keywords.get(keywordIndex);
        keywordIndex = (keywordIndex + 1) % keywords.size();
        return keyword;
    }

    /**
     * 关闭工作线程
     */
    public void shutdown() {
        accepting = false;
        close();
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
                LOGGER.info("搜索浏览器已关闭");
            } catch (Exception _) {
            }
        }
    }

}
