package com.fbp.engine.runner.stage2.step1_network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

/**
 * TCP 에코 클라이언트.
 * 에코 서버에 접속하여 "Hello FBP"를 전송하고,
 * 서버가 돌려보낸 응답을 출력한다.
 */
public class EchoClient {
    private static final Logger log = LoggerFactory.getLogger(EchoClient.class);
    private final String host;
    private final int port;

    public EchoClient(String host, int port){
        this.host = host;
        this.port = port;
    }

    /**
     * 서버에 연결하여 메시지를 전송하고 에코 응답을 반환한다.
     *
     * @param message 전송할 메시지
     * @return 서버로부터 수신한 에코 응답
     * @throws IOException 연결 또는 I/O 오류
     */
    public String send(String message) throws IOException {
        try(Socket socket = new Socket(host, port);
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))
        ) {
            log.info("[EchoClient] 서버 연결: {} : {}", host, port);
            log.info("[EchoClient] 전송: {}", message);

            writer.println(message);

            String response = reader.readLine();
            log.info("[EchoClient] 응답: {}", response);

            return response;
        }
    }

    public static void main(String[] args) throws Exception {
        EchoClient client = new EchoClient("localhost", 9000);

        String response = client.send("Hello FBP");
        log.info("[EchoClient] 최종 수신 응답: {}", response);
    }
}
