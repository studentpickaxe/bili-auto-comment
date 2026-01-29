package yfrp.autobili.comment;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 随机评论生成器
 * 
 * 支持从配置文件或字符串中解析评论模板和变量，生成随机评论
 * 
 * 示例配置格式：
 * <p>
 * 评论模板1:sticker;:var1;
 * <p>
 * :sticker;:var2;评论模板2
 * <p>
 * {{{{{{
 * <p>
 * sticker={[星星眼]'[打call]'[滑稽]'[妙啊]'[嗑瓜子]'[呲牙]'[大笑]'[偷笑]'[鼓掌]'[嘘声]'[捂眼]'[惊喜]'[哈欠]'[抓狂]}
 * <p>
 * var1={:var2;'①}
 * <p>
 * var2={②}
 */
public class RandomComment {

    // 评论模板集合
    private final Set<String> templates;
    // 变量映射表
    private final HashMap<String, Set<String>> vars;

    /**
     * 从配置对象构造随机评论生成器
     *
     * @param config 包含 templates 和 vars 的配置对象
     */
    public RandomComment(Map<String, Object> config) {

        this.templates = new HashSet<>();
        this.vars = new HashMap<>();

        // 解析 templates
        Object templatesObj = config.get("templates");
        if (templatesObj instanceof List<?> templatesList) {
            for (Object template : templatesList) {
                if (template != null) {
                    templates.add(template.toString());
                }
            }
        }

        // 解析 vars
        Object varsObj = config.get("vars");
        if (varsObj instanceof Map) {
            var varsMap = (Map<String, Object>) varsObj;
            for (Map.Entry<String, Object> entry : varsMap.entrySet()) {
                String varName = entry.getKey();
                Object valuesObj = entry.getValue();

                if (valuesObj instanceof List<?> valuesList) {
                    Set<String> values = new HashSet<>();
                    for (Object value : valuesList) {
                        if (value != null) {
                            values.add(value.toString());
                        }
                    }
                    vars.put(varName, values);
                }
            }
        }

    }

    /**
     * 从字符串构造随机评论生成器
     * 字符串格式：模板部分 + 分隔符 + 变量部分
     *
     * @param rcfStr 配置字符串
     */
    public RandomComment(String rcfStr) {
        this.templates = new HashSet<>();
        this.vars = new HashMap<>();

        // 分割字符串，找到分隔符 {{{{{{
        String[] parts = rcfStr.split("\\{\\{\\{\\{\\{\\{", 2);

        // 处理模板部分（分隔符之前）
        if (parts.length > 0) {
            String templatesPart = parts[0];
            String[] templateLines = templatesPart.split("\\r?\\n");

            for (String line : templateLines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    templates.add(line);
                }
            }
        }

        // 处理变量部分（分隔符之后）
        if (parts.length > 1) {
            String varsPart = parts[1];
            String[] varLines = varsPart.split("\\r?\\n");

            // 正则表达式：匹配 varName={value1'value2'value3}
            Pattern pattern = Pattern.compile("^([^=]+)=\\{((?:[^'}]+')*[^'}]+)}$");

            for (String line : varLines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    String varName = matcher.group(1).trim();
                    String valuesStr = matcher.group(2);

                    // 分割值（用单引号作为分隔符）
                    String[] values = valuesStr.split("'");
                    Set<String> valueSet = new HashSet<>();

                    for (String value : values) {
                        value = value.trim();
                        if (!value.isEmpty()) {
                            valueSet.add(value);
                        }
                    }

                    vars.put(varName, valueSet);
                }
            }
        }
    }

    /**
     * 获取评论模板集合
     *
     * @return 评论模板集合
     */
    public Set<String> getTemplates() {
        return templates;
    }

    /**
     * 获取变量映射表
     *
     * @return 变量映射表
     */
    public HashMap<String, Set<String>> getVars() {
        return vars;
    }

    /**
     * 生成随机评论
     * 随机选择一个模板，然后替换其中的变量占位符
     *
     * @return 生成的随机评论
     */
    public String generate() {
        Random random = new Random();

        if (templates.isEmpty()) {
            return "";
        }

        // 随机选择一个模板
        List<String> templateList = new ArrayList<>(templates);
        String template = templateList.get(random.nextInt(templateList.size()));

        // 替换所有 :varName; 格式的占位符
        Pattern pattern = Pattern.compile(":([^;]+);");
        Matcher matcher = pattern.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = resolveVariable(varName, vars, random, new HashSet<>());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 递归解析变量（处理嵌套变量的情况）
     * 防止循环引用，确保变量解析的正确性
     *
     * @param varName 变量名
     * @param vars 变量映射表
     * @param random 随机数生成器
     * @param visitedVars 已访问的变量集合，用于防止循环引用
     * @return 解析后的变量值
     */
    private static String resolveVariable(String varName,
                                          HashMap<String, Set<String>> vars,
                                          Random random,
                                          Set<String> visitedVars) {
        // 防止循环引用
        if (visitedVars.contains(varName)) {
            return ":" + varName + ";";
        }

        Set<String> values = vars.get(varName);
        if (values == null || values.isEmpty()) {
            return ":" + varName + ";";
        }

        // 随机选择一个值
        List<String> valueList = new ArrayList<>(values);
        String selectedValue = valueList.get(random.nextInt(valueList.size()));

        // 检查选中的值是否包含嵌套变量
        Pattern nestedPattern = Pattern.compile(":([^;]+);");
        Matcher nestedMatcher = nestedPattern.matcher(selectedValue);

        if (nestedMatcher.find()) {
            StringBuilder resolvedValue = new StringBuilder();
            Set<String> newVisited = new HashSet<>(visitedVars);
            newVisited.add(varName);

            nestedMatcher.reset();
            while (nestedMatcher.find()) {
                String nestedVarName = nestedMatcher.group(1);
                String nestedReplacement = resolveVariable(nestedVarName, vars, random, newVisited);
                nestedMatcher.appendReplacement(resolvedValue, Matcher.quoteReplacement(nestedReplacement));
            }
            nestedMatcher.appendTail(resolvedValue);

            return resolvedValue.toString();
        }

        return selectedValue;
    }

    /**
     * 测试方法
     * 演示如何使用 RandomComment 类生成随机评论
     */
    static void main() {
        String testInput = """
                           评论模板1:sticker;:var1;
                           :sticker;:var2;评论模板2
                           {{{{{{
                           sticker={[星星眼]'[打call]'[滑稽]'[妙啊]'[嗑瓜子]'[呲牙]'[大笑]'[偷笑]'[鼓掌]'[嘘声]'[捂眼]'[惊喜]'[哈欠]'[抓狂]}
                           var1={:var2;'①}
                           var2={②}
                           """;

        RandomComment rcf = new RandomComment(testInput);

        System.out.println("templates:");
        for (String template : rcf.getTemplates()) {
            System.out.println("  " + template);
        }

        System.out.println("\nVariables:");
        for (Map.Entry<String, Set<String>> entry : rcf.getVars().entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }

        System.out.println("\n生成的随机评论示例:");
        for (int i = 0; i < 100; i++) {
            System.out.println("  " + (i + 1) + ". " + rcf.generate());
        }
    }
}
