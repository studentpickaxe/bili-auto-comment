package yfrp.autobili.browser;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

public class ChromeUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromeUtil.class);

    private static final String COOKIE_FILE = "cookies.txt";


    public static ChromeDriver getHeadlessDriver() {
        return new ChromeDriver(getHeadlessOptions());
    }

    private static ChromeOptions getHeadlessOptions() {

        ChromeOptions options = new ChromeOptions();

        options.addArguments(
                "--headless=new",
                "--no-sandbox",

                "--mute-audio",
                "--blink-settings=imagesEnabled=false",

                "--disable-gpu",
                "--disable-dev-shm-usage",

                "--disable-extensions",
                "--disable-plugins",
                "--disable-default-apps",

                "--disable-background-networking",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",

                "--disable-sync",
                "--disable-translate",
                "--disable-notifications",
                "--disable-infobars",

                "--silent"
        );

        return options;
    }

    public static void withHeadlessDriver(Consumer<WebDriver> action) {

        var driver = getHeadlessDriver();
        try {
            action.accept(driver);
        } finally {
            driver.quit();
        }
    }

    /**
     * 保存 Cookies 到文件
     *
     * @param driver      WebDriver 实例
     * @param homepageUrl 主页 URL
     */
    public static void saveCookies(WebDriver driver,
                                   String homepageUrl) {

        try (FileWriter fileWriter = new FileWriter(COOKIE_FILE);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

            driver.get(homepageUrl);
            Thread.sleep(500);

            Set<Cookie> cookies = driver.manage().getCookies();

            for (org.openqa.selenium.Cookie c : cookies) {
                bufferedWriter.write(
                        c.getName() + ";" +
                        c.getValue() + ";" +
                        c.getDomain() + ";" +
                        c.getPath() + ";" +
                        c.getExpiry() + ";" +
                        c.isSecure()
                );
                bufferedWriter.newLine();
            }
            LOGGER.info("Cookies 保存成功");

        } catch (InterruptedException | IOException e) {
            LOGGER.error("保存 cookies 时出错", e);
        }
    }

    /**
     * 从文件加载 Cookies 到浏览器
     *
     * @param driver WebDriver 实例
     */
    public static void loadCookies(WebDriver driver,
                                   String homepageUrl) {

        if (Files.notExists(Path.of(COOKIE_FILE))) {
            LOGGER.warn("Cookies 文件不存在，请登录");

            Login.loginHeadless(driver, homepageUrl);

            return;
        }

        try (FileReader fileReader = new FileReader(COOKIE_FILE);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] token = line.split(";");

                var cookieBuilder = new org.openqa.selenium.Cookie.Builder(token[0], token[1])
                        .domain(token[2])
                        .path(token[3])
                        .isSecure(Boolean.parseBoolean(token[5]));

                driver.manage().addCookie(cookieBuilder.build());
            }
            LOGGER.info("Cookies 加载成功");

        } catch (IOException | ArrayIndexOutOfBoundsException e) {
            LOGGER.error("加载 cookies 时出错", e);
        }
    }

}
