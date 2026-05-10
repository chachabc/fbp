package com.fbp.engine.parser;

import java.io.InputStream;

public interface FlowParser {

    FlowDefinition parse(String json);

    FlowDefinition parse(InputStream input);
}