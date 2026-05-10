package com.fbp.engine.registry;

public class NodeRegistryException extends RuntimeException {

    public NodeRegistryException(String message) {
        super(message);
    }

    public NodeRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
