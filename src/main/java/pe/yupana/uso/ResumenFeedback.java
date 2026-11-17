package pe.yupana.uso;

import java.util.List;
import java.util.Map;

/** Conteo por respuesta (si / mas_o_menos / no) y los últimos 20 comentarios. */
public record ResumenFeedback(Map<String, Long> respuestas, List<ComentarioFeedback> comentarios) {
}
