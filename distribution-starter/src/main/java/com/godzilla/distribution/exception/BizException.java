package com.godzilla.distribution.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private String message;
    private int code;

    public BizException(String message, int code) {
        super(message);
        this.message = message;
        this.code = code;
    }
}
