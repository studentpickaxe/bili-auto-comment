package yfrp.autobili.util;

import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Bilibili 登录工具类
 * <p>
 * 提供手动登录功能，用于设置浏览器配置和用户登录状态
 */
public class Login {
    private static final Logger LOGGER = LoggerFactory.getLogger(Login.class);

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

                printLoginQrCode(qrBase64);

                // 检测登录成功
                WebDriverWait wait2 = new WebDriverWait(driver, Duration.ofSeconds(120));

                var isLoginBtnHidden = wait2.until(ExpectedConditions.invisibilityOfElementLocated(loginBtnLocator));
                if (isLoginBtnHidden) {
                    LOGGER.info("登录成功");
                    Thread.sleep(1000);
                    break;
                }

            } catch (TimeoutException _) {
                LOGGER.info("正在重新获取二维码，旧二维码还有大约 60s 有效时间");
            } catch (InterruptedException e) {
                ChromeUtil.quitDriver(driver);
            }
        }

        // 保存 Cookies
        ChromeUtil.saveCookies(driver, homepageUrl);

    }

    private static void printLoginQrCode(String qrBase64) {

        try {
            LOGGER.info("{}\n{}\n{}",
                    "—".repeat(51),
                    QrCodeUtil.addWhiteBorder(qrBase64, 420),
                    String.join("\n", QrCodeUtil.extractQrCode(QrCodeUtil.base64ToImage(qrBase64)))
            );
            LOGGER.info("请扫描上方二维码登录；若无法扫描，请完整复制二维码上方的 Data URL，在浏览器中打开以扫码登录");
        } catch (NotFoundException | IOException | FormatException e) {
            LOGGER.warn("无法解析二维码，请完整复制二维码上方的 Data URL，在浏览器中打开以扫码登录");
        }
        LOGGER.info("—".repeat(51));

    }

}
