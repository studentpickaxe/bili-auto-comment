package yfrp.autobili.vid;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

public class BiliApi {

    private static final Pattern pubdatePattern = Pattern.compile("\"pubdate\":(\\d+),");

    public static String getVidInfo(String bvid)
            throws IOException, InterruptedException {
        var apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;

        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    public static long getVidPubDate(String bvid)
            throws IOException, InterruptedException {
        var responseBody = getVidInfo(bvid);

        var matcher = pubdatePattern.matcher(responseBody);
        if (matcher.find()) {
            return NumberUtils.toLong(matcher.group(1), -1);
        }
        return -1;
    }
}
