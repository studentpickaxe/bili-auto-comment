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
import java.util.*;
import java.util.regex.Pattern;

/**
 * 系统配置类
 * <p>
 * 负责加载和管理应用程序的配置信息
 */
public class Config {

    // 配置文件名
    private static final String configFile = "config.yaml";

    // 默认配置内容
    private static final String defaultConfig =
            """
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
            
              min-pubdate:
                year:   2000
                month:  1
                day:    1
                hour:   0
                minute: 0
                second: 0
            
              auto-clear-delay:
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
                  - '✌️赢'
            
                won:
                  - '赢了'
                  - '✌️赢了'
                  - '赢了✌️'
                  - '🥇赢了'
            
            
            url:
              placeholder: '{}'
              homepage:    'https://www.bilibili.com/'
              video-api:   'https://api.bilibili.com/x/web-interface/view?bvid={}'
              video:       'https://www.bilibili.com/video/{}/'
              search:      'https://search.bilibili.com/all?keyword={}&from_source=webtop_search&search_source=5&order=pubdate'
            
            
            toast-keyword:
              cd-ban:        'cd'
              not-logged-in: '未登录'
            
            """;


    // 随机数种子，用于关键词随机化
    private final long SEED = System.currentTimeMillis();


    // 搜索间隔最小值（秒）
    public static final int MIN_SEARCH_INTERVAL = 10;
    // 评论间隔最小值（秒）
    public static final int MIN_COMMENT_INTERVAL = 20;

    // 搜索配置
    private boolean searchEnabled;
    private int searchInterval;
    // 搜索关键词列表实例
    private final List<String> searchKeywordsInstance = new ArrayList<>();

    // 评论配置
    private int commentInterval;
    // 评论冷却时间（秒）
    private int commentCooldown;
    // 最早发布时间戳
    private long minPubdate;
    // 自动清理延迟时间（秒）
    private int autoClearDelay;

    // URL 替换占位符
    private String urlPlaceholder;
    // 主页 URL
    private String urlHomepage;
    // 视频 API URL
    private String urlVideoApi;
    // 视频页面 URL
    private String urlVideo;
    // 搜索页面 URL
    private String urlSearch;

    // cd 风控 toast 关键词
    private String toastKwCdBan;
    // 未登录 toast 关键词
    private String toastKwNotLoggedIn;

    // 自动评论实例
    private final AutoComment autoCommentInstance = new AutoComment(this);


    /**
     * 获取配置实例
     * <p>
     * 单例模式，确保全局只有一个配置实例
     *
     * @return 配置实例
     */
    public static Config getInstance() {
        Path path = Path.of(configFile);
        var config = new Config();
        config.loadConfig(path);
        return config;
    }

    /**
     * 重新加载配置
     * <p>
     * 从配置文件中重新读取配置信息
     */
    public void loadConfig() {
        loadConfig(Path.of(configFile));
    }

    /**
     * 从指定路径加载配置
     *
     * @param path 配置文件路径
     */
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

    /**
     * 解析配置文件
     * <p>
     * 将 YAML 配置文件解析为 Java 对象
     *
     * @param config 配置对象
     */
    private void parseConfig(Map<String, Object> config) {

        // 解析搜索配置
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
        // 随机化关键词顺序
        Collections.shuffle(keywords, new Random(SEED));
        this.searchKeywordsInstance.clear();
        this.searchKeywordsInstance.addAll(keywords);

        // 解析评论配置
        Map<String, Object> commentMap = getMap(config, "comment");
        this.commentInterval = Math.max(
                getInt(commentMap, "interval", 120),
                MIN_COMMENT_INTERVAL
        );

        // 解析评论冷却配置
        Map<String, Object> cooldownMap = getMap(commentMap, "cooldown");
        this.commentCooldown = getInt(cooldownMap, "hour",   2) * 3600 +
                               getInt(cooldownMap, "minute", 0) * 60 +
                               getInt(cooldownMap, "second", 0);

        // 解析最早发布时间配置
        Map<String, Object> minPubMap = getMap(commentMap, "min-pubdate");
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

        // 解析自动清理延迟配置
        Map<String, Object> autoClearMap = getMap(commentMap, "auto-clear-delay");
        this.autoClearDelay = getInt(autoClearMap, "day",  10) * 86400 +
                              getInt(autoClearMap, "hour", 0 ) * 3600;

        // 解析 URL 配置
        Map<String, Object> urlMap = getMap(config, "url");
        this.urlPlaceholder = MapUtils.getString(urlMap, "placeholder", "{}");
        this.urlHomepage    = MapUtils.getString(urlMap, "homepage",    "https://www.bilibili.com/");
        this.urlVideoApi    = MapUtils.getString(urlMap, "video-api",   "https://api.bilibili.com/x/web-interface/view?bvid={}");
        this.urlVideo       = MapUtils.getString(urlMap, "video",       "https://www.bilibili.com/video/{}/");
        this.urlSearch      = MapUtils.getString(urlMap, "search",      "https://search.bilibili.com/all?keyword={}&from_source=webtop_search&search_source=5&order=pubdate");

        // 解析 toast 关键词
        Map<String, Object> toastMap = getMap(config, "toast-keyword");
        this.toastKwCdBan        = MapUtils.getString(toastMap, "ban",           "cd");
        this.toastKwNotLoggedIn  = MapUtils.getString(toastMap, "not-logged-in", "未登录");

        // 设置评论格式
        this.autoCommentInstance.setCommentFormat(new RandomComment(commentMap));
    }


