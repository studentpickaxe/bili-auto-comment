package yfrp.autobili.vid;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yfrp.autobili.browser.ChromeOptionUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoSearch {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoSearch.class);

    public static final List<String> KEYWORDS = List.of("斩杀线", "殖", "公知", "NGO");

    public static void search(@Nonnull VidPool bvidsToComment,
                              @Nonnull VidPool bvidsCommented) {

        LOGGER.info("开始搜索视频");
        LOGGER.info("关键词: {}", String.join(", ", KEYWORDS));
        List<Thread> threads = new ArrayList<>();

        for (String k : KEYWORDS) {
            Thread thread = new Thread(() -> searchAndExtract(k, bvidsToComment, bvidsCommented), "Thread-" + k);
            threads.add(thread);
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                LOGGER.error("搜索视频时出错", e);
            }
        }

    }

    static void searchAndExtract(String keyword,
                                 @Nonnull VidPool bvidsToComment,
                                 @Nonnull VidPool bvidsCommented) {

        var options = new ChromeOptions();
        ChromeOptionUtil.makeLightweight(options);

        WebDriver driver = new ChromeDriver(options);

        try {
            // 打开 bilibili
            driver.get("https://www.bilibili.com");

            // 加载 cookies
            // Cookie.loadCookies(driver);
            // driver.navigate().refresh();
            // Thread.sleep(1000);

            // 搜索
            driver.get("https://search.bilibili.com/all?keyword=" + keyword +
                       "&from_source=webtop_search&spm_id_from=333.1007&search_source=5&order=pubdate");
            Thread.sleep(3000);

            // 记录 BVID
            var bvids = extractBVIDs(driver);
            LOGGER.info("根据关键词 '{}' 已搜索到 {} 个视频", keyword, bvids.size());

            bvids.stream()
                    .filter(bv -> !bvidsCommented.hasVid(bv))
                    .forEach(bvidsToComment::add);


        } catch (InterruptedException e) {
            LOGGER.error("搜索关键词 '{}' 时出错", keyword, e);
        } finally {
            driver.quit();
        }
    }

    // 提取 BVID 的方法
    public static List<String> extractBVIDs(WebDriver driver) {
        List<String> bvids = new ArrayList<>();
        Pattern pattern = Pattern.compile("BV[a-zA-Z0-9]+");

        try {
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

        } catch (Exception e) {
            LOGGER.error("提取 BVID 时出错", e);
        }

        // fuck bishi
        bvids.remove("BV1Xx411c7cH");

        return bvids;
    }

}
