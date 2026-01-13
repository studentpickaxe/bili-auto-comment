package yfrp.autobili.browser;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;

public class Cookie {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cookie.class);

    public static final String COOKIE_FILE = "bilibili_cookies.txt";

    // 保存 Cookies 到文件
    public static void saveCookies(WebDriver driver) throws InterruptedException {

        driver.get("https://www.bilibili.com");
        Thread.sleep(500);

        try (FileWriter fileWriter = new FileWriter(COOKIE_FILE);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

            Set<org.openqa.selenium.Cookie> cookies = driver.manage().getCookies();
            for (org.openqa.selenium.Cookie cookie : cookies) {
                bufferedWriter.write(cookie.getName() + ";" +
                                     cookie.getValue() + ";" +
                                     cookie.getDomain() + ";" +
                                     cookie.getPath() + ";" +
                                     cookie.getExpiry() + ";" +
                                     cookie.isSecure());
                bufferedWriter.newLine();
            }
            LOGGER.info("Cookies 保存成功");

        } catch (IOException e) {
            LOGGER.error("保存 cookies 时出错", e);
        }

    }

    // 从文件加载 Cookies
    public static void loadCookies(WebDriver driver) {

        try (FileReader fileReader = new FileReader(COOKIE_FILE);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] token = line.split(";");

                org.openqa.selenium.Cookie.Builder cookieBuilder = new org.openqa.selenium.Cookie.Builder(token[0], token[1])
                        .domain(token[2])
                        .path(token[3])
                        .isSecure(Boolean.parseBoolean(token[5]));

                driver.manage().addCookie(cookieBuilder.build());
            }
            LOGGER.info("Cookies 加载成功");

        } catch (IOException e) {
            LOGGER.error("加载 cookies 时出错", e);
        }

    }

}
