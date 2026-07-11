package com.smart.agent.exception;

/**
 * Exception thrown when API rate limit is exceeded.
 *
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
