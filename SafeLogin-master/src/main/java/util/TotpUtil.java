package util;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.commons.codec.binary.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TotpUtil {
    private static final int TIME_STEP_SECONDS = 30;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int CODE_DIGITS = 6;
    private static final int TIME_SKEW = 1;

    /**
     * Generuje losowy Base32 sekret (160-bit).
     */
    public static String generateSecret() {
        byte[] buffer = new byte[20]; // 160-bit
        new SecureRandom().nextBytes(buffer);
        Base32 base32 = new Base32();
        return base32.encodeToString(buffer).replace("=", "");
    }

    /**
     * Generuje URI w formacie otpauth://
     */
    public static String generateTotpUri(String issuer, String username, String secret) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                urlEncode(issuer), urlEncode(username), secret, urlEncode(issuer), CODE_DIGITS, TIME_STEP_SECONDS);
    }

    /**
     * Generuje kod QR z podanego URI
     */
    public static String generateQrCode(String uri) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix bitMatrix = qrCodeWriter.encode(uri, BarcodeFormat.QR_CODE, 200, 200, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Błąd podczas generowania kodu QR", e);
        }
    }

    /**
     * Weryfikuje kod TOTP z marginesem czasowym
     */
    public static boolean verifyCode(String secret, String code) {
        long currentTimeStep = getCurrentTimeStep();
        for (int i = -TIME_SKEW; i <= TIME_SKEW; i++) {
            String candidate = generateCode(secret, currentTimeStep + i);
            if (candidate.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generuje kod TOTP dla danego czasu
     */
    public static String generateCode(String secret, long timeStep) {
        Base32 base32 = new Base32();
        byte[] key = base32.decode(secret);
        byte[] data = longToBytes(timeStep);
        byte[] hmac = generateHMAC(key, data);

        int offset = hmac[hmac.length - 1] & 0xF;
        int binary =
                ((hmac[offset] & 0x7f) << 24)
                        | ((hmac[offset + 1] & 0xff) << 16)
                        | ((hmac[offset + 2] & 0xff) << 8)
                        | (hmac[offset + 3] & 0xff);

        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    private static long getCurrentTimeStep() {
        return System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(TIME_STEP_SECONDS);
    }

    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    private static byte[] generateHMAC(byte[] key, byte[] data) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
            hmac.init(keySpec);
            return hmac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    private static String urlEncode(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (isUrlSafe(c)) {
                result.append(c);
            } else {
                result.append('%').append(String.format("%02X", (int) c));
            }
        }
        return result.toString();
    }

    private static boolean isUrlSafe(char c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~';
    }
}