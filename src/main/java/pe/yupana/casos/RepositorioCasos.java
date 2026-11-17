package pe.yupana.casos;

import java.util.List;
import java.util.Optional;

import pe.yupana.conciliacion.VistaRevision;

/** Puerto de salida de la revisión de casos y de los avisos del día. */
public interface RepositorioCasos {

    /** Resultado (CONCILIADO, DIFERENCIA…) del caso; vacío si no existe o es de otra empresa. */
    Optional<String> resultadoDe(long empresaId, long idResultado);

    /** Cada acción es una fila nueva: queda el historial completo. */
    void registrar(long idResultado, VistaRevision revision);

    /** Copia la última revisión de cada caso de la conciliación anterior que siga igual (misma llave y misma causa). */
    void heredarRevisiones(long empresaId, long idConciliacion);

    long guardarAviso(long idConciliacion, String fecha, String asunto, String cuerpo);

    /** Los 10 más recientes de la empresa. */
    List<VistaAviso> avisos(long empresaId);
}
