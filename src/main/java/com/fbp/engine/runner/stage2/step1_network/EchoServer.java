package com.fbp.engine.runner.stage2.step1_network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * TCP 에코 서버.
 * 클라이언트가 보낸 문자열을 그대로 돌려보낸다.
 * 포트 9000에서 단일 클라이언트 연결을 처리한다.
 */
public class EchoServer {
    private static final Logger log = LoggerFactory.getLogger(EchoServer.class);
    private final int port;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public EchoServer(int port){
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        log.info("[EchoServer] 시작됨 - 포트 {} 대기 중", port);
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                log.info("[EchoServer] 클라이언트 연결: {}",
                        clientSocket.getRemoteSocketAddress());
                handleClient(clientSocket);
            } catch (SocketException e) {
                if (running) {
                    log.error("[EchoServer] 소켓 오류: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 클라이언트 소켓을 처리한다.
     * 수신한 각 줄을 그대로 돌려보낸다.
     */
    private void handleClient(Socket clientSocket) {
        Thread clinetThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter writer = new PrintWriter(
                         new OutputStreamWriter(clientSocket.getOutputStream()), true
                 )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[EchoServer] 수신: {}", line);
                    writer.println(line);
                    log.info("[EchoServer] 에코: {}", line);
                }
            } catch (IOException e) {
                log.error("[EchoServer] 클라이언트 처리 중 오류: {}", e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.error("[EchoServer] 클라이언트 종료 중 오류: {}", e.getMessage());
                }
            }
        });
        clinetThread.setDaemon(true);
        clinetThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("[EchoServer] 종료 중 오류: {}", e.getMessage());
        }
        log.info("[EchoServer] 종료 됨");
    }

    public static void main(String[] args) throws Exception {
        EchoServer server = new EchoServer(9000);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }
}
