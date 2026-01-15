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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchWorker.class);

    private final Config config;
    private final VidPool toComment;
    private final VidPool commented;

    private final List<String> keywords;
    private int keywordIndex = 0;

    private volatile boolean accepting = true;
    private volatile Thread workerThread;
    private WebDriver driver;

    public SearchWorker(Config config,
                        VidPool toComment,
                        VidPool commented) {

        this.config = config;
        this.toComment = toComment;
        this.commented = commented;
        this.keywords = config.getSearchKeywords();

        ChromeOptions options = new ChromeOptions();
        ChromeOptionUtil.makeLightweight(options);
        ChromeOptionUtil.setProfile(options, "search");

        this.driver = new ChromeDriver(options);
        LOGGER.info("搜索浏览器已启动");
        driver.get("https://www.bilibili.com");
    }

    @Override
    public void run() {
        this.workerThread = Thread.currentThread();

        while (accepting) {
            try {
                config.loadConfig();
                String keyword = nextKeyword();
                searchOnce(keyword);
                Thread.sleep(config.getSearchInterval() * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (WebDriverException e) {
                LOGGER.warn("搜索失败，尝试恢复浏览器: {}", e.getMessage());
                recoverDriver();
            } catch (Exception e) {
                if (accepting) {
                    LOGGER.error("搜索线程异常", e);
                }
            }
        }

        close();
    }

    private synchronized void recoverDriver() {
        close();
        try {
            ChromeOptions options = new ChromeOptions();
            ChromeOptionUtil.makeLightweight(options);
            ChromeOptionUtil.setProfile(options, "search");

            driver = new ChromeDriver(options);
            driver.get("https://www.bilibili.com");
            LOGGER.info("浏览器已恢复");
        } catch (Exception e) {
            LOGGER.error("浏览器恢复失败", e);
        }
    }

    private void searchOnce(String keyword)
            throws InterruptedException {

        LOGGER.info("开始搜索关键词 '{}'", keyword);

        driver.get("https://search.bilibili.com/all?keyword=" + keyword +
                   "&from_source=webtop_search&search_source=5&order=pubdate");

        Thread.sleep(3000);

        var bvids = extractBVIDs(driver);
        bvids.stream()
                .filter(bv -> !commented.hasVid(bv))
                .forEach(toComment::add);
        toComment.saveVideos();

        LOGGER.info("根据关键词 '{}' 搜索到 {} 个视频 | 待评论: {}, 已评论: {}",
                keyword,
                bvids.size(),
                toComment.size(),
                commented.size()
        );
    }

    // 提取 BVID 的方法
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

    private String nextKeyword() {
        if (keywords.isEmpty()) {
            throw new IllegalStateException("搜索关键词为空");
        }

        String keyword = keywords.get(keywordIndex);
        keywordIndex = (keywordIndex + 1) % keywords.size();
        return keyword;
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
                LOGGER.info("搜索浏览器已关闭");
            } catch (Exception _) {
            }
        }
    }

}
