package com.smart.agent.model;

/**
 * Card delivery result.
 *
 * @description Encapsulates the result of a card delivery operation, containing the external tracking ID and the process query key
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public record CardDeliveryResult(String outTrackId, String processQueryKey) {
}
