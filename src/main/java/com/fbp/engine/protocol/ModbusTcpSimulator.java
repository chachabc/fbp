package com.fbp.engine.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Locale;


public class ModbusTcpSimulator {
    public static final Logger log = LoggerFactory.getLogger(ModbusTcpSimulator.class);

    private ServerSocket serverSocket;
    private final int[] registers;
    private volatile boolean running;
    private final int port;

    public ModbusTcpSimulator(int port, int registerCount){
        this.port = port;
        this.registers = new int[registerCount];
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        log.info("[Simulator] 시작 - 포트: {}, 레지스터: {}개", port, registers.length);

        Thread acceptThread = new Thread(() -> {
            while(running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log.debug("[Simulator] 클라이언트 연결: {}", clientSocket.getRemoteSocketAddress());

                    //클라이언트 별로 각 스레드에서 처리 (다중 클라이언트)
                    Thread clientThread = new Thread(
                            () -> handleClient(clientSocket),
                            "simulator-client" + clientSocket.getPort()
                    );
                    clientThread.setDaemon(true);
                    clientThread.start();
                } catch (SocketException e) {
                    if (running) log.error("[Simulator] 소켓 오류: {}", e.getMessage());
                } catch (IOException e) {
                    log.error("[Simulator] accept 오류: {}", e.getMessage());
                }
            }
        }, "simulator-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("[Simulator] 종료 중 오류: {}", e.getMessage());
        }
        log.info("[Simulator] 종료");
    }

    public void handleClient(Socket socket) {
        try (DataInputStream inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))
        ) {
            while (running && !socket.isClosed()) {
                int txId = inputStream.readUnsignedShort();
                int protocolId = inputStream.readUnsignedShort();
                int length = inputStream.readUnsignedShort();
                int unitId = inputStream.readUnsignedByte();

                int fc = inputStream.readUnsignedByte();

                log.debug("Simulator] 요청 수신 - txId:{}, FC:0x{}, length:{}",
                        txId, Integer.toHexString(fc).toUpperCase(), length);

                switch (fc) {
                    case 0x03 -> handleFc03(inputStream, outputStream, txId, unitId);
                    case 0x06 -> handleFc06(inputStream, outputStream, txId, unitId);
                    default -> sendErrorResponse(outputStream, txId, unitId, fc,
                                        ModbusException.ILLEGAL_FUNCTION);
                }
                outputStream.flush();
            }
        } catch (EOFException e) {
            log.debug("[Simulator] 클라이언트 연결 종료 (EOF)");
        } catch (IOException e) {
            if (running) log.warn("[Simulator] 클라이언트 처리 오류: {}", e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public synchronized void setRegisters(int address, int value) {
        registers[address] = value;
    }

    public synchronized int getRegister(int address) {
        return registers[address];
    }

    private void handleFc03(DataInputStream inputStream, DataOutputStream outputStream,
                            int txId, int unitId) throws IOException {
        int startAddress = inputStream.readUnsignedShort();
        int quantity = inputStream.readUnsignedShort();

        log.debug("[Simulator] Fc03 - address:{}, quantity:{}", startAddress, quantity);

        if (startAddress + quantity > registers.length) {
            sendErrorResponse(outputStream, txId, unitId, 0x03, ModbusException.ILLEGAL_DATA_ADDRESS);
            return;
        }

        int pduLength = 1 + 1 + quantity + 2;
        writeMbapHeader(outputStream, txId, 1 + pduLength, unitId);
        outputStream.writeByte(0x03);
        outputStream.writeByte(quantity * 2);

        synchronized (this) {
            for (int i = 0; i < quantity; i++) {
                outputStream.writeShort(registers[startAddress + 1]);
            }
        }
    }

    private void handleFc06(DataInputStream inputStream, DataOutputStream outputStream,
                            int txId, int unitId) throws IOException {
        int address = inputStream.readUnsignedShort();
        int value = inputStream.readUnsignedShort();

        log.debug("[Simulator] FC06 - address:{}, value:{}", address, value);

        if (address > registers.length) {
            sendErrorResponse(outputStream, txId, unitId, 0x06, ModbusException.ILLEGAL_DATA_ADDRESS);
            return;
        }

        synchronized (this) {
            registers[address] = value;
        }

        writeMbapHeader(outputStream, txId, 1 + 5, unitId);
        outputStream.writeByte(0x06);
        outputStream.writeShort(address);
        outputStream.writeShort(value);
    }

    private void sendErrorResponse(DataOutputStream outputStream, int txId, int unitId,
                                     int fc, int exceptionCode) throws IOException {
        log.warn("[Simulator] 에러 응답 - FC:0x{}, ExCode:{}",
                Integer.toHexString(fc).toUpperCase(),
                Integer.toHexString(exceptionCode).toUpperCase());

        writeMbapHeader(outputStream, txId, 1 + 2, unitId);
        outputStream.writeByte(fc | 0x80);
        outputStream.writeByte(exceptionCode);
    }

    private void writeMbapHeader(DataOutputStream outputStream, int txId,
                                 int length, int unitId) throws IOException {
        outputStream.writeShort(txId);
        outputStream.writeShort(0x0000);
        outputStream.writeShort(length);
        outputStream.writeByte(unitId);
    }
}
