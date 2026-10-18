import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// Protocol fixture: same AES/GCM parameters and AAD as Android Crypto.kt.
class CryptoInterop {
    public static void main(String[] args) throws Exception {
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var decoder = Base64.getDecoder();
        var encoder = Base64.getEncoder();
        var key = new SecretKeySpec(decoder.decode(input.readLine()), "AES");
        var aad = input.readLine().getBytes(StandardCharsets.UTF_8);
        var nonce = decoder.decode(input.readLine());
        var ciphertext = decoder.decode(input.readLine());
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        var plaintext = cipher.doFinal(ciphertext);
        System.out.println(encoder.encodeToString(plaintext));
        var nextNonce = new byte[12]; new SecureRandom().nextBytes(nextNonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nextNonce));
        cipher.updateAAD(aad);
        System.out.println(encoder.encodeToString(nextNonce));
        System.out.println(encoder.encodeToString(cipher.doFinal(plaintext)));
    }
}
