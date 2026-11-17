package pe.yupana.conciliacion;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.yupana.carga.ErrorCarga;
import pe.yupana.carga.RepositorioCargas;
import pe.yupana.dominio.Conciliador;
import pe.yupana.dominio.ResultadoComparacion;
import pe.yupana.seguridad.EventoAuditoria;
import pe.yupana.seguridad.EventoAuditoria.Tipo;

/** Casos de uso de la conciliación (tarea 2.1): ejecutar, consultar y exportar. Fachada sobre el dominio y los puertos. */
@Service
public class ServicioConciliacion {

    private final RepositorioCargas cargas;
    private final RepositorioConciliaciones conciliaciones;
    private final Conciliador conciliador;
    private final ExportadorCsv exportador;
    private final ApplicationEventPublisher eventos;

    public ServicioConciliacion(RepositorioCargas cargas, RepositorioConciliaciones conciliaciones, Conciliador conciliador,
                                ExportadorCsv exportador, ApplicationEventPublisher eventos) {
        this.cargas = cargas;
        this.conciliaciones = conciliaciones;
        this.conciliador = conciliador;
        this.exportador = exportador;
        this.eventos = eventos;
    }

    /**
     * Concilia la última carga de compras contra la última propuesta RCE de la empresa y registra el resultado.
     * Publica {@link ConciliacionEjecutada} dentro de la misma transacción (los casos heredan sus revisiones).
     */
    @Transactional
    public long conciliar(long empresaId) {
        long idCompras = ultimaCarga(empresaId, "compras");
        long idRce = ultimaCarga(empresaId, "rce");
        List<ResultadoComparacion> resultados =
                conciliador.comparar(cargas.comprobantes(idCompras), cargas.comprobantes(idRce));
        long id = conciliaciones.guardar(new NuevaConciliacion(empresaId, idCompras, idRce, resultados));
        eventos.publishEvent(new ConciliacionEjecutada(empresaId, id));
        eventos.publishEvent(new EventoAuditoria(Tipo.CONCILIACION_EJECUTADA,
                "empresa=" + empresaId + " conciliacion=" + id + " comprobantes=" + resultados.size()));
        return id;
    }

    private long ultimaCarga(long empresaId, String origen) {
        return cargas.ultimaDe(empresaId, origen)
                .orElseThrow(() -> new ErrorCarga("Primero carga ambos archivos: Compras y Propuesta RCE."));
    }

    public Optional<VistaConciliacion> detalle(long empresaId, long id) {
        return conciliaciones.detalle(empresaId, id);
    }

    public Optional<VistaConciliacion> ultima(long empresaId) {
        return conciliaciones.ultima(empresaId);
    }

    public Optional<String> csv(long empresaId, long id) {
        return conciliaciones.detalle(empresaId, id).map(exportador::generar);
    }
}
