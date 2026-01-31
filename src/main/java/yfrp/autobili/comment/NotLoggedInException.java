package yfrp.autobili.comment;

/**
 * 未登录异常
 * <p>
 * 当被强制退出登录时抛出此异常
 */
public class NotLoggedInException
        extends CommentException {

    /**
     * 构造一个无参数的未登录异常
     */
    public NotLoggedInException() {
        super();
    }

    /**
     * 构造一个带消息的未登录异常
     *
     * @param message 异常消息
     */
    public NotLoggedInException(String message) {
        super(message);
    }

}
