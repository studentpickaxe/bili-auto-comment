package yfrp.autobili.vid;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.ChromeOptionUtil;
import yfrp.autobili.config.Config;

public class SearchWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchWorker.class);

    private final Config config;
    private final VidPool toComment;
    private final VidPool commented;

    private final String[] keywords;
    private int keywordIndex = 0;

    private volatile boolean accepting = true;

    private WebDriver driver;

    public SearchWorker(Config config,
                        VidPool toComment,
                        VidPool commented) {
        this.config = config;
        this.toComment = toComment;
        this.commented = commented;
        this.keywords = config.getSearchKeywords();
    }

    @Override
    public void run() {
        try {
            initBrowser();

            while (accepting) {
                String keyword = nextKeyword();

                try {
                    searchOnce(keyword);
                    Thread.sleep(config.getSearchInterval() * 1000L);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            LOGGER.info("搜索线程已结束");
        }
    }

    private void initBrowser() {
        ChromeOptions options = new ChromeOptions();
        ChromeOptionUtil.makeLightweight(options);
        this.driver = new ChromeDriver(options);
        LOGGER.info("搜索浏览器已启动");
    }

    private void searchOnce(String keyword)
            throws InterruptedException {

        LOGGER.info("开始搜索关键词 '{}'", keyword);

        driver.get("https://search.bilibili.com/all?keyword=" + keyword +
                   "&from_source=webtop_search&search_source=5&order=pubdate");

        Thread.sleep(3000);

        var bvids = AutoSearch.extractBVIDs(driver);
        LOGGER.info("根据关键词 '{}' 搜索到 {} 个视频", keyword, bvids.size());

        bvids.stream()
                .filter(bv -> !commented.hasVid(bv))
                .forEach(toComment::add);

        // 保存
        toComment.saveVideos();
        LOGGER.info(
                "已保存视频列表 | 待评论: {}, 已评论: {}",
                toComment.size(), commented.size()
        );
    }

    private String nextKeyword() {
        if (keywords.length == 0) {
            throw new IllegalStateException("搜索关键词为空");
        }

        String keyword = keywords[keywordIndex];
        keywordIndex = (keywordIndex + 1) % keywords.length;
        return keyword;
    }

    public void shutdown() {
        accepting = false;
    }

    public void close() {
        if (driver != null) {
            try {
                driver.quit();
                LOGGER.info("搜索浏览器已关闭");
            } catch (Exception e) {
                LOGGER.debug("关闭搜索浏览器异常（已忽略）", e);
            }
        }
    }
}
