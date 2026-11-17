package pe.yupana.panel;

import java.util.List;

import org.springframework.stereotype.Service;

import pe.yupana.acceso.ServicioAcceso;
import pe.yupana.carga.RepositorioCargas;
import pe.yupana.conciliacion.ServicioConciliacion;
import pe.yupana.conciliacion.VistaConciliacion;
import pe.yupana.empresas.Empresa;
import pe.yupana.empresas.ServicioEmpresas;
import pe.yupana.mensajes.ServicioMensajes;

/**
 * Consulta del panel multiempresa (solo lectura). Junta datos de varios módulos sin que ellos se conozcan entre sí:
 * por eso vive en su propio paquete y nadie depende de él.
 */
@Service
public class PanelEstudio {

    private final ServicioEmpresas empresas;
    private final ServicioConciliacion conciliaciones;
    private final RepositorioCargas cargas;
    private final ServicioAcceso accesos;
    private final ServicioMensajes mensajes;

    public PanelEstudio(ServicioEmpresas empresas, ServicioConciliacion conciliaciones, RepositorioCargas cargas,
                        ServicioAcceso accesos, ServicioMensajes mensajes) {
        this.empresas = empresas;
        this.conciliaciones = conciliaciones;
        this.cargas = cargas;
        this.accesos = accesos;
        this.mensajes = mensajes;
    }

    public List<FilaPanel> filas() {
        return empresas.todas().stream().map(this::fila).toList();
    }

    private FilaPanel fila(Empresa e) {
        FilaPanel.ResumenConciliacion resumen = conciliaciones.ultima(e.id()).map(PanelEstudio::resumir).orElse(null);
        return new FilaPanel(e.id(), e.ruc(), e.razonSocial(), e.telefono(), resumen, cargas.cantidad(e.id()),
                accesos.accesosDe(e.id()), mensajes.ultimaFecha(e.id()).orElse(null));
    }

    private static FilaPanel.ResumenConciliacion resumir(VistaConciliacion c) {
        long pendientes = c.resultados().stream().filter(r -> !"CONCILIADO".equals(r.resultado()) && r.revision() == null).count();
        return new FilaPanel.ResumenConciliacion(c.id(), c.fecha(), c.periodo(), c.total(), c.conDiferencia(), c.igvRevision(), pendientes);
    }
}
