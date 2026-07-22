package com.devops.assistant.safety;

/** 指令未通過安全驗證時拋出。 */
public class UnsafeCommandException extends RuntimeException {
    public UnsafeCommandException(String message) {
        super(message);
    }
}
