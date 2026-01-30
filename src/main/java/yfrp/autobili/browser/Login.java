package yfrp.autobili.browser;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
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
     * 在新的无头模式浏览器中登录并保存 cookies
     *
     * @param homepageUrl 主页 URL
     */
    public static void loginHeadless(String homepageUrl) {
        ChromeUtil.withHeadlessDriver(driver -> {
            loginHeadless(driver, homepageUrl);
            ChromeUtil.saveCookies(driver, homepageUrl);
        });
    }

    /**
     * 在无头模式下登录并保存 cookies
     *
     * @param driver      WebDriver 实例
     * @param homepageUrl 主页 URL
     */
    public static void loginHeadless(WebDriver driver,
                                     String homepageUrl) {

        LOGGER.info("正在获取登录二维码");
        while (true) {
            try {

                driver.get(homepageUrl);
                WebDriverWait wait1 = new WebDriverWait(driver, Duration.ofSeconds(5));
                By loginBtnLocator = By.cssSelector(".header-login-entry");
                By qrCodeLocator = By.cssSelector(".login-scan-box img");

                // 点击登录按钮
                WebElement loginBtn = wait1.until(ExpectedConditions.elementToBeClickable(loginBtnLocator));
                loginBtn.click();

                // 提取二维码图片 Base64
                WebElement qrCodeImg = wait1.until(ExpectedConditions.visibilityOfElementLocated(qrCodeLocator));
                Thread.sleep(500);
                var qrBase64 = qrCodeImg.getAttribute("src");
                if (qrBase64 == null || !qrBase64.startsWith("data:image")) {
                    LOGGER.warn("未获取到有效的登录二维码，正在重试");
                    continue;
                }

                IO.println();
                IO.println("请完整复制下方的 Data URL，并在浏览器中打开以扫码登录：");
                IO.println(qrBase64);
                IO.println();

                // 检测登录成功
                WebDriverWait wait2 = new WebDriverWait(driver, Duration.ofSeconds(120));

                var isLoginBtnHidden = wait2.until(ExpectedConditions.invisibilityOfElementLocated(loginBtnLocator));
                if (isLoginBtnHidden) {
                    LOGGER.info("登录成功");
                    Thread.sleep(1000);
                    break;
                }

            } catch (TimeoutException | InterruptedException _) {
                LOGGER.info("正在重新获取二维码，旧二维码还有大约 60s 有效时间");
            }
        }

        // 保存 Cookies
        ChromeUtil.saveCookies(driver, homepageUrl);

    }

}
