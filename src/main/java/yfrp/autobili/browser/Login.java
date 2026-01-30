package yfrp.autobili.browser;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Bilibili 登录工具类
 * <p>
 * 提供手动登录功能，用于设置浏览器配置和用户登录状态
 */
public class Login {
    private static final Logger LOGGER = LoggerFactory.getLogger(Login.class);


    /**
     * 在无头模式下登录
     *
     * @param driver WebDriver 实例
     */
    public static void loginHeadless(WebDriver driver) {

        LOGGER.info("正在获取登录二维码");
        while (true) {

            driver.get("https://www.bilibili.com/");
            WebDriverWait wait1 = new WebDriverWait(driver, Duration.ofSeconds(5));
            By loginBtnLocator = By.cssSelector(".header-login-entry");
            By qrCodeLocator = By.cssSelector(".login-scan-box img");

            // 点击登录按钮
            WebElement loginBtn = wait1.until(ExpectedConditions.elementToBeClickable(loginBtnLocator));
            loginBtn.click();

            // 提取二维码图片 Base64
            WebElement qrCodeImg = wait1.until(ExpectedConditions.visibilityOfElementLocated(qrCodeLocator));
            var qrBase64 = qrCodeImg.getAttribute("src");

            IO.println();
            IO.println("请完整复制下方的 Data URL，并在浏览器中打开以扫码登录：");
            IO.println(qrBase64);
            IO.println();

            // 检测登录成功
            try {
                WebDriverWait wait2 = new WebDriverWait(driver, Duration.ofSeconds(120));

                var isLoginBtnHidden = wait2.until(ExpectedConditions.invisibilityOfElementLocated(loginBtnLocator));
                if (isLoginBtnHidden) {
                    LOGGER.info("登录成功");
                    Thread.sleep(1000);
                    return;
                }

            } catch (TimeoutException | InterruptedException _) {
                LOGGER.info("正在重新获取二维码，旧二维码还有大约 60s 有效时间");
            }

        }

    }

    /**
     * 执行登录流程
     * <p>
     * 打开浏览器，导航到 Bilibili 网站，等待用户手动登录和设置
     * <p>
     * 登录完成后按 Enter 键退出程序
     */
    public static void login() {
        // 创建 Chrome 浏览器选项
        var options = new ChromeOptions();
        // 设置用户配置文件，用于保存登录状态
        ChromeOptionUtil.setProfile(options, "comment");

        // 创建 Chrome 浏览器驱动
        WebDriver driver = new ChromeDriver(options);

        try {
            // 记录登录开始信息
            LOGGER.info("正在打开浏览器...");
            LOGGER.info("请登录并设置（如自动播放、分辨率等）");
            LOGGER.info("按 Enter 退出程序");

            // 导航到 Bilibili 主页
            driver.get("https://www.bilibili.com");
            // 等待用户输入，表示登录完成
            IO.readln();

            // 短暂延迟，确保操作完成
            Thread.sleep(1000);

            // 提示用户修改配置以正常运行程序
            LOGGER.info("将配置中的 'login.enable' 设为 NO 以正常运行程序");
        } catch (InterruptedException e) {
            // 捕获中断异常，记录错误并恢复中断状态
            LOGGER.error("登录时出错", e);
            Thread.currentThread().interrupt();
        } finally {
            // 无论成功还是失败，都要关闭浏览器
            driver.quit();
        }
    }

}
