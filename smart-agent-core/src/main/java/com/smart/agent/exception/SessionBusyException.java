package com.smart.agent.exception;

/**
 * Exception thrown when a session is busy processing another request.
 *
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
public class SessionBusyException extends RuntimeException {

    public SessionBusyException(String message) {
        super(message);
    }
}
