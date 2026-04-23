package com.fbp.engine.runner.stage2.step3_modbus;

import com.fbp.engine.node.stage1.PrintNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 과제 3-1: MODBUS TCP 프레임을 수작업으로 조립하고 해석하는 연습
//
// 시나리오:
//   A. 슬레이브 1, 주소 10번부터 Holding Register 5개 읽기 (FC 03)
//   B. 슬레이브 1, 주소 5번에 값 1234 쓰기 (FC 06)
//   C. 읽기 응답: 레지스터 5개 값이 [100, 200, 300, 400, 500]
public class ModbusFramePractice {
    private static final Logger log = LoggerFactory.getLogger(ModbusFramePractice.class);

    public static void main(String[] args) {
        printFc03Request();
        printFc06Request();
        printFc03Response();
        verifyFrameLengths();
    }

    // ── A. FC 03 요청 프레임: 슬레이브 1, 주소 10부터 5개 읽기 ──────────────
    static byte[] buildFc03Request() {
        return new byte[] {
                // ── MBAP 헤더 (7바이트) ──────────────────────────────────────────
                (byte) 0x00, (byte) 0x01,  // [0~1] Transaction ID = 1 (요청/응답 쌍 식별)
                (byte) 0x00, (byte) 0x00,  // [2~3] Protocol ID = 0x0000 (항상 MODBUS)
                (byte) 0x00, (byte) 0x06,  // [4~5] Length = 6 (이후 바이트 수: Unit ID 1 + PDU 5)
                (byte) 0x01,               // [6]   Unit ID = 1 (슬레이브 ID)

                // ── PDU (5바이트) ────────────────────────────────────────────────
                (byte) 0x03,               // [7]   Function Code = 0x03 (Read Holding Registers)
                (byte) 0x00, (byte) 0x0A,  // [8~9] Start Address = 10 (0x000A)
                (byte) 0x00, (byte) 0x05   // [10~11] Quantity = 5 (읽을 레지스터 개수)
        };
        // 전체 12바이트 = MBAP 7 + PDU 5
    }

    // ── B. FC 06 요청 프레임: 슬레이브 1, 주소 5번에 값 1234 쓰기 ──────────
    static byte[] buildFc06Request() {
        // 1234 = 0x04D2
        return new byte[] {
                // ── MBAP 헤더 (7바이트) ──────────────────────────────────────────
                (byte) 0x00, (byte) 0x02,  // [0~1] Transaction ID = 2
                (byte) 0x00, (byte) 0x00,  // [2~3] Protocol ID = 0x0000
                (byte) 0x00, (byte) 0x06,  // [4~5] Length = 6 (Unit ID 1 + PDU 5)
                (byte) 0x01,               // [6]   Unit ID = 1

                // ── PDU (5바이트) ────────────────────────────────────────────────
                (byte) 0x06,               // [7]   Function Code = 0x06 (Write Single Register)
                (byte) 0x00, (byte) 0x05,  // [8~9] Register Address = 5
                (byte) 0x04, (byte) 0xD2   // [10~11] Value = 1234 (0x04D2)
        };
        // 전체 12바이트 = MBAP 7 + PDU 5
        // FC 06 응답은 요청과 동일한 내용을 에코백 (Transaction ID만 동일)
    }

    // ── C. FC 03 응답 프레임: 레지스터 5개 값 [100, 200, 300, 400, 500] ─────
    static byte[] buildFc03Response() {
        //  100 = 0x0064
        //  200 = 0x00C8
        //  300 = 0x012C
        //  400 = 0x0190
        //  500 = 0x01F4
        return new byte[] {
                // ── MBAP 헤더 (7바이트) ──────────────────────────────────────────
                (byte) 0x00, (byte) 0x01,  // [0~1] Transaction ID = 1 (요청과 동일)
                (byte) 0x00, (byte) 0x00,  // [2~3] Protocol ID = 0x0000
                (byte) 0x00, (byte) 0x0D,  // [4~5] Length = 13 (Unit ID 1 + FC 1 + ByteCount 1 + Data 10)
                (byte) 0x01,               // [6]   Unit ID = 1

                // ── PDU ──────────────────────────────────────────────────────────
                (byte) 0x03,               // [7]   Function Code = 0x03
                (byte) 0x0A,               // [8]   Byte Count = 10 (레지스터 5개 × 2바이트)
                (byte) 0x00, (byte) 0x64,  // [9~10]  Register 0 (주소 10) = 100
                (byte) 0x00, (byte) 0xC8,  // [11~12] Register 1 (주소 11) = 200
                (byte) 0x01, (byte) 0x2C,  // [13~14] Register 2 (주소 12) = 300
                (byte) 0x01, (byte) 0x90,  // [15~16] Register 3 (주소 13) = 400
                (byte) 0x01, (byte) 0xF4   // [17~18] Register 4 (주소 14) = 500
        };
        // 전체 19바이트 = MBAP 7 + FC 1 + ByteCount 1 + Data(5×2) 10
    }

