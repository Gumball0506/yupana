package pe.yupana.uso;

/** Una respuesta de feedback para el equipo (sin sesión ni datos de quien la dio). */
public record ComentarioFeedback(String fecha, String respuesta, String comentario) {
}
