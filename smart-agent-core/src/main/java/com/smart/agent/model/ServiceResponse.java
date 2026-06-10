package com.smart.agent.model;

import lombok.Data;

/**
 * 通用服务响应包装类
 *
 * @description 统一的API响应封装，包含状态码、提示信息、时间戳和泛型数据体，提供成功与失败的静态构造方法
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
     * 判断响应是否成功
     *
     * @description 根据响应状态码判断本次请求是否处理成功
     * @return true表示成功，false表示失败
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
     * 构建失败响应
     *
     * @description 创建一个不携带数据体的失败响应对象
     * @param msg 失败提示信息
     * @return 失败响应对象
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static ServiceResponse<Object> failed(String msg) {
        return buildResponse(CODE_FAIL, msg, null);
    }

    /**
     * 构建携带数据的失败响应
     *
     * @description 创建一个携带数据体的失败响应对象
     * @param msg 失败提示信息
     * @param result 响应数据体
     * @return 失败响应对象
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static <T> ServiceResponse<T> failed(String msg, T result) {
        return buildResponse(CODE_FAIL, msg, result);
    }

    /**
     * 构建成功响应
     *
     * @description 创建一个携带数据体的成功响应对象，提示信息默认为"success"
     * @param result 响应数据体
     * @return 成功响应对象
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static <S> ServiceResponse<S> success(S result) {
        return buildResponse(CODE_SUCCESS, "success", result);
    }
}
