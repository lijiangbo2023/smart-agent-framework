package com.smart.agent.model;

/**
 * 卡片投递结果
 *
 * @description 封装卡片投递操作的结果信息，包含外部跟踪ID和流程查询键
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public record CardDeliveryResult(String outTrackId, String processQueryKey) {
}
