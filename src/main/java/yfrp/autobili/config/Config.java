package yfrp.autobili.config;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.*;

public class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

    private static final String configFile = "config.yaml";

    private static final String defaultConfig =
            """
            login:
              # 无法重载
              # could NOT be reloaded
              enable: NO
            
            
            search:
              # 无法重载
              # could NOT be reloaded
              enable: YES
            
              interval: 30
            
              keywords:
                - 殖
                - 公知
            
            
            comment:
              interval: 120
            
              cooldown:
                hour:   2
                minute: 0
                second: 0
            
              min_pubdate:
                year:   2000
                month:  1
                day:    1
                hour:   0
                minute: 0
                second: 0
            
              auto_clear_delay:
                day:    10
                hour:   0
            
              templates:
                - :wins;:stickers;
                - :stickers;:wins;
            
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


    private final long SEED = System.currentTimeMillis();


    // 登录
    private boolean loginEnabled;

    public static final int MIN_SEARCH_INTERVAL = 10;
    public static final int MIN_COMMENT_INTERVAL = 20;

    // 搜索
    private boolean searchEnabled;
    private int searchInterval;
    private final List<String> searchKeywords = new ArrayList<>();

    // 评论
    private int commentInterval;
    private int commentCooldown;
    private long minPubdate;
    private int autoClearDelay;

    private final AutoComment autoCommentInstance = new AutoComment();


    public Config(Path path) {
        var first = Files.notExists(path);
        this.loginEnabled = first;
        if (first) {
            LOGGER.info("检测到第一次启动，请按提示完成浏览器初始化");
        }
    }

    public static Config getInstance() {
        Path path = Path.of(configFile);
        var config = new Config(path);
        config.loadConfig(path);
        return config;
    }

    public void loadConfig() {
        loadConfig(Path.of(configFile));
    }

    private void loadConfig(Path path) {
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

            parseConfig(yaml.load(defaultConfig));
        }

        try (InputStream in = Files.newInputStream(path)) {
            parseConfig(yaml.load(in));
        } catch (IOException e) {
            parseConfig(yaml.load(defaultConfig));
        }
    }

    private void parseConfig(Map<String, Object> config) {

        // 登录
        Map<String, Object> loginMap = getMap(config, "login");
        if (getBoolean(loginMap, "enable", false)) {
            this.loginEnabled = true;
        }

        // 搜索
        Map<String, Object> searchMap = getMap(config, "search");
        this.searchEnabled = getBoolean(searchMap, "enable", true);
        this.searchInterval = Math.max(
                getInt(searchMap, "interval", 30),
                MIN_SEARCH_INTERVAL
        );
        var keywords = new ArrayList<>(getStringArray(
                searchMap,
                "keywords",
                new String[]{"殖", "公知"}
        ));
        Collections.shuffle(keywords, new Random(SEED));
        this.searchKeywords.clear();
        this.searchKeywords.addAll(keywords);

        // 评论
        Map<String, Object> commentMap = getMap(config, "comment");
        this.commentInterval = Math.max(
                getInt(commentMap, "interval", 120),
                MIN_COMMENT_INTERVAL
        );

        Map<String, Object> cooldownMap = getMap(commentMap, "cooldown");
        this.commentCooldown = getInt(cooldownMap, "hour",   2) * 3600 +
                               getInt(cooldownMap, "minute", 0) * 60 +
                               getInt(cooldownMap, "second", 0);

        Map<String, Object> minPubMap = getMap(commentMap, "min_pubdate");
        int year   = getInt(minPubMap, "year",   2000);
        int month  = getInt(minPubMap, "month",  1   );
        int day    = getInt(minPubMap, "day",    1   );
        int hour   = getInt(minPubMap, "hour",   0   );
        int minute = getInt(minPubMap, "minute", 0   );
        int second = getInt(minPubMap, "second", 0   );
        LocalDateTime minPubdateTime = LocalDateTime.of(year, month, day, hour, minute, second);
        this.minPubdate = (int) minPubdateTime
                .atZone(ZoneId.systemDefault())
                .toEpochSecond();

        Map<String, Object> autoClearMap = getMap(commentMap, "auto_clear_delay");
        this.autoClearDelay = getInt(autoClearMap, "day",  10) * 86400 +
                              getInt(autoClearMap, "hour", 0 ) * 3600;

        this.autoCommentInstance.setCommentFormat(new RandomComment(commentMap));
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map,
                                              String key) {

        return (Map<String, Object>) MapUtils.getMap(map, key, Map.of());
    }

    private static int getInt(Map<String, Object> map,
                              String key,
                              int defaultVal) {

        Object v = map.get(key);
        return NumberUtils.toInt(String.valueOf(v), defaultVal);
    }

    private static boolean getBoolean(Map<String, Object> map,
                                      String key,
                                      boolean defaultVal) {

        Object v = map.get(key);
        return v == null ? defaultVal : BooleanUtils.toBoolean(v.toString());
    }

    private static List<String> getStringArray(Map<String, Object> map,
                                               String key,
                                               String[] defaultVal) {

        Object v = map.get(key);
        if (v instanceof java.util.List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(defaultVal);
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

    public List<String> getSearchKeywords() {
        return searchKeywords;
    }

    public AutoComment autoCommentInstance() {
        return autoCommentInstance;
    }

    public int getCommentInterval() {
        return commentInterval;
    }

    public int getCommentCooldown() {
        return commentCooldown;
    }

    public long getMinPubdate() {
        return minPubdate;
    }

    public int getAutoClearDelay() {
        return autoClearDelay;
    }

}
