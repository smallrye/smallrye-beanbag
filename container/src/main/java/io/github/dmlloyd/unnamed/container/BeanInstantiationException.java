package io.github.dmlloyd.unnamed.container;

import java.io.Serial;

/**
 *
 */
public class BeanInstantiationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 8313488012855336482L;

    /**
     * Constructs a new {@code BeanInstantiationException} instance.  The message is left blank ({@code null}), and no
     * cause is specified.
     */
    public BeanInstantiationException() {
    }

    /**
     * Constructs a new {@code BeanInstantiationException} instance with an initial message.  No
     * cause is specified.
     *
     * @param msg the message
     */
    public BeanInstantiationException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a new {@code BeanInstantiationException} instance with an initial cause.  If
     * a non-{@code null} cause is specified, its message is used to initialize the message of this
     * {@code BeanInstantiationException}; otherwise the message is left blank ({@code null}).
     *
     * @param cause the cause
     */
    public BeanInstantiationException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code BeanInstantiationException} instance with an initial message and cause.
     *
     * @param msg   the message
     * @param cause the cause
     */
    public BeanInstantiationException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
