package yfrp.autobili.comment;

/**
 * 评论冷却异常
 * 当评论发送过于频繁，触发平台冷却机制时抛出此异常
 */
public class CommentCooldownException
        extends Exception {

    /**
     * 构造一个无参数的评论冷却异常
     */
    public CommentCooldownException() {
        super();
    }

    /**
     * 构造一个带消息的评论冷却异常
     *
     * @param message 异常消息
     */
    public CommentCooldownException(String message) {
        super(message);
    }

}
