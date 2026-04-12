package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class FileWriterNode extends AbstractNode {
    private final String filePath;
    private BufferedWriter writer;

    public FileWriterNode(String id, String filePath){
        super(id);
        this.filePath = filePath;
        addInputPort("in");
    }

    @Override
    public void initialize(){
        try {
            writer = new BufferedWriter(new FileWriter(filePath, true));
        } catch (IOException e) {
            System.out.println("[" + getId() + "] 파일 열기 실패: " + e.getMessage());
        }
        super.initialize();
    }

    @Override
    protected void onProcess(Message message) {
        if (writer == null) return;
        try {
            writer.write(message.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e){
            System.out.println("[" + getId() + "] 파일 쓰기 실패: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                System.out.println("[" + getId() + "] 파일 닫기 실패: " + e.getMessage());
            }
        }
        super.shutdown();
    }
}