    /**
     * 从配置映射中获取子映射
     *
     * @param map 配置映射
     * @param key 键名
     * @return 子映射
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map,
                                              String key) {

        return (Map<String, Object>) MapUtils.getMap(map, key, Map.of());
    }

    /**
     * 从配置映射中获取整数值
     *
     * @param map        配置映射
     * @param key        键名
     * @param defaultVal 默认值
     * @return 整数值
     */
    private static int getInt(Map<String, Object> map,
                              String key,
                              int defaultVal) {

        Object v = map.get(key);
        return NumberUtils.toInt(String.valueOf(v), defaultVal);
    }

    /**
     * 从配置映射中获取布尔值
     *
     * @param map        配置映射
     * @param key        键名
     * @param defaultVal 默认值
     * @return 布尔值
     */
    private static boolean getBoolean(Map<String, Object> map,
                                      String key,
                                      boolean defaultVal) {

        Object v = map.get(key);
        return v == null ? defaultVal : BooleanUtils.toBoolean(v.toString());
    }

    /**
     * 从配置映射中获取字符串数组
     *
     * @param map        配置映射
     * @param key        键名
     * @param defaultVal 默认值
     * @return 字符串列表
     */
    private static List<String> getStringArray(Map<String, Object> map,
                                               String key,
                                               String[] defaultVal) {

        Object v = map.get(key);
        if (v instanceof java.util.List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(defaultVal);
    }


    /**
     * 获取搜索启用状态
     *
     * @return 是否启用搜索
     */
    public boolean isSearchEnabled() {
        return searchEnabled;
    }

    /**
     * 获取搜索间隔
     *
     * @return 搜索间隔（秒）
     */
    public int getSearchInterval() {
        return searchInterval;
    }

    /**
     * 获取搜索关键词列表
     *
     * @return 搜索关键词列表
     */
    public List<String> getSearchKeywordsInstance() {
        return searchKeywordsInstance;
    }

    /**
     * 获取评论间隔
     *
     * @return 评论间隔（秒）
     */
    public int getCommentInterval() {
        return commentInterval;
    }

    /**
     * 获取评论冷却时间
     *
     * @return 评论冷却时间（秒）
     */
    public int getCommentCooldown() {
        return commentCooldown;
    }

    /**
     * 获取最早发布时间戳
     *
     * @return 最早发布时间戳
     */
    public long getMinPubdate() {
        return minPubdate;
    }

    /**
     * 获取自动清理延迟时间
     *
     * @return 自动清理延迟时间（秒）
     */
    public int getAutoClearDelay() {
        return autoClearDelay;
    }

    /**
     * 获取 URL 替换占位符
     *
     * @return URL 替换占位符
     */
    public String getUrlPlaceholder() {
        return urlPlaceholder;
    }

    /**
     * 获取主页 URL
     *
     * @return 主页 URL
     */
    public String getUrlHomepage() {
        return urlHomepage;
    }

    /**
     * 获取视频 API URL
     *
     * @return 视频 API URL
     */
    public String getUrlVideoApi() {
        return urlVideoApi;
    }

    /**
     * 获取视频页面 URL
     *
     * @return 视频页面 URL
     */
    public String getUrlVideo() {
        return urlVideo;
    }

    /**
     * 获取搜索页面 URL
     *
     * @return 搜索页面 URL
     */
    public String getUrlSearch() {
        return urlSearch;
    }

    /**
     * 获取视频 API URL，并将占位符替换为指定字符串
     *
     * @param replaceWith 替换字符串
     * @return 视频 API URL
     */
    public String getUrlVideoApi(String replaceWith) {
        return urlVideoApi.replaceAll(
                Pattern.quote(getUrlPlaceholder()),
                replaceWith
        );
    }

    /**
     * 获取视频页面 URL，并将占位符替换为指定字符串
     *
     * @param replaceWith 替换字符串
     * @return 视频页面 URL
     */
    public String getUrlVideo(String replaceWith) {
        return urlVideo.replaceAll(
                Pattern.quote(getUrlPlaceholder()),
                replaceWith
        );
    }

    /**
     * 获取搜索页面 URL，并将占位符替换为指定字符串
     *
     * @param replaceWith 替换字符串
     * @return 搜索页面 URL
     */
    public String getUrlSearch(String replaceWith) {
        return urlSearch.replaceAll(
                Pattern.quote(getUrlPlaceholder()),
                replaceWith
        );
    }

    /**
     * 获取 cd 风控 toast 关键词
     *
     * @return cd 风控 toast 关键词
     */
    public String getToastKwCdBan() {
        return toastKwCdBan;
    }

    /**
     * 获取未登录 toast 关键词
     *
     * @return 未登录 toast 关键词
     */
    public String getToastKwNotLoggedIn() {
        return toastKwNotLoggedIn;
    }

    /**
     * 获取自动评论实例
     *
     * @return 自动评论实例
     */
    public AutoComment autoCommentInstance() {
        return autoCommentInstance;
    }

}
