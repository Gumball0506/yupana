package pe.yupana.dominio;

import java.math.BigDecimal;

/** Estrategia para decidir si dos valores de un mismo campo difieren. */
@FunctionalInterface
public interface ComparadorDeValores {

    boolean difieren(Object compras, Object rce, BigDecimal tolerancia);

    /** Los importes difieren solo si superan la tolerancia de redondeo. */
    ComparadorDeValores IMPORTE = (a, b, tolerancia) ->
            ((BigDecimal) a).subtract((BigDecimal) b).abs().compareTo(tolerancia) > 0;

    /** El texto debe coincidir exactamente (sin contar espacios en los bordes). */
    ComparadorDeValores TEXTO = (a, b, tolerancia) -> !a.toString().strip().equals(b.toString().strip());
}
