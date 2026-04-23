package com.fbp.engine.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

public class ModbusTcpClient {
    private static final Logger log = LoggerFactory.getLogger(ModbusTcpClient.class);

    private static final int SOCKET_TIMEOUT_MS = 3000;
    private static final int PROTOCOL_ID = 0x0000;

    private final String host;
    private final int port;

    private Socket socket;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    private int transactionId = 0;

    public ModbusTcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        outputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        log.info("[ModbusTcpClient] 연결: {}:{}", host, port);
    }

    public void disconnection() {
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            log.warn("[ModbusTcpClient] 닫기 중 오류: {}", e.getMessage());
        }
        log.info("[ModbusTcpClient] 연결 해제");
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // ── FC 03: Read Holding Registers ─────────────────────────────────────

    /**
     * Holding Register를 읽는다 (FC 03)
     *
     * @param unitId
     * @param startAddress
     * @param quantity
     * @return 레지스터 값 배열 (unsigned 16-bit, 0~65535)
     * @throws IOException
     * @throws ModbusException
     */
    public int[] readHoldingRegisters(int unitId, int startAddress, int quantity)
        throws IOException, ModbusException {
        int txId = ++transactionId;

        //요청 프레임 조립
        byte[] frame = buildFrame(txId, unitId, dos -> {
            dos.writeByte(0x03);
            dos.writeByte(startAddress);
            dos.writeShort(quantity);
        });

        outputStream.write(frame);
        outputStream.flush();
        log.debug("[FC3] 요청 - unitId:{}, addr:{}, qty:{}", unitId, startAddress, quantity);

        int[] mbap = readMbapHeader();
        validateTransactionId(txId, mbap[0]);

        int fc = inputStream.readUnsignedByte();

        if ((fc & 0x80) != 0) {
            throw new ModbusException(fc & 0x7F, inputStream.readUnsignedByte());
        }

        int byteCount = inputStream.readUnsignedByte();
        int[] registers = new int[byteCount / 2];
        for (int i = 0; i < registers.length; i++) {
            registers[i] = inputStream.readUnsignedShort();
        }

        log.debug("[FC03] 응답 - 레지스터 {}개 수신", registers.length);
        return registers;
    }

    // ── FC 06: Write Single Register ──────────────────────────────────────

    /**
     * 단일 레지스터에 값을 쓴다.(FC 06)
     * 응답은 요청과 동일한 에코백 - 주소/값 불일치 시 ModbusException 발생.
     *
     * @param unitId
     * @param address
     * @param value
     * @throws IOException
     * @throws ModbusException
     */
    public void writeSingleRegister(int unitId, int address, int value)
        throws IOException, ModbusException {


    }

    // ── 내부 메서드 ────────────────────────────────────────────────────────

    /**
     * MBAP 헤더 7바이트를 생성한다.
     * buildMbapHeader는 buildFrame 내부에서 처리되므로 별도 노출하지 않음.
     *
     * @param txId Transaction ID
     * @param length Length 필드 값 (Unit ID 1 + PUD 길이)
     * @param unitId Unit ID
     * @return
     * @throws IOException
     */
    private byte[] buildMbapHeader(int txId, int length, int unitId) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(7);
        DataOutputStream dos = new DataOutputStream(byteArrayOutputStream);
        dos.writeShort(txId);
        dos.writeShort(PROTOCOL_ID);
        dos.writeShort(length);
        dos.writeByte(unitId);
        dos.flush();
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * MBAP 헤더 7바이트를 읽어 파싱한다.
     *
     * @return [transactionId, protocolId, length, unitId]
     * @throws IOException
     */
    private int[] readMbapHeader() throws IOException {
        int txId = inputStream.readUnsignedShort();
        int protocolId = inputStream.readUnsignedShort();
        int length = inputStream.readUnsignedShort();
        int unitId = inputStream.readUnsignedByte();
        return new int[]{txId, protocolId, length, unitId};
    }

    private byte[] buildFrame(int txId, int unitId, PduWriter pduWriter) throws IOException {
        ByteArrayOutputStream pduBaos = new ByteArrayOutputStream();
        pduWriter.write(new DataOutputStream(pduBaos));
        byte[] pdu = pduBaos.toByteArray();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(buildMbapHeader(txId, 1 + pdu.length, unitId));
        byteArrayOutputStream.write(pdu);
        return byteArrayOutputStream.toByteArray();
    }

    private void validateTransactionId(int expected, int actual) throws IOException {
        if (expected != actual) {
            throw new IOException(String.format(
                    "Transaction ID 불일치 - 요청: %d, 응답: %d", expected, actual
            ));
        }
    }

    @FunctionalInterface
    private interface PduWriter {
        void write(DataOutputStream dos) throws IOException;
    }
}
