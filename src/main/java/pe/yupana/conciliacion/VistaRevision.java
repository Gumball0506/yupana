package pe.yupana.conciliacion;

/** Última decisión del contador sobre un caso: CONFIRMADO u OBSERVADO, con nota opcional, fecha y quién la tomó. */
public record VistaRevision(String accion, String nota, String fecha, String usuario) {
}
