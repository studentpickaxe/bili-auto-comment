package yfrp.autobili.comment;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import yfrp.autobili.Main;

import java.time.Duration;

public class AutoComment {
    static void main() throws InterruptedException {

        String winCFormat = """
                            :wins;:stickers;
                            :stickers;:wins;
                            {{{{{{
                            sticker={[星星眼]'[打call]'[滑稽]'[妙啊]'[嗑瓜子]'[呲牙]'[大笑]'[偷笑]'[鼓掌]'[嘘声]'[捂眼]'[惊喜]'[哈欠]'[抓狂]}
                            stickers={:sticker;':sticker;:sticker;}
                            wins={:win;':won;}
                            win={赢'🥇赢'赢🥇'✌赢}
                            won={赢了'✌赢了'赢了✌'🥇赢了}
                            """;

        String testLink = "https://www.bilibili.com/video/BV1pTr8BsEg2/";

        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(Main.timeout));

        RandomComment rcWin = new RandomComment(winCFormat);

        try {

            driver.get("https://www.bilibili.com");
            Main.loadCookies(driver, Main.COOKIE_FILE);
            driver.navigate().refresh();
            Thread.sleep(1000);

            // 访问视频页面
            System.out.println("正在访问视频页面...");
            driver.get(testLink);
            Thread.sleep(3000);

            // 滚动到评论区
            System.out.println("滚动到评论区...");
            scrollToCommentSection(driver);
            Thread.sleep(2000);

            // 发送评论
            System.out.println("准备发送评论...");
            boolean success = sendComment(driver, wait, rcWin.generate());

            if (success) {
                System.out.println("评论发送成功！");
            } else {
                System.out.println("评论发送失败！");
            }

            Thread.sleep(3000); // 等待查看结果

        } catch (Exception e) {
            System.out.println("发送评论时出错: " + e.getMessage());
            e.printStackTrace();
        }

    }

    /**
     * 发送评论的核心方法
     */
    private static boolean sendComment(WebDriver driver,
                                       WebDriverWait wait,
                                       String commentText) {
        try {
            // 方法1: 通过 Shadow DOM 访问（推荐）
            return sendCommentViaShadowDOM(driver, wait, commentText);
        } catch (Exception e1) {
            System.out.println("Shadow DOM 方法失败，尝试 JavaScript 方法...");
            try {
                // 方法2: 通过 JavaScript 直接操作
                return sendCommentViaJavaScript(driver, commentText);
            } catch (Exception e2) {
                System.out.println("JavaScript 方法失败，尝试 XPath 方法...");
                try {
                    // 方法3: 通过 XPath（备用方案）
                    return sendCommentViaXPath(driver, wait, commentText);
                } catch (Exception e3) {
                    e3.printStackTrace();
                    return false;
                }
            }
        }
    }

    /**
     * 方法1: 通过 Shadow DOM 访问评论框
     */
    private static boolean sendCommentViaShadowDOM(WebDriver driver,
                                                   WebDriverWait wait,
                                                   String commentText)
            throws InterruptedException {
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
    }

    /**
     * 方法2: 纯 JavaScript 方法
     */
    private static boolean sendCommentViaJavaScript(WebDriver driver,
                                                    String commentText)
            throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String script =
                "const comments = document.querySelector('bili-comments');" +
                "if (!comments || !comments.shadowRoot) return false;" +

                "const commentBox = comments.shadowRoot.querySelector('bili-comment-box');" +
                "if (!commentBox || !commentBox.shadowRoot) return false;" +

                "const textarea = commentBox.shadowRoot.querySelector('bili-comment-rich-textarea');" +
                "if (!textarea || !textarea.shadowRoot) return false;" +

                "const editor = textarea.shadowRoot.querySelector('.brt-editor');" +
                "if (!editor) return false;" +

                "editor.textContent = arguments[0];" +
                "editor.dispatchEvent(new Event('input', { bubbles: true }));" +

                "setTimeout(() => {" +
                "  const publishBtn = commentBox.shadowRoot.querySelector('#pub button');" +
                "  if (publishBtn) publishBtn.click();" +
                "}, 500);" +

                "return true;";

        Boolean result = (Boolean) js.executeScript(script, commentText);
        Thread.sleep(1500);

        return result != null && result;
    }

    /**
     * 方法3: 通过 XPath 和 CSS 选择器（备用）
     */
    private static boolean sendCommentViaXPath(WebDriver driver,
                                               WebDriverWait wait,
                                               String commentText)
            throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 尝试直接定位到评论输入框
        WebElement commentBox = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("bili-comments")
        ));

        // 点击评论框激活
        js.executeScript("arguments[0].click();", commentBox);
        Thread.sleep(500);

        // 通过 JS 设置内容
        String script =
                "const editor = document.querySelector('bili-comments').shadowRoot" +
                ".querySelector('bili-comment-box').shadowRoot" +
                ".querySelector('bili-comment-rich-textarea').shadowRoot" +
                ".querySelector('.brt-editor');" +
                "editor.textContent = arguments[0];" +
                "editor.focus();";

        js.executeScript(script, commentText);
        Thread.sleep(1000);

        // 点击发布
        String publishScript =
                "const btn = document.querySelector('bili-comments').shadowRoot" +
                ".querySelector('bili-comment-box').shadowRoot" +
                ".querySelector('#pub button');" +
                "btn.click();";

        js.executeScript(publishScript);

        return true;
    }

    /**
     * 滚动到评论区
     */
    private static void scrollToCommentSection(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 查找评论区元素
        try {
            WebElement commentSection = driver.findElement(By.cssSelector("bili-comments"));
            js.executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", commentSection);
        } catch (Exception e) {
            // 如果找不到评论区，滚动到页面中部
            js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.5);");
        }
    }

}
