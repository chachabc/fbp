package com.fbp.engine.parser;

public class FlowParserException extends RuntimeException {

    public FlowParserException(String message) {
        super(message);
    }

    public FlowParserException(String message, Throwable cause) {
        super(message, cause);
    }
}