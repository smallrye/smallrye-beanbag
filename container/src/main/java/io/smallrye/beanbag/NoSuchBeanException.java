package io.smallrye.beanbag;

/**
 * An exception which indicates that a required bean is not present or resolvable in the scope.
 */
public final class NoSuchBeanException extends BeanInstantiationException {
    private static final long serialVersionUID = 8066269097967059891L;

    /**
     * Constructs a new {@code NoSuchBeanException} instance.  The message is left blank ({@code null}), and no
     * cause is specified.
     */
    public NoSuchBeanException() {
    }

    /**
     * Constructs a new {@code NoSuchBeanException} instance with an initial message.  No
     * cause is specified.
     *
     * @param msg the message
     */
    public NoSuchBeanException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a new {@code NoSuchBeanException} instance with an initial cause.  If
     * a non-{@code null} cause is specified, its message is used to initialize the message of this
     * {@code NoSuchBeanException}; otherwise the message is left blank ({@code null}).
     *
     * @param cause the cause
     */
    public NoSuchBeanException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code NoSuchBeanException} instance with an initial message and cause.
     *
     * @param msg   the message
     * @param cause the cause
     */
    public NoSuchBeanException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