    // ── 출력 / 검증 ───────────────────────────────────────────────────────────

    static void printFc03Request() {
        byte[] frame = buildFc03Request();
        log.info("=== A. FC 03 요청 — 슬레이브1, 주소10부터 5개 읽기 ===");
        printFrame(frame);
        log.info("Transaction ID : {}", toUnsignedShort(frame, 0));
        log.info("Protocol ID    : 0x{}", String.format("%04X", toUnsignedShort(frame, 2)));
        log.info("Length         : {}", toUnsignedShort(frame, 4));
        log.info("Unit ID        : {}", frame[6] & 0xFF);
        log.info("Function Code  : 0x{}", String.format("%02X", frame[7] & 0xFF));
        log.info("Start Address  : {}", toUnsignedShort(frame, 8));
        log.info("Quantity       : {}", toUnsignedShort(frame, 10));
    }

    static void printFc06Request() {
        byte[] frame = buildFc06Request();
        log.info("=== B. FC 06 요청 — 슬레이브1, 주소5에 1234 쓰기 ===");
        printFrame(frame);
        log.info("Transaction ID     : {}", toUnsignedShort(frame, 0));
        log.info("Protocol ID        : 0x{}", String.format("%04X", toUnsignedShort(frame, 2)));
        log.info("Length             : {}", toUnsignedShort(frame, 4));
        log.info("Unit ID            : {}", frame[6] & 0xFF);
        log.info("Function Code      : 0x{}", String.format("%02X", frame[7] & 0xFF));
        log.info("Register Address   : {}", toUnsignedShort(frame, 8));
        log.info("Value              : {} (0x{})", toUnsignedShort(frame, 10), String.format("%04X", toUnsignedShort(frame, 10)));}

    static void printFc03Response() {
        byte[] frame = buildFc03Response();
        log.info("=== C. FC 03 응답 — 레지스터 5개: [100, 200, 300, 400, 500] ===");
        printFrame(frame);
        log.info("Transaction ID : {}", toUnsignedShort(frame, 0));
        log.info("Length         : {}", toUnsignedShort(frame, 4));
        log.info("Function Code  : 0x{}", String.format("%02X", frame[7] & 0xFF));
        log.info("Byte Count     : {}", frame[8] & 0xFF);

        int registerCount = (frame[8] & 0xFF) / 2;
        StringBuilder registers = new StringBuilder("Register Values: ");
        for (int i = 0; i < registerCount; i++) {
            registers.append(toUnsignedShort(frame, 9 + i * 2)).append(" ");
        }
        log.info(registers.toString());
    }

    static void verifyFrameLengths() {
        log.info("=== 프레임 크기 검증 ===");
        log.info("FC03 요청: {}바이트 (기대: 12)", buildFc03Request().length);
        log.info("FC06 요청: {}바이트 (기대: 12)", buildFc06Request().length);
        log.info("FC03 응답: {}바이트 (기대: 19)", buildFc03Response().length);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    static void printFrame(byte[] frame) {
        StringBuilder sb = new StringBuilder("HEX: ");
        for (byte b : frame) {
            sb.append(String.format("%02X ", b));
        }
        log.info(sb.toString());
    }

    static int toUnsignedShort(byte[] buf, int offset) {
        return ((buf[offset] & 0xFF) << 8) | (buf[offset + 1] & 0xFF);
    }
}
