package yfrp.autobili;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.time.Duration;
import java.util.Set;

public class Main {

    public static final int timeout = 10;
    public static final String COOKIE_FILE = "bilibili_cookies.txt";
    public static final String searchWord = "斩杀线";
    private static WebDriver driver;

    // 保存 Cookies 到文件
    public static void saveCookies(WebDriver driver, String filepath) throws InterruptedException {

        driver.get("https://www.bilibili.com");
        Thread.sleep(500);

        try {
            File file = new File(filepath);
            FileWriter fileWriter = new FileWriter(file);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            Set<Cookie> cookies = driver.manage().getCookies();
            for (Cookie cookie : cookies) {
                bufferedWriter.write(cookie.getName() + ";" +
                                     cookie.getValue() + ";" +
                                     cookie.getDomain() + ";" +
                                     cookie.getPath() + ";" +
                                     cookie.getExpiry() + ";" +
                                     cookie.isSecure());
                bufferedWriter.newLine();
            }
            bufferedWriter.close();
            fileWriter.close();
            System.out.println("Cookies 保存成功！");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // 从文件加载 Cookies
    public static void loadCookies(WebDriver driver, String filepath) {

        try {
            File file = new File(filepath);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] token = line.split(";");

                Cookie.Builder cookieBuilder = new Cookie.Builder(token[0], token[1])
                        .domain(token[2])
                        .path(token[3])
                        .isSecure(Boolean.parseBoolean(token[5]));

                if (token[4] != null && !token[4].equals("null")) {
                    // 处理过期时间
                }

                driver.manage().addCookie(cookieBuilder.build());
            }
            bufferedReader.close();
            fileReader.close();
            System.out.println("Cookies 加载成功！");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    static void main() {

        driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout));

        try {
            // 打开 bilibili
            driver.get("https://www.bilibili.com");

            // 加载 cookies
            loadCookies(driver, COOKIE_FILE);
            driver.navigate().refresh();
            Thread.sleep(1000);

            // 搜索
            driver.get("https://search.bilibili.com/all?keyword=" + searchWord + "&from_source=webtop_search&spm_id_from=333.1007&search_source=5&order=pubdate");


            System.out.println("\n浏览器将保持打开状态");
            System.out.println("👉 按 Enter 键【保存 Cookies 并退出】");
            System.out.println("👉 直接关闭浏览器【不保存 Cookies】");

            // 监听浏览器是否被手动关闭
            Thread browserMonitor = new Thread(() -> {
                while (true) {
                    try {
                        driver.getWindowHandles();
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        System.out.println("\n检测到浏览器被手动关闭，不保存 Cookies，程序退出");
                        System.exit(0);
                    }
                }
            });
            browserMonitor.setDaemon(true);
            browserMonitor.start();

            // 等待用户按 Enter
            System.in.read();

            // 保存 cookies
            System.out.println("\n用户选择正常退出，正在保存 Cookies...");
            saveCookies(driver, COOKIE_FILE);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

    }
}
