package com.fbp.engine.protocol;

/**
 * MODBUS 에러 응답을 표현하는 예외 클래스.
 *
 * <p>슬레이브가 에러 응답(FC | 0x80  + Exception Code)을 보낼 때 발생한다.</p>
 */
public class ModbusException extends RuntimeException {
    public static final int ILLEGAL_FUNCTION = 0x01;
    public static final int ILLEGAL_DATA_ADDRESS = 0x02;
    public static final int ILLEGAL_DATA_VALUE = 0x03;
    public static final int SLAVE_DEVICE_FAILURE = 0x04;

    private final int functionCode;
    private final int exceptionCode;

    public ModbusException(int functionCode, int exceptionCode) {
        this.functionCode = functionCode;
        this.exceptionCode = exceptionCode;
    }

    public ModbusException(String message) {
        super(message);
        this.functionCode = -1;
        this.exceptionCode = -1;
    }

    @Override
    public String getMessage() {
        if (functionCode == -1) return super.getMessage();
        return String.format("MODBUS 에러 - FC: 0x%02X, Exception: 0x%02X (%s)",
                functionCode, exceptionCode, describeExceptionCode(exceptionCode));
    }

    public int getFunctionCode() {
        return functionCode;
    }

    public int getExceptionCode() {
        return exceptionCode;
    }

    private static String describeExceptionCode(int exceptionCode) {
        return switch (exceptionCode) {
            case ILLEGAL_FUNCTION -> "Illegal Function";
            case ILLEGAL_DATA_ADDRESS -> "Illegal Data Address";
            case ILLEGAL_DATA_VALUE -> "Illegal Data Value";
            case SLAVE_DEVICE_FAILURE -> "Slave Device Failure";
            default -> "Unknown";
        };
    }
}
