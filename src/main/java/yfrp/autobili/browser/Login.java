package yfrp.autobili.browser;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Login {
    private static final Logger LOGGER = LoggerFactory.getLogger(Login.class);

    public static void login() {
        var options = new ChromeOptions();
        ChromeOptionUtil.setProfile(options, "comment");

        WebDriver driver = new ChromeDriver(options);

        try {
            LOGGER.info("正在打开浏览器...");
            LOGGER.info("请登录并设置（如自动播放、分辨率等）");
            LOGGER.info("按 Enter 退出程序");

            driver.get("https://www.bilibili.com");
            IO.readln();

            Thread.sleep(1000);

            LOGGER.info("将配置中的 'login.enable' 设为 NO 以正常运行程序");
        } catch (InterruptedException e) {
            LOGGER.error("登录时出错", e);
            Thread.currentThread().interrupt();
        } finally {
            driver.quit();
        }
    }

}
