package com.smart.agent.model;

/**
 * Chat result.
 *
 * @description Encapsulates the response result of an intelligent conversation, containing the message ID and the response text content
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public record ChatResult(Long messageId, String responseText) {
}
