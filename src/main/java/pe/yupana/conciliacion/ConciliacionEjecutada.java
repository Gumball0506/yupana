package pe.yupana.conciliacion;

/** Se guardó una conciliación nueva. Quien escucha (los casos) puede completar datos sin que este módulo lo conozca. */
public record ConciliacionEjecutada(long empresaId, long idConciliacion) {
}
