package yfrp.autobili.browser;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

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
                IO.println("请完整复制以下 Data URL，并在浏览器中打开以扫码登录：");
                IO.println(addWhiteBorder(qrBase64, 420));
                IO.println("请完整复制以上 Data URL，并在浏览器中打开以扫码登录：");
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

    public static String addWhiteBorder(String imgBase64,
                                        int borderSize) {

        if (!imgBase64.startsWith("data:image/png;base64,")) {
            throw new IllegalArgumentException("仅支持 PNG 格式的 Base64 图片");
        }

        try {
            String base64 = imgBase64.substring("data:image/png;base64,".length());
            byte[] imgBytes = Base64.getDecoder().decode(base64);

            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imgBytes));

            // 计算尺寸
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            int newWidth = originalWidth + borderSize * 2;
            int newHeight = originalHeight + borderSize * 2;

            // 创建新画布
            BufferedImage newImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);

            // 填充白色背景
            var backgroundRgb = originalImage.getRGB(0, 0);
            drawRectangle(newImage, (0), (0), newWidth, borderSize, backgroundRgb);
            drawRectangle(newImage, (0), (newHeight - borderSize), newWidth, borderSize, backgroundRgb);
            drawRectangle(newImage, (0), borderSize, borderSize, originalHeight, backgroundRgb);
            drawRectangle(newImage, (newWidth - borderSize), borderSize, borderSize, originalHeight, backgroundRgb);

            var xMax = originalImage.getHeight();
            var yMax = originalImage.getWidth();
            for (int y = 0; y < yMax; y++) {
                var y2 = y + borderSize;
                for (int x = 0; x < xMax; x++) {
                    var x2 = x + borderSize;

                    // 复制原图像素
                    int rgb = originalImage.getRGB(x, y);
                    newImage.setRGB(x2, y2, rgb);
                }
            }

            // 转回 Base64
            var outputStream = new ByteArrayOutputStream();
            ImageIO.write(newImage, "png", outputStream);
            byte[] newImgBytes = outputStream.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(newImgBytes);

        } catch (IOException _) {
            return imgBase64;
        }
    }

    private static void drawRectangle(BufferedImage image,
                                      int x,
                                      int y,
                                      int width,
                                      int height,
                                      int rgb) {

        var xMax = x + width;
        var yMax = y + height;
        for (int j = y; j < yMax; j++) {
            for (int i = x; i < xMax; i++) {
                image.setRGB(i, j, rgb);
            }
        }
    }

}
