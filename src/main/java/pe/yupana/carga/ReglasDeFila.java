package pe.yupana.carga;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import pe.yupana.dominio.Textos;

/** Reglas de validación por fila, en el orden en que se aplican. */
final class ReglasDeFila {

    private static final BigDecimal TOLERANCIA_TOTAL = new BigDecimal("0.01");
    private static final int ECO_MAXIMO = 40;

    private ReglasDeFila() {}

    static List<ReglaDeFila> enOrden() {
        return List.of(ReglasDeFila::textosObligatorios, ReglasDeFila::ruc, ReglasDeFila::fecha,
                ReglasDeFila::importes, ReglasDeFila::coherenciaDelTotal);
    }

    static void textosObligatorios(FilaEnRevision fila, Hallazgos h) {
        for (String columna : List.of("tipo_comprobante", "serie", "numero")) {
            String valor = fila.valor(columna);
            int maximo = EsquemaArchivo.LARGO_MAXIMO.get(columna);
            if (valor.isEmpty()) h.errorEn(fila.n(), columna, "está vacía.");
            else if (valor.length() > maximo) h.errorEn(fila.n(), columna, "admite hasta " + maximo + " caracteres.");
        }
    }

    static void ruc(FilaEnRevision fila, Hallazgos h) {
        String ruc = fila.valor("ruc_proveedor");
        if (!ruc.matches("\\d{11}"))
            h.errorEn(fila.n(), "ruc_proveedor", "debe tener 11 dígitos (llegó '" + Textos.acortar(ruc, ECO_MAXIMO) + "').");
    }

    static void fecha(FilaEnRevision fila, Hallazgos h) {
        String fecha = fila.valor("fecha_emision");
        try {
            if (!fecha.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("formato", fecha, 0);
            LocalDate.parse(fecha);
        } catch (DateTimeParseException e) {
            h.errorEn(fila.n(), "fecha_emision", "usa el formato AAAA-MM-DD (llegó '" + Textos.acortar(fecha, ECO_MAXIMO) + "').");
        }
    }

    static void importes(FilaEnRevision fila, Hallazgos h) {
        for (String columna : EsquemaArchivo.IMPORTES) {
            String valor = fila.valor(columna);
            try {
                BigDecimal importe = new BigDecimal(EsquemaArchivo.normalizarDecimal(valor));
                if (importe.signum() < 0)
                    h.errorEn(fila.n(), columna, "no puede ser negativo (" + Textos.acortar(valor, ECO_MAXIMO) + ").");
                fila.registrarImporte(columna, importe);
            } catch (NumberFormatException e) {
                h.errorEn(fila.n(), columna, "'" + Textos.acortar(valor, ECO_MAXIMO) + "' no es un número válido (usa punto decimal).");
            }
        }
    }

    /** Coherencia aritmética, no cálculo tributario: avisa, no bloquea. */
    static void coherenciaDelTotal(FilaEnRevision fila, Hallazgos h) {
        if (!fila.tieneTodosLosImportes()) return;
        BigDecimal suma = fila.importe("base_imponible").add(fila.importe("igv"));
        BigDecimal total = fila.importe("total");
        if (suma.subtract(total).abs().compareTo(TOLERANCIA_TOTAL) > 0)
            h.advertir("Fila " + fila.n() + ": el total (" + total.toPlainString() + ") no coincide con base + IGV ("
                    + suma.toPlainString() + "). Revisa esa fila con el proveedor.");
    }
}
