package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class FileWriterNodeTest {
    private FileWriterNode fileWriterNode;
    private Path testFilePath;

    @BeforeEach
    void setUp() throws IOException {
        testFilePath = Files.createTempFile("test-output", "log");
        fileWriterNode = new FileWriterNode("file-writer", testFilePath.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(testFilePath);
    }

    @Test
    @DisplayName("파일 생성")
    void test1(){
        fileWriterNode.initialize();
        fileWriterNode.shutdown();
        Assertions.assertTrue(Files.exists(testFilePath));
    }

    @Test
    @DisplayName("내용 기록")
    void test2() throws IOException {
        fileWriterNode.initialize();
        fileWriterNode.process(new Message(Map.of("temperature", 25.0)));
        fileWriterNode.process(new Message(Map.of("temperature", 35.0)));
        fileWriterNode.process(new Message(Map.of("temperature", 30.0)));
        fileWriterNode.shutdown();

        List<String> lines = Files.readAllLines(testFilePath);
        Assertions.assertEquals(3, lines.size());
    }

    @Test
    @DisplayName("shutdown 후 파일 닫힘")
    void test3() throws IOException {
        fileWriterNode.initialize();
        fileWriterNode.process(new Message(Map.of("temperature", 25.0)));
        fileWriterNode.shutdown();

        fileWriterNode.process(new Message(Map.of("temperature", 30.0)));

        List<String> lines = Files.readAllLines(testFilePath);
        Assertions.assertEquals(1, lines.size());
    }


}
