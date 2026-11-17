package pe.yupana.dominio;

import java.text.Normalizer;

/** Utilidades de texto sin estado. Todo lo que llega de un archivo pasa por aquí antes de mostrarse o guardarse. */
public final class Textos {

    private static final int LARGO_MAXIMO_NOMBRE = 255;

    private Textos() {}

    public static String sinTildes(String s) {
        return Normalizer.normalize(s.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Recorta un valor ajeno antes de repetirlo en un mensaje (evita eco de textos enormes). */
    public static String acortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Solo el nombre del archivo, sin carpetas ni caracteres de control (evita ruta falsa e inyección en bitácoras). */
    public static String nombreSeguro(String crudo) {
        if (crudo == null) return "archivo";
        String nombre = crudo.substring(Math.max(crudo.lastIndexOf('/'), crudo.lastIndexOf('\\')) + 1)
                .replaceAll("[\\p{Cntrl}]", "").strip();
        if (nombre.isEmpty()) return "archivo";
        return nombre.length() > LARGO_MAXIMO_NOMBRE ? nombre.substring(0, LARGO_MAXIMO_NOMBRE) : nombre;
    }

    /** Quita saltos de línea y controles: un valor del usuario nunca debe poder fabricar una línea de bitácora. */
    public static String paraBitacora(String s) {
        return s == null ? "" : s.replaceAll("[\\p{Cntrl}]", " ");
    }
}
