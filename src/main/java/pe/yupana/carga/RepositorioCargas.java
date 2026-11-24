package pe.yupana.carga;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.yupana.dominio.Comprobante;

/** Puerto de salida: lo que la aplicación necesita guardar y consultar de las importaciones de cada empresa. */
public interface RepositorioCargas {

    int TODAS_LAS_FILAS = Integer.MAX_VALUE;

    long guardar(long empresaId, String origen, String nombreArchivo, Lote lote, byte[] contenido, String periodo);
    Optional<ArchivoImportado> archivo(long empresaId, long id);

    /** Vacío si la importación no existe o es de otra empresa. */
    Optional<VistaImportacion> detalle(long empresaId, long id, int limiteFilas);

    /** Última importación de cada origen, con vista previa de 10 filas. */
    Map<String, VistaImportacion> ultimas(long empresaId, String periodo);

    List<ResumenImportacion> historial(long empresaId);

    List<Comprobante> comprobantes(long idImportacion);

    Optional<Long> ultimaDe(long empresaId, String origen);

    Optional<Long> ultimaDe(long empresaId, String origen, String periodo);
    long cantidad(long empresaId);

    int borrarDeEmpresa(long empresaId);
}
