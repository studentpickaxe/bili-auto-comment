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

    private RandomComment commentFormat = null;

    public void setCommentFormat(RandomComment commentFormat) {
        this.commentFormat = commentFormat;
    }

    public boolean commentAt(WebDriver driver, String bvid)
            throws InterruptedException {

        if (commentFormat == null){
            throw new IllegalStateException("Comment format not set");
        }

        String vidLink = "https://www.bilibili.com/video/" + bvid + "/";

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT));

        driver.get(vidLink);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("bili-comments")));

        scrollToCommentSection(driver);
        Thread.sleep(1000);

        String comment = commentFormat.generate();

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
                    """
                    const comments = arguments[0];
                    if (!comments || !comments.shadowRoot) return false;
                    
                    const shadowRoot = comments.shadowRoot;
                    const commentBox = shadowRoot.querySelector('bili-comment-box');
                    if (!commentBox || !commentBox.shadowRoot) return false;
                    
                    const textarea = commentBox.shadowRoot.querySelector('bili-comment-rich-textarea');
                    if (!textarea || !textarea.shadowRoot) return false;
                    
                    const editor = textarea.shadowRoot.querySelector('.brt-editor');
                    if (!editor) return false;
                    
                    editor.textContent = arguments[1];
                    editor.dispatchEvent(new Event('input', { bubbles: true }));
                    return true;
                    """;

            for (int i = 0; i < 3; i++) {
                var success = (boolean) js.executeScript(script, biliComments, commentText);
                if (success) {
                    LOGGER.info("已发送评论: {}", commentText);
                    break;
                }
                LOGGER.warn("第 {} 次评论失败！1s 后重试...", (i + 1));
                Thread.sleep(1000);
            }
            Thread.sleep(500);

            // 3. 点击发布按钮
            String clickScript =
                    """
                    const comments = arguments[0];
                    const shadowRoot = comments.shadowRoot;
                    const commentBox = shadowRoot.querySelector('bili-comment-box');
                    const publishBtn = commentBox.shadowRoot.querySelector('#pub button');
                    publishBtn.click();
                    """;

            js.executeScript(clickScript, biliComments);

            return true;

        } catch (Exception e) {
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

            // LOGGER.debug("已滚动到评论区");

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
