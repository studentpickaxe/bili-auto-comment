package yfrp.autobili.comment;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 随机评论生成器
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
     * <p>
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
     * <p>
     * 防止循环引用，确保变量解析的正确性
     *
     * @param varName     变量名
     * @param vars        变量映射表
     * @param random      随机数生成器
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

}
