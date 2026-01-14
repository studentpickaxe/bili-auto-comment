package yfrp.autobili.browser;

import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ChromeOptionUtil {

    public static void setProfile(ChromeOptions options, String profileName) {

        var userDir = System.getProperty("user.dir");
        var chromeProfilePath = userDir + File.separator + "chrome-profiles" + File.separator + "profile-" + profileName;

        options.addArguments("--user-data-dir=" + chromeProfilePath);
    }

    public static void makeLightweight(ChromeOptions options) {

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

        // 禁用自动化控制提示
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

        options.setExperimentalOption("prefs", prefs);
    }
}
