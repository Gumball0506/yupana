package pe.yupana.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Resultado de un comprobante. Los IGV son null del lado que no tiene el comprobante. */
public record ResultadoComparacion(Comprobante comprobante, TipoResultado tipo, List<Diferencia> diferencias,
                                   BigDecimal igvCompras, BigDecimal igvRce) {

    public static ResultadoComparacion soloCompras(Comprobante compras) {
        return new ResultadoComparacion(compras, TipoResultado.SOLO_COMPRAS, List.of(), compras.igv(), null);
    }

    public static ResultadoComparacion soloRce(Comprobante rce) {
        return new ResultadoComparacion(rce, TipoResultado.SOLO_RCE, List.of(), null, rce.igv());
    }

    public static ResultadoComparacion enAmbas(Comprobante compras, Comprobante rce, List<Diferencia> diferencias) {
        TipoResultado tipo = diferencias.isEmpty() ? TipoResultado.CONCILIADO : TipoResultado.DIFERENCIA;
        return new ResultadoComparacion(compras, tipo, diferencias, compras.igv(), rce.igv());
    }

    /** Causa legible que se guarda con el resultado (tarea 2.1-B: registrar origen y causa). */
    public String causa() {
        return switch (tipo) {
            case CONCILIADO -> "Coincide campo por campo.";
            case SOLO_COMPRAS -> "Está en tus compras pero no figura en la propuesta RCE.";
            case SOLO_RCE -> "Figura en la propuesta RCE pero no en tus compras.";
            case DIFERENCIA -> "Difiere " + diferencias.stream().map(Diferencia::descripcion)
                    .collect(Collectors.joining(", ")) + ".";
        };
    }
}
