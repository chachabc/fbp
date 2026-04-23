package com.fbp.engine.network;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * stage_2 - 과제 1-2
 *
 * <p>TCP 서버 소켓을 열고, 클라이언트가 보낸 데이터를 수신할 때마다
 * {@link MessageListener#onMessage}를 호출한다. 콜백 구현체는
 * {@link MessageConverter}를 통해 FBP {@code Message}로 변환한 뒤
 * {@code "out"} 포트로 전송한다.</p>
 *
 * <pre>
 * 흐름:
 *   외부 TCP 클라이언트
 *     └─ (소켓 전송)
 *           └─ TcpReceiverNode (수신 스레드)
 *                 └─ MessageListener.onMessage(topic, bytes)   ← 콜백
 *                       └─ MessageConverter.convert()          ← 변환
 *                             └─ send("out", message)          ← FBP 파이프라인
 * </pre>
 */
public class TcpReceiverNode extends AbstractNode {
    private static final Logger log = LoggerFactory.getLogger(TcpReceiverNode.class);
    private final int listenPort;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    private final MessageListener messageListener = new MessageListener() {
        @Override
        public void onMessage(String topic, byte[] payload) {
            Message message = MessageConverter.convert(topic, payload);
            log.info("[{}] 수신 -> FBP Message: {}", getId(), message.getPayload());

            send("out", message);
        }

        @Override
        public void onConnectionLost(Throwable cause) {
            log.info("[{}] 연결 끊김: {}", getId(), cause.getMessage());
        }

        @Override
        public void onConnected() {
            log.info("[{}] 클라이언트 연결됨", getId());
        }
    };

    public TcpReceiverNode(String id, int listenPort){
        super(id);
        this.listenPort = listenPort;
        addOutputPort("out");
    }

    @Override
    public void initialize() {
        super.initialize();
        running = true;
        try {
            serverSocket = new ServerSocket(listenPort);
            log.info("[{}] TCP 수신 대기 중 - 포트 {}", getId(), listenPort);

            acceptThread = new Thread(this::acceptLoop, getId() + "-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException e){
            log.error("[{}] 서버 소켓 생성 실패: ", e.getMessage());
        }
    }

    /**
     * 클라이언트 연결 수락 루프.
     * 연결마다 별도 스레드에서 {@code readLoop()}를 실행한다.
     */
    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                messageListener.onConnected();

                String threadName = getId() + "-read" + clientSocket.getPort();
                Thread readThread = new Thread(
                        () -> readLoop(clientSocket), threadName
                );
                readThread.setDaemon(true);
                readThread.start();
            } catch (SocketException e) {
                if (running) log.error("[{}] 소켓 오류 {}", getId(), e.getMessage());
            } catch (IOException e) {
                log.error("[{}] accept 오류 {}", getId(), e.getMessage());
            }
        }
    }

    /**
     * 클라이언트 소켓에서 한 줄씩 데이터를 읽어 콜백을 호출한다.
     */
    private void readLoop(Socket socket) {
        String remoteAddr = socket.getRemoteSocketAddress().toString();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                messageListener.onMessage(
                        remoteAddr,
                        line.getBytes(StandardCharsets.UTF_8)
                );
            }
        } catch (IOException e) {
            messageListener.onConnectionLost(e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    @Override
    protected void onProcess(Message message) {

    }

    @Override
    public void shutdown() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        if (acceptThread != null) acceptThread.interrupt();
        super.shutdown();
    }
}
