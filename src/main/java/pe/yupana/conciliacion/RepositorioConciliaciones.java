package pe.yupana.conciliacion;

import java.util.Optional;

/** Puerto de salida de la conciliación: guardar cada ejecución con su origen y causa, y consultarla por empresa. */
public interface RepositorioConciliaciones {

    long guardar(NuevaConciliacion conciliacion);

    /** Vacío si la conciliación no existe o es de otra empresa. */
    Optional<VistaConciliacion> detalle(long empresaId, long id);

    Optional<VistaConciliacion> ultima(long empresaId);
}
