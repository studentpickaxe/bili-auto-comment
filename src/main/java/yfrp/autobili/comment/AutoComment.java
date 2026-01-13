package yfrp.autobili.comment;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class AutoComment {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoComment.class);
    private static final int TIMEOUT = 15;

    private final RandomComment commentFormat;

    public AutoComment(RandomComment commentFormat) {
        this.commentFormat = commentFormat;
    }

    public boolean commentAt(WebDriver driver, String bvid)
            throws InterruptedException {

        String vidLink = "https://www.bilibili.com/video/" + bvid + "/";

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT));

        driver.get(vidLink);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("bili-comments")));

        scrollToCommentSection(driver);
        Thread.sleep(1500);

        String comment = commentFormat.generate();
        LOGGER.info("发送评论: {}", comment);

        return sendCommentWithSelenium(driver, wait, comment);
    }


    private boolean sendCommentWithSelenium(WebDriver driver,
                                            WebDriverWait wait,
                                            String commentText) {

        try {

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. 找到 bili-comments 组件
            WebElement biliComments = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("bili-comments")
            ));

            // 2. 通过 JavaScript 访问 Shadow DOM 并输入评论
            String script =
                    "const comments = arguments[0];" +
                    "const shadowRoot = comments.shadowRoot;" +
                    "const commentBox = shadowRoot.querySelector('bili-comment-box');" +
                    "const textarea = commentBox.shadowRoot.querySelector('bili-comment-rich-textarea');" +
                    "const editor = textarea.shadowRoot.querySelector('.brt-editor');" +
                    "editor.textContent = arguments[1];" +
                    "editor.dispatchEvent(new Event('input', { bubbles: true }));";

            js.executeScript(script, biliComments, commentText);
            Thread.sleep(1000);

            // 3. 点击发布按钮
            String clickScript =
                    "const comments = arguments[0];" +
                    "const shadowRoot = comments.shadowRoot;" +
                    "const commentBox = shadowRoot.querySelector('bili-comment-box');" +
                    "const publishBtn = commentBox.shadowRoot.querySelector('#pub button');" +
                    "publishBtn.click();";

            js.executeScript(clickScript, biliComments);

            return true;

        } catch (InterruptedException e) {
            LOGGER.error("评论失败", e);
        }
        return false;
    }

    /**
     * 滚动到评论区
     */
    private void scrollToCommentSection(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 查找评论区元素
            WebElement commentSection = driver.findElement(By.cssSelector("bili-comments"));

            // 平滑滚动到评论区
            js.executeScript(
                    "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                    commentSection
            );

            LOGGER.debug("已滚动到评论区");

        } catch (Exception e) {
            LOGGER.warn("滚动到评论区失败，尝试备选方案");
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.5);");
            } catch (Exception ex) {
                LOGGER.error("备选滚动方案也失败", ex);
            }
        }
    }

}
