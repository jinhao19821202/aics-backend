package com.aics.common;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BizException(String msg) {
        this(400, msg);
    }

    public static BizException of(String msg) { return new BizException(400, msg); }
    public static BizException forbidden(String msg) { return new BizException(403, msg); }
    public static BizException notFound(String msg) { return new BizException(404, msg); }
    public static BizException conflict(String msg) { return new BizException(409, msg); }
}
