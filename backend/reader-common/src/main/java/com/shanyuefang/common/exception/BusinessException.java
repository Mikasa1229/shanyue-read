package com.shanyuefang.common.exception;

import com.shanyuefang.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：已知的、可预期的业务错误，不打印堆栈
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
