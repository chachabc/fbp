package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 수신한 메시지를 지정된 파일에 한 줄씩 기록하는 종단 노드.
 * 파일은 append 모드로 열리며, 자원({@link java.io.BufferedWriter})을 생명주기({@code initialize}/{@code shutdown})로 관리한다.
 * OutputPort가 없으며, 주로 {@link ThresholdFilterNode}의 {@code "normal"} 포트에 연결하여 정상 데이터를 저장하는 용도로 사용한다.
 */
public class FileWriterNode extends AbstractNode {
    private static final Logger log = LoggerFactory.getLogger(FileWriterNode.class);
    private final String filePath;
    private BufferedWriter writer;

    /**
     * FileWriterNode를 생성한다.
     *
     * @param id       노드의 고유 식별자
     * @param filePath 기록할 파일의 경로
     */
    public FileWriterNode(String id, String filePath){
        super(id);
        this.filePath = filePath;
        addInputPort("in");
    }

    /**
     * 지정된 파일을 append 모드로 열어 쓰기를 준비한다.
     * 파일 열기에 실패하면 경고를 출력하고 이후 쓰기 작업을 건너뛴다.
     */
    @Override
    public void initialize(){
        try {
            writer = new BufferedWriter(new FileWriter(filePath, true));
        } catch (IOException e) {
            log.info("[{}] 파일 열기 실패: {}", getId(), e.getMessage());
        }
        super.initialize();
    }

    /**
     * 메시지의 {@code toString()} 결과를 파일에 한 줄 기록하고 즉시 flush한다.
     * {@code initialize()} 이전이거나 {@code shutdown()} 이후에는 쓰기를 건너뛴다.
     *
     * @param message 파일에 기록할 메시지
     */
    @Override
    protected void onProcess(Message message) {
        if (writer == null) return;
        try {
            writer.write(message.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e){
            log.info("[{}] 파일 쓰기 실패: {}", getId(), e.getMessage());
        }
    }

    /**
     * 파일 핸들을 닫고 노드를 종료한다.
     */
    @Override
    public void shutdown() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.info("[{}] 파일 닫기 실패: {}", getId(), e.getMessage());
            }
        }
        super.shutdown();
    }
}
