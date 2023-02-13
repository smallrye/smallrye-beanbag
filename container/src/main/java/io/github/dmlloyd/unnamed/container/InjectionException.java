package io.github.dmlloyd.unnamed.container;

import java.io.Serial;

/**
 * An exception indicating that a failure occurred specifically with injection.
 */
public class InjectionException extends BeanInstantiationException {
    @Serial
    private static final long serialVersionUID = 4150858056742398287L;

    /**
     * Constructs a new {@code InjectionException} instance.  The message is left blank ({@code null}), and no
     * cause is specified.
     */
    public InjectionException() {
    }

    /**
     * Constructs a new {@code InjectionException} instance with an initial message.  No
     * cause is specified.
     *
     * @param msg the message
     */
    public InjectionException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a new {@code InjectionException} instance with an initial cause.  If
     * a non-{@code null} cause is specified, its message is used to initialize the message of this
     * {@code InjectionException}; otherwise the message is left blank ({@code null}).
     *
     * @param cause the cause
     */
    public InjectionException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code InjectionException} instance with an initial message and cause.
     *
     * @param msg   the message
     * @param cause the cause
     */
    public InjectionException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
