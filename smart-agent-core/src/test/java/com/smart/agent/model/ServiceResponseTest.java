package com.smart.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceResponseTest {

    @Test
    void success_codeIs1() {
        ServiceResponse<String> resp = ServiceResponse.success("data");
        assertEquals(1, resp.getCode());
        assertTrue(resp.isSuccess());
    }

    @Test
    void success_msgIsSuccess() {
        ServiceResponse<String> resp = ServiceResponse.success("data");
        assertEquals("success", resp.getMsg());
    }

    @Test
    void success_dataPresent() {
        ServiceResponse<String> resp = ServiceResponse.success("hello");
        assertEquals("hello", resp.getData());
    }

    @Test
    void success_nullData() {
        ServiceResponse<Object> resp = ServiceResponse.success(null);
        assertNull(resp.getData());
        assertTrue(resp.isSuccess());
    }

    @Test
    void success_tsIsPositive() {
        ServiceResponse<String> resp = ServiceResponse.success("data");
        assertTrue(resp.getTs() > 0);
    }

    @Test
    void failed_codeIsNeg1() {
        ServiceResponse<Object> resp = ServiceResponse.failed("error");
        assertEquals(-1, resp.getCode());
        assertFalse(resp.isSuccess());
    }

    @Test
    void failed_msgPreserved() {
        ServiceResponse<Object> resp = ServiceResponse.failed("something went wrong");
        assertEquals("something went wrong", resp.getMsg());
    }

    @Test
    void failed_dataIsNull() {
        ServiceResponse<Object> resp = ServiceResponse.failed("error");
        assertNull(resp.getData());
    }

    @Test
    void failedWithData_dataPreserved() {
        ServiceResponse<String> resp = ServiceResponse.failed("error", "detail");
        assertEquals(-1, resp.getCode());
        assertEquals("error", resp.getMsg());
        assertEquals("detail", resp.getData());
    }

    @Test
    void success_genericType_map() {
        var data = java.util.Map.of("key", "value");
        ServiceResponse<java.util.Map<String, String>> resp = ServiceResponse.success(data);
        assertEquals("value", resp.getData().get("key"));
    }

    @Test
    void success_genericType_list() {
        var data = java.util.List.of("a", "b", "c");
        ServiceResponse<java.util.List<String>> resp = ServiceResponse.success(data);
        assertEquals(3, resp.getData().size());
    }
}
