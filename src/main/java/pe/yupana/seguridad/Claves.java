package pe.yupana.seguridad;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Claves de usuario (ISO 27001 A.5.17 información de autenticación; A.8.24 criptografía).
 * Se guarda «pbkdf2$iteraciones$sal$hash», nunca la clave. La sal es aleatoria: dos usuarios con la misma clave
 * no comparten hash.
 */
public final class Claves {

    private static final int ITERACIONES = 210_000; // recomendación OWASP para PBKDF2-HMAC-SHA256
    private static final SecureRandom AZAR = new SecureRandom();

    private Claves() {}

    public static String cifrar(String clave) {
        byte[] sal = new byte[16];
        AZAR.nextBytes(sal);
        return "pbkdf2$" + ITERACIONES + "$" + b64(sal) + "$" + b64(pbkdf2(clave, sal, ITERACIONES));
    }

    public static boolean coincide(String clave, String guardada) {
        String[] partes = guardada.split("\\$");
        if (partes.length != 4 || !partes[0].equals("pbkdf2")) return false;
        byte[] esperado = Base64.getDecoder().decode(partes[3]);
        // Comparación en tiempo constante: no revela cuántos bytes coinciden
        return MessageDigest.isEqual(esperado, pbkdf2(clave, Base64.getDecoder().decode(partes[2]), Integer.parseInt(partes[1])));
    }

    private static byte[] pbkdf2(String clave, byte[] sal, int iteraciones) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(clave.toCharArray(), sal, iteraciones, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no disponible en esta JVM", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
