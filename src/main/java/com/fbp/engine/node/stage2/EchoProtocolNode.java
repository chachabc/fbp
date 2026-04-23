package com.fbp.engine.node.stage2;

import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ProtocolNode를 상속하는 TCP 에코 노드.
 *
 * <p>config에서 host와 port를 읽어 에코 서버에 TCP 소켓으로 연결한다.
 * onProcess()에서 수신한 메시지를 에코 서버로 전송하고,
 * 서버가 돌려보낸 응답을 "out" 포트로 내보낸다.</p>
 *
 * <pre>
 * config 키:
 *   host (String) — 에코 서버 호스트 (기본: "localhost")
 *   port (int)    — 에코 서버 포트   (기본: 9000)
 * </pre>
 */
public class EchoProtocolNode extends ProtocolNode {
    private static final Logger log = LoggerFactory.getLogger(EchoProtocolNode.class);
    private Socket socket;
    private PrintWriter printWriter;
    private BufferedReader reader;

    public EchoProtocolNode(String id, Map<String, Object> config) {
        super(id, config);
        addInputPort("in");
        addOutputPort("out");
    }

    /**
     * config의 host, port로 TCP 소켓을 연결한다.
     * 실패하면 예외를 던져 ProtocolNode의 재연결 흐름으로 넘긴다.
     */
    @Override
    protected void connect() throws Exception {
        String host = getConfig("host") != null ? (String) getConfig("host") : "localhost";
        int port = getConfig("port") != null ? (int) getConfig("port") : 9000;

        socket = new Socket(host, port);
        printWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),
                StandardCharsets.UTF_8), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                StandardCharsets.UTF_8));

        log.info("[{}] TCP 연결 - {}:{}", getId(), host, port);
    }

    /**
     * 소켓과 스트림을 닫는다. 이미 닫혀 있어도 예외가 발생하지 않도록 null 체크.
     */
    @Override
    protected void disconnect() {
        try {
            if (printWriter != null) printWriter.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            log.warn("[{}] 소켓 닫기 중 오류: {}", getId(), e.getMessage());
        }
        log.info("[{}] TCP 연결 해제", getId());
    }

    /**
     * 수신한 FBP 메시지의 payload를 에코 서버로 전송하고,
     * 응답을 새 메시지로 만들어 "out" 포트로 내보낸다.
     */
    @Override
    protected void onProcess(Message message) {
        if (!isConnected()) {
            log.warn("[{}] 연결되지 않은 상태 - 메시지 무시", getId());
            return;
        }

        String text = message.getPayload().toString();

        try {
            printWriter.println(text);
            String response = reader.readLine();

            if (response != null) {
                log.info("[{}] 에코 응답: {}", getId(), response);
                Message responseMsg = message.withEntry("echoResponse", response);
                send("out", responseMsg);
            }
        } catch (IOException e) {
            log.error("[{}] 에코 통신 중 오류: {}", getId(), e.getMessage());
        }
    }
}
