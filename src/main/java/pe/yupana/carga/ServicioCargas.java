package pe.yupana.carga;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.yupana.config.PropiedadesYupana;
import pe.yupana.seguridad.AccesoDenegado;
import pe.yupana.seguridad.EventoAuditoria;
import pe.yupana.seguridad.EventoAuditoria.Tipo;

/** Casos de uso de la carga (tarea 1.2): recibir, validar, guardar y consultar, siempre dentro de una empresa. */
@Service
public class ServicioCargas {

    private final FabricaLectores lectores;
    private final ValidadorLote validador;
    private final RepositorioCargas repositorio;
    private final PropiedadesYupana propiedades;
    private final ApplicationEventPublisher eventos;

    public ServicioCargas(FabricaLectores lectores, ValidadorLote validador, RepositorioCargas repositorio,
                          PropiedadesYupana propiedades, ApplicationEventPublisher eventos) {
        this.lectores = lectores;
        this.validador = validador;
        this.repositorio = repositorio;
        this.propiedades = propiedades;
        this.eventos = eventos;
    }

    /** Una transacción: o se guarda todo el lote o nada, y nadie ve una carga a medias. */
    @Transactional
    public long importar(long empresaId, String origen, String nombreArchivo, byte[] contenido) {
        try {
            exigirOrigenValido(origen);
            Lote lote = validador.validar(lectores.para(nombreArchivo).leer(contenido));
            long id = repositorio.guardar(empresaId, origen, nombreArchivo, lote);
            eventos.publishEvent(new EventoAuditoria(Tipo.CARGA_ACEPTADA, "empresa=" + empresaId + " origen=" + origen
                    + " archivo=" + nombreArchivo + " filas=" + lote.comprobantes().size()));
            return id;
        } catch (ErrorCarga e) {
            eventos.publishEvent(new EventoAuditoria(Tipo.CARGA_RECHAZADA, "empresa=" + empresaId + " origen=" + origen
                    + " archivo=" + nombreArchivo + " errores=" + e.errores().size()));
            throw e;
        }
    }

    private static void exigirOrigenValido(String origen) {
        if (!EsquemaArchivo.ORIGENES.containsKey(origen)) throw new ErrorCarga("Origen inválido: usa 'compras' o 'rce'.");
    }

    public Optional<VistaImportacion> detalle(long empresaId, long id, int limiteFilas) {
        return repositorio.detalle(empresaId, id, limiteFilas);
    }

    public Map<String, VistaImportacion> ultimas(long empresaId) {
        return repositorio.ultimas(empresaId);
    }

    public List<ResumenImportacion> historial(long empresaId) {
        return repositorio.historial(empresaId);
    }

    /**
     * Reiniciar demo (tarea 4.1-A) de UNA empresa: borra sus cargas (en cascada, conciliaciones, casos y avisos).
     * Publica {@link DemoReiniciada} para que el chat y el simulador SIRE limpien lo suyo sin que este servicio los conozca.
     */
    @Transactional
    public int reiniciar(long empresaId) {
        if (!propiedades.reinicioHabilitado()) {
            eventos.publishEvent(new EventoAuditoria(Tipo.REINICIO_DENEGADO, "empresa=" + empresaId));
            throw new AccesoDenegado("«Reiniciar demo» está deshabilitado en este entorno.");
        }
        int borradas = repositorio.borrarDeEmpresa(empresaId);
        eventos.publishEvent(new DemoReiniciada(empresaId));
        eventos.publishEvent(new EventoAuditoria(Tipo.DEMO_REINICIADA, "empresa=" + empresaId + " importaciones borradas=" + borradas));
        return borradas;
    }
}
