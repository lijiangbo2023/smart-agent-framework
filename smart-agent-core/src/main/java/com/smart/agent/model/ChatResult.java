package com.smart.agent.model;

/**
 * 对话结果
 *
 * @description 封装智能对话的响应结果，包含消息ID和响应文本内容
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public record ChatResult(Long messageId, String responseText) {
}
