package pe.yupana.uso;

import java.util.Optional;
import java.util.Set;

/**
 * Feedback anónimo (tarea 3.3): «¿Te quedó claro qué casos revisar?». Como los eventos, sobrevive a «Reiniciar demo».
 * Se pide no escribir información tributaria real; el comentario se guarda tal cual, sin datos de quien lo escribe.
 */
public record Feedback(String sesion, String respuesta, String comentario) {

    static final Set<String> RESPUESTAS = Set.of("si", "mas_o_menos", "no");
    private static final int COMENTARIO_MAXIMO = 500;

    public static Optional<Feedback> validar(String sesion, String respuesta, String comentario) {
        String limpio = comentario == null || comentario.isBlank() ? null : comentario.strip();
        boolean valido = sesion != null && sesion.matches("[a-f0-9-]{8,36}") && respuesta != null && RESPUESTAS.contains(respuesta)
                && (limpio == null || limpio.length() <= COMENTARIO_MAXIMO);
        return valido ? Optional.of(new Feedback(sesion, respuesta, limpio)) : Optional.empty();
    }
}
