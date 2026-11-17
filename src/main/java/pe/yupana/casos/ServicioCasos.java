package pe.yupana.casos;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.yupana.conciliacion.ConciliacionEjecutada;
import pe.yupana.conciliacion.ServicioConciliacion;
import pe.yupana.conciliacion.VistaConciliacion;
import pe.yupana.conciliacion.VistaRevision;
import pe.yupana.dominio.AccionCaso;
import pe.yupana.dominio.Fechas;
import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.seguridad.EventoAuditoria;
import pe.yupana.seguridad.EventoAuditoria.Tipo;

/** Tarea 3.1: el contador revisa cada caso que no cuadra (Confirmar / Observar) y la demo simula el aviso del día. */
@Service
public class ServicioCasos {

    private static final int NOTA_MAXIMA = 300;

    private final RepositorioCasos repositorio;
    private final ServicioConciliacion conciliaciones;
    private final ApplicationEventPublisher eventos;

    public ServicioCasos(RepositorioCasos repositorio, ServicioConciliacion conciliaciones, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.conciliaciones = conciliaciones;
        this.eventos = eventos;
    }

    /** Solo cambia un estado interno de la demo: no presenta nada a SUNAT. */
    @Transactional
    public VistaRevision revisar(long empresaId, long idResultado, String verbo, String nota, String usuario) {
        AccionCaso accion = AccionCaso.desde(verbo);
        String limpia = nota == null || nota.isBlank() ? null : nota.strip();
        if (limpia != null && limpia.length() > NOTA_MAXIMA)
            throw new ReglaIncumplida("La nota admite hasta " + NOTA_MAXIMA + " caracteres.");
        String resultado = repositorio.resultadoDe(empresaId, idResultado)
                .orElseThrow(() -> new ReglaIncumplida("Caso no encontrado."));
        if ("CONCILIADO".equals(resultado)) throw new ReglaIncumplida("Ese comprobante cuadra: no hay nada que revisar.");
        VistaRevision revision = new VistaRevision(accion.name(), limpia, Fechas.ahora(), usuario);
        repositorio.registrar(idResultado, revision);
        eventos.publishEvent(new EventoAuditoria(Tipo.CASO_REVISADO,
                "empresa=" + empresaId + " caso=" + idResultado + " accion=" + accion + " por=" + usuario));
        return revision;
    }

    /** Volver a conciliar los mismos archivos no debe borrar lo que el contador ya revisó. */
    @EventListener
    public void alConciliar(ConciliacionEjecutada evento) {
        repositorio.heredarRevisiones(evento.empresaId(), evento.idConciliacion());
    }

    /** Tarea 3.1-A: vista previa del aviso con la última conciliación. Queda guardado (rastreable), no se envía. */
    @Transactional
    public VistaAviso simularAviso(long empresaId) {
        VistaConciliacion c = conciliaciones.ultima(empresaId)
                .orElseThrow(() -> new ReglaIncumplida("Primero concilia: el aviso resume la última conciliación."));
        RedactorAviso.Aviso aviso = RedactorAviso.redactar(c);
        String fecha = Fechas.ahora();
        long id = repositorio.guardarAviso(c.id(), fecha, aviso.asunto(), aviso.cuerpo());
        return new VistaAviso(id, c.id(), fecha, aviso.asunto(), aviso.cuerpo());
    }

    public List<VistaAviso> avisos(long empresaId) {
        return repositorio.avisos(empresaId);
    }
}
