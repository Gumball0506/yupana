package pe.yupana.casos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import pe.yupana.conciliacion.VistaConciliacion;
import pe.yupana.conciliacion.VistaResultado;
import pe.yupana.dominio.Fechas;

/**
 * Arma el texto del aviso del día a partir de una conciliación. Cada caso lleva « /#caso-N»: la pantalla lo convierte
 * en enlace «Ver caso» y el chat lo quita. Rótulo obligatorio del IGV: «no es crédito fiscal perdido ni recuperado».
 */
final class RedactorAviso {

    record Aviso(String asunto, String cuerpo) {}

    private RedactorAviso() {}

    static Aviso redactar(VistaConciliacion c) {
        List<VistaResultado> observados = c.resultados().stream().filter(r -> !"CONCILIADO".equals(r.resultado())).toList();
        List<VistaResultado> pendientes = observados.stream().filter(r -> r.revision() == null).toList();
        List<VistaResultado> revisados = observados.stream().filter(r -> r.revision() != null).toList();

        String asunto = observados.isEmpty() ? "Yupana (demo) · Los " + c.total() + " comprobantes cuadran"
                : "Yupana (demo) · " + pendientes.size() + " de " + observados.size() + " casos por revisar";
        StringBuilder cuerpo = new StringBuilder("Aviso de demostración: no se envió por correo ni por WhatsApp.\n\n")
                .append("Conciliación #").append(c.id()).append(" · periodo ").append(c.periodo())
                .append(" · ").append(Fechas.enTexto(c.fecha())).append("\n")
                .append(c.total()).append(" comprobantes cruzados: ").append(c.conciliados()).append(" cuadran y ")
                .append(observados.size()).append(observados.size() == 1 ? " no cuadra.\n" : " no cuadran.\n");
        if (!pendientes.isEmpty()) {
            cuerpo.append("\nPor revisar:\n");
            pendientes.forEach(r -> cuerpo.append(linea(r, Objects.requireNonNullElse(r.causa(), r.resultado()))));
        }
        if (!revisados.isEmpty()) {
            cuerpo.append("\nYa revisados:\n");
            revisados.forEach(r -> cuerpo.append(linea(r, "OBSERVADO".equals(r.revision().accion()) ? "Observado" : "Confirmado")));
        }
        if (!observados.isEmpty())
            cuerpo.append("\nIGV bajo revisión (demo): ").append(soles(c.igvRevision()))
                    .append(". Cifra indicativa sujeta a revisión profesional: no es crédito fiscal perdido ni recuperado.\n");
        return new Aviso(asunto, cuerpo.toString());
    }

    private static String linea(VistaResultado r, String detalle) {
        return "· " + r.serie() + "-" + r.numero() + " — " + detalle + " /#caso-" + r.id() + "\n";
    }

    private static String soles(BigDecimal importe) {
        return "S/ " + String.format(Locale.US, "%,.2f", importe);
    }
}
