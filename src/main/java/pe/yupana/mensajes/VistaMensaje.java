package pe.yupana.mensajes;

/** Un mensaje del chat simulado. SALIENTE = lo escribió el estudio; ENTRANTE = lo respondió la MYPE. */
public record VistaMensaje(long id, String direccion, String texto, String fecha, String usuario) {
}
