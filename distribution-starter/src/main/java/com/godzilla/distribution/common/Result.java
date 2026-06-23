package com.godzilla.distribution.common;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    public Result(int code) {
        this.code = code;
    }

    public Result(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Result(int code, T data) {
        this.code = code;
        this.data = data;
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), data);
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode());
    }

    public static <T> Result<T> fail() {
        return new Result(ResultCode.FAIL.getCode());
    }

    public static <T> Result<T> authFail() {
        return new Result(ResultCode.AUTH_FAIL.getCode(), ResultCode.AUTH_FAIL.getMsg());
    }

    public static <T> Result<T> loginFail() {
        return new Result<>(20002, "未登录或登录已过期");
    }
}
