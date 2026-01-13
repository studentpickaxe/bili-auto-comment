package yfrp.autobili.config;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.yaml.snakeyaml.Yaml;
import yfrp.autobili.comment.AutoComment;
import yfrp.autobili.comment.RandomComment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

public class Config {

    private static final String configFile = "config.yaml";

    private static final String defaultConfig =
            """
            login:
              # 第一次使用请登录
              enable: false
            
            
            search:
              enable: true
              interval: 120
              keywords:
                - 斩杀线
                - 殖
                - 公知
                - NGO
            
            
            comment:
              interval: 30
              min_pubdate:
                year:   2000
                month:  1
                day:    1
                hour:   0
                minute: 0
                second: 0
            
              templates:
                - ${wins}${stickers}
                - ${stickers}${wins}
            
              vars:
                stickers:
                  - :sticker;
                  - :sticker;:sticker;
            
                sticker:
                  - '[星星眼]'
                  - '[打call]'
                  - '[滑稽]'
                  - '[妙啊]'
                  - '[嗑瓜子]'
                  - '[呲牙]'
                  - '[大笑]'
                  - '[偷笑]'
                  - '[鼓掌]'
                  - '[嘘声]'
                  - '[捂眼]'
                  - '[惊喜]'
                  - '[哈欠]'
                  - '[抓狂]'
            
                wins:
                  - :win;
                  - :won;
            
                win:
                  - '赢'
                  - '🥇赢'
                  - '赢🥇'
                  - '✌赢'
            
                won:
                  - '赢了'
                  - '✌赢了'
                  - '赢了✌'
                  - '🥇赢了'
            
            """;


    // 登录
    private final boolean loginEnabled;

    // 搜索
    private final boolean searchEnabled;
    private final int searchInterval;
    private final String[] searchKeywords;

    // 评论
    private static final int minCommentInterval = 20;
    private final int commentInterval;
    private final int minPubdate;

    private final AutoComment autoCommentInstance;


    private Config(Map<String, Object> config) {

        // 登录
        Map<String, Object> loginMap = getMap(config, "login");
        this.loginEnabled = getBoolean(loginMap, "enable", false);

        // 搜索
        Map<String, Object> searchMap = getMap(config, "search");
        this.searchEnabled = getBoolean(searchMap, "enable", true);
        this.searchInterval = getInt(searchMap, "interval", 300);
        this.searchKeywords = getStringArray(
                searchMap,
                "keywords",
                new String[]{"斩杀线", "殖", "公知", "NGO"}
        );

        // 评论
        Map<String, Object> commentMap = getMap(config, "comment");
        this.commentInterval = Math.max(
                getInt(commentMap, "interval", 30),
                minCommentInterval
        );

        // 解析 min_pubdate - 转换为时间戳（秒）
        Map<String, Object> minPubMap = getMap(commentMap, "min_pubdate");
        int year = getInt(minPubMap, "year", 2000);
        int month = getInt(minPubMap, "month", 1);
        int day = getInt(minPubMap, "day", 1);
        int hour = getInt(minPubMap, "hour", 0);
        int minute = getInt(minPubMap, "minute", 0);
        int second = getInt(minPubMap, "second", 0);
        LocalDateTime minPubDateTime = LocalDateTime.of(year, month, day, hour, minute, second);

        this.minPubdate = (int) minPubDateTime
                .atZone(ZoneId.systemDefault())
                .toEpochSecond();

        this.autoCommentInstance = new AutoComment(new RandomComment(commentMap));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        return (Map<String, Object>) MapUtils.getMap(map, key, Map.of());
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        return NumberUtils.toInt(String.valueOf(v), defaultVal);
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultVal) {
        Object v = map.get(key);
        return v == null ? defaultVal : BooleanUtils.toBoolean(v.toString());
    }

    private static String[] getStringArray(Map<String, Object> map, String key, String[] defaultVal) {
        Object v = map.get(key);
        if (v instanceof java.util.List<?> list) {
            return list.stream().map(String::valueOf).toArray(String[]::new);
        }
        return defaultVal;
    }


    public boolean isLoginEnabled() {
        return loginEnabled;
    }

    public boolean isSearchEnabled() {
        return searchEnabled;
    }

    public int getSearchInterval() {
        return searchInterval;
    }

    public String[] getSearchKeywords() {
        return searchKeywords;
    }

    public AutoComment autoCommentInstance() {
        return autoCommentInstance;
    }

    public int getCommentInterval() {
        return commentInterval;
    }

    public int getMinPubdate() {
        return minPubdate;
    }

    public static Config getInstance() {
        return new Config(loadConfig());
    }

    private static Map<String, Object> loadConfig() {
        Path path = Path.of(configFile);
        Yaml yaml = new Yaml();

        if (Files.notExists(path)) {
            // 创建默认配置文件
            try {
                Files.writeString(
                        path,
                        defaultConfig,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return yaml.load(defaultConfig);
        }

        try (InputStream in = Files.newInputStream(path)) {
            return yaml.load(in);
        } catch (IOException e) {
            return yaml.load(defaultConfig);
        }
    }

}
