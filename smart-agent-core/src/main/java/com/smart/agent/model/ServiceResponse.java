package com.smart.agent.model;

import lombok.Data;

/**
 * Generic service response wrapper.
 *
 * @description A unified API response wrapper containing status code, message, timestamp, and generic data body, providing static factory methods for success and failure responses
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Data
public class ServiceResponse<T> {

    private int code;
    private long ts;
    private String msg;
    private T data;

    public static final int CODE_SUCCESS = 1;
    public static final int CODE_FAIL = -1;

    /**
     * Check whether the response is successful.
     *
     * @description Determines whether this request was processed successfully based on the response status code
     * @return true if successful, false if failed
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public boolean isSuccess() {
        return code == CODE_SUCCESS;
    }

    private static <S> ServiceResponse<S> buildResponse(int code, String msg, S data) {
        ServiceResponse<S> resp = new ServiceResponse<>();
        resp.setCode(code);
        resp.setMsg(msg);
        resp.setTs(System.currentTimeMillis() / 1000L);
        resp.setData(data);
        return resp;
    }

    /**
     * Build a failure response.
     *
     * @description Creates a failure response object without a data body
     * @param msg the failure message
     * @return the failure response object
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static ServiceResponse<Object> failed(String msg) {
        return buildResponse(CODE_FAIL, msg, null);
    }

    /**
     * Build a failure response with data.
     *
     * @description Creates a failure response object with a data body
     * @param msg the failure message
     * @param result the response data body
     * @return the failure response object
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static <T> ServiceResponse<T> failed(String msg, T result) {
        return buildResponse(CODE_FAIL, msg, result);
    }

    /**
     * Build a success response.
     *
     * @description Creates a success response object with a data body, defaulting the message to "success"
     * @param result the response data body
     * @return the success response object
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static <S> ServiceResponse<S> success(S result) {
        return buildResponse(CODE_SUCCESS, "success", result);
    }
}
