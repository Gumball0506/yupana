package pe.yupana.sire;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.yupana.carga.DemoReiniciada;
import pe.yupana.carga.EsquemaArchivo;
import pe.yupana.carga.RepositorioCargas;
import pe.yupana.carga.ServicioCargas;
import pe.yupana.dominio.Comprobante;
import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.dominio.SimuladorSire;
import pe.yupana.seguridad.EventoAuditoria;
import pe.yupana.seguridad.EventoAuditoria.Tipo;

/**
 * SIMULADOR del flujo SIRE: periodo → solicitar propuesta → ticket → descargar → importar o «aceptar».
 * No se conecta con SUNAT ni pide clave SOL. La regla de cómo se arma la propuesta está en {@link SimuladorSire}.
 */
@Service
public class ServicioSire {

    /** El SIRE real responde con un ticket y procesa en segundo plano; aquí, 3 segundos. */
    static final Duration PROCESO = Duration.ofSeconds(3);

    private final RepositorioSire tickets;
    private final RepositorioCargas cargas;
    private final ServicioCargas servicioCargas;
    private final ApplicationEventPublisher eventos;

    public ServicioSire(RepositorioSire tickets, RepositorioCargas cargas, ServicioCargas servicioCargas, ApplicationEventPublisher eventos) {
        this.tickets = tickets;
        this.cargas = cargas;
        this.servicioCargas = servicioCargas;
        this.eventos = eventos;
    }

    private List<Comprobante> compras(long empresaId) {
        return cargas.ultimaDe(empresaId, "compras").map(cargas::comprobantes).orElse(List.of());
    }

    /** Periodos con compras (el más reciente primero, es el que se propone) y al final el mes actual si no tiene compras. */
    public List<String> periodos(long empresaId) {
        TreeSet<String> conCompras = new TreeSet<>(Comparator.reverseOrder());
        compras(empresaId).forEach(c -> conCompras.add(c.fechaEmision().substring(0, 7)));
        List<String> periodos = new ArrayList<>(conCompras);
        String actual = LocalDate.now().toString().substring(0, 7);
        if (!conCompras.contains(actual)) periodos.add(actual);
        return periodos;
    }

    /** Paso 2: genera el archivo y devuelve un ticket, como el SIRE real. */
    @Transactional
    public VistaTicket solicitar(long empresaId, String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-(0[1-9]|1[0-2])")) throw new ReglaIncumplida("Periodo inválido: usa AAAA-MM.");
        List<Comprobante> compras = compras(empresaId);
        if (compras.isEmpty()) throw new ReglaIncumplida("Primero carga tus compras: el simulador arma la propuesta a partir de ellas.");
        SimuladorSire.Propuesta propuesta = SimuladorSire.para(compras, periodo);
        long id = tickets.guardar(empresaId, periodo, LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).toString(),
                propuesta.comprobantes().size(), csv(propuesta.comprobantes()));
        return ticket(empresaId, id).conVariaciones(propuesta.variaciones());
    }

    /** El archivo usa el mismo formato de 8 columnas que una carga manual. */
    public static String csv(List<Comprobante> comprobantes) {
        StringBuilder csv = new StringBuilder(String.join(",", EsquemaArchivo.COLUMNAS)).append("\n");
        for (Comprobante c : comprobantes)
            csv.append(String.join(",", c.rucProveedor(), c.tipoComprobante(), c.serie(), c.numero(), c.fechaEmision(),
                    importe(c.baseImponible()), importe(c.igv()), importe(c.total()))).append("\n");
        return csv.toString();
    }

    private static String importe(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Paso 3: «EN_PROCESO» los primeros segundos, luego el estado guardado y el nombre del archivo. */
    public VistaTicket ticket(long empresaId, long id) {
        RepositorioSire.TicketGuardado t = tickets.buscar(empresaId, id).orElseThrow(() -> new ReglaIncumplida("Ticket no encontrado."));
        String numero = String.format("SIM-%s-%06d", t.periodo().replace("-", ""), t.id());
        boolean listo = LocalDateTime.parse(t.creado()).plus(PROCESO).isBefore(LocalDateTime.now());
        return new VistaTicket(t.id(), t.periodo(), t.creado(), t.filas(), listo ? t.estado().name() : "EN_PROCESO", numero,
                listo ? "propuesta_rce_" + t.periodo() + "_" + numero + ".csv" : null, null);
    }

    private VistaTicket listo(long empresaId, long id) {
        VistaTicket t = ticket(empresaId, id);
        if (t.archivo() == null) throw new ReglaIncumplida("El ticket aún está en proceso: espera unos segundos.");
        return t;
    }

    public String archivo(long empresaId, long id) {
        listo(empresaId, id);
        return tickets.contenido(id);
    }

    /** Paso 4: importar como «Propuesta RCE»; pasa por la misma validación que un archivo subido a mano. */
    @Transactional
    public long importar(long empresaId, long id) {
        VistaTicket t = listo(empresaId, id);
        long importacion = servicioCargas.importar(empresaId, "rce", t.archivo(), tickets.contenido(id).getBytes(StandardCharsets.UTF_8));
        tickets.cambiarEstado(id, EstadoTicket.IMPORTADO);
        eventos.publishEvent(new EventoAuditoria(Tipo.SIRE_IMPORTADO, "empresa=" + empresaId + " ticket=" + t.numeroTicket()));
        return importacion;
    }

    /** «Aceptar propuesta»: en el SIRE real genera tu registro de compras. Aquí solo cambia un estado interno. */
    public VistaTicket aceptar(long empresaId, long id) {
        listo(empresaId, id);
        tickets.cambiarEstado(id, EstadoTicket.ACEPTADO_SIMULADO);
        return ticket(empresaId, id);
    }

    public List<VistaTicket> recientes(long empresaId) {
        return tickets.recientes(empresaId).stream().map(id -> ticket(empresaId, id)).toList();
    }

    @EventListener
    public void alReiniciar(DemoReiniciada evento) {
        tickets.borrarDeEmpresa(evento.empresaId());
    }
}
