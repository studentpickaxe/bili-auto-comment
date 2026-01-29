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
import java.util.List;

public class AutoComment {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoComment.class);
    private static final int TIMEOUT = 15;

    private RandomComment commentFormat = null;

    private static final String SCRIPT_SEND_COMMENT =
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
            editor.dispatchEvent(new Event('input', {bubbles: true}));
            return true;
            """;
    private static final String SCRIPT_CLICK_SEND_BUTTON =
            """
            const comments = arguments[0];
            const shadowRoot = comments.shadowRoot;
            const commentBox = shadowRoot.querySelector('bili-comment-box');
            const publishBtn = commentBox.shadowRoot.querySelector('#pub button');
            publishBtn.click();
            """;
    private static final String SCRIPT_CHECK_TOAST =
            """
            window.biliToasts = [];
            const observer = new MutationObserver((mutations) => {
              mutations.forEach((mutation) => {
                mutation.addedNodes.forEach((node) => {
                  // 检查节点本身或其子节点是否有 b-toast 类
                  if (node.nodeType === 1) {
                    let toastNode = node.classList.contains('b-toast') ? node : node.querySelector('.b-toast');
                    if (toastNode) {
                      const content = toastNode.innerText.trim();
                      if (content) {
                        window.biliToasts.push(content);
                        console.log('[监视器] 捕获到 toast: ' + content);
                      }
                    }
                  }
                });
              });
            });
            observer.observe(document.body, {childList: true, subtree: true});
            """;
    private static final String SCRIPT_GET_TOASTS =
            """
            var results = window.biliToasts;
            window.biliToasts = [];
            return results;
            """;

    public void setCommentFormat(RandomComment commentFormat) {
        this.commentFormat = commentFormat;
    }

    public boolean comment(WebDriver driver,
                           String bvid)
            throws InterruptedException,
                   CommentCooldownException {

        var comment = commentFormat.generate();
        LOGGER.info("开始在视频 {} 评论: {}", bvid, comment);

        if (commentFormat == null){
            throw new IllegalStateException("Comment format not set");
        }

        String vidLink = "https://www.bilibili.com/video/" + bvid + "/";

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT));

        driver.get(vidLink);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("bili-comments")));

        scrollToCommentSection(driver);
        Thread.sleep(1000);

        return sendComment(driver, wait, comment);
    }


    private boolean sendComment(WebDriver driver,
                                WebDriverWait wait,
                                String commentText)
            throws InterruptedException,
                   CommentCooldownException {

        ((JavascriptExecutor) driver).executeScript(SCRIPT_CHECK_TOAST);

        if (!sendCommentWithSelenium(driver, wait, commentText)) {
            return false;
        }

        checkCommentToast(driver);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void checkCommentToast(WebDriver driver)
            throws InterruptedException,
                   CommentCooldownException {

        for (int i = 0; i < 10; i++) {
            var toasts = (List<String>) ((JavascriptExecutor) driver).executeScript(SCRIPT_GET_TOASTS);

            for (var t : toasts) {

                if (t == null || t.isBlank()) {
                    continue;
                }

                if (t.equals("发送成功")) {
                    LOGGER.info("评论发送成功");
                    return;
                }

                if (t.contains("cd") || t.contains("CD")) {
                    throw new CommentCooldownException();
                }

                LOGGER.warn("评论发送失败: {}", t);
                return;

            }

            Thread.sleep(500);
        }

    }


    private boolean sendCommentWithSelenium(WebDriver driver,
                                            WebDriverWait wait,
                                            String commentText) {

        try {

            JavascriptExecutor js = (JavascriptExecutor) driver;

            WebElement biliComments = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("bili-comments")
            ));

            var i = 0;
            while (true) {
                var success = (boolean) js.executeScript(SCRIPT_SEND_COMMENT, biliComments, commentText);
                if (success) {
                    break;
                }
                if (i == 3) {
                    LOGGER.error("无法找到评论框，评论输入失败");
                    return false;
                }
                LOGGER.warn("第 {} 次输入评论失败！1s 后重试...", (++i));
                Thread.sleep(1000);
            }
            Thread.sleep(500);

            js.executeScript(SCRIPT_CLICK_SEND_BUTTON, biliComments);

            return true;

        } catch (Exception e) {
            LOGGER.error("尝试输入评论时出错", e);
        }
        return false;
    }

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
            LOGGER.error("滚动到评论区失败", e);
        }
    }

}
