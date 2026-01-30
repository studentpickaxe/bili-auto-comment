package yfrp.autobili.browser;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;

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
                "--disable-gpu",
                "--disable-dev-shm-usage"
        );

        return options;
    }

    /**
     * 保存 Cookies 到文件
     *
     * @param driver WebDriver 实例
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
    public static void loadCookies(WebDriver driver) {

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
