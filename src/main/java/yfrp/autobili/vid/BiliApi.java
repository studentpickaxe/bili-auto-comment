package yfrp.autobili.vid;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

/**
 * Bilibili API 工具类
 * <p>
 * 提供访问 Bilibili API 的方法
 */
public class BiliApi {

    // 发布时间正则表达式模式
    private static final Pattern pubdatePattern = Pattern.compile("\"pubdate\":(\\d+),");

    /**
     * 获取视频信息
     * <p>
     * 通过 Bilibili API 获取指定视频的详细信息
     *
     * @param url 视频信息 API URL
     * @return API 返回的 JSON 响应字符串
     * @throws IOException IO 异常
     * @throws InterruptedException 线程中断异常
     */
    public static String getVidInfo(String url)
            throws IOException, InterruptedException {

        try (var client = HttpClient.newHttpClient()) {

            // 构建 HTTP 请求
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                    .GET()
                    .build();
            // 发送请求并获取响应
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.body();
        }
    }

    /**
     * 获取视频发布时间
     * 从视频信息中提取发布时间戳
     *
     * @param url 视频 API URL
     * @return 视频发布时间戳（秒），如果获取失败返回 -1
     * @throws IOException IO 异常
     * @throws InterruptedException 线程中断异常
     */
    public static long getVidPubDate(String url)
            throws IOException, InterruptedException {
        // 获取视频信息
        var responseBody = getVidInfo(url);

        // 使用正则表达式匹配发布时间
        var matcher = pubdatePattern.matcher(responseBody);
        if (matcher.find()) {
            // 将匹配到的时间戳字符串转换为长整型
            return NumberUtils.toLong(matcher.group(1), -1);
        }
        // 匹配失败，返回 -1
        return -1;
    }
}
