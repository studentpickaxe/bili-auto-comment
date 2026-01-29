package yfrp.autobili.browser;

import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Chrome 浏览器选项工具类
 * <p>
 * 提供配置 Chrome 浏览器选项的静态方法
 */
public class ChromeOptionUtil {

    /**
     * 设置 Chrome 用户配置文件
     * <p>
     * 将浏览器数据保存到指定的配置文件目录中
     *
     * @param options Chrome 选项对象
     * @param profileName 配置文件名称
     */
    public static void setProfile(ChromeOptions options, String profileName) {

        // 获取当前工作目录
        var userDir = System.getProperty("user.dir");
        // 构建配置文件路径
        var chromeProfilePath = userDir + File.separator + "chrome-profiles" + File.separator + "profile-" + profileName;

        // 设置用户数据目录参数
        options.addArguments("--user-data-dir=" + chromeProfilePath);
    }

    /**
     * 使 Chrome 浏览器轻量化
     * <p>
     * 通过禁用不必要的功能和特性来提高性能和减少资源占用
     *
     * @param options Chrome 选项对象
     */
    public static void makeLightweight(ChromeOptions options) {

        // 添加各种禁用和优化参数
        options.addArguments(
                "--profile-directory=Default",
                "--disable-features=TranslateUI",
                "--disable-gpu",
                "--blink-settings=imagesEnabled=false",
                "--disable-extensions",
                "--disable-plugins",
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--disable-infobars",
                "--disable-features=VizDisplayCompositor",
                "--disable-blink-features=AutomationControlled",
                "--silent"
        );

        // 禁用自动化控制提示，避免被检测为自动化工具
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // 设置内容设置：阻止媒体流
        Map<String, Object> prefs = new HashMap<>();

        // 禁用媒体流（包括视频和音频）
        prefs.put("profile.default_content_setting_values.media_stream", 2);
        prefs.put("profile.default_content_setting_values.media_stream_mic", 2);
        prefs.put("profile.default_content_setting_values.media_stream_camera", 2);

        // 禁用受保护的媒体内容
        prefs.put("profile.default_content_setting_values.protected_media_identifier", 2);

        // 应用偏好设置
        options.setExperimentalOption("prefs", prefs);
    }
}
