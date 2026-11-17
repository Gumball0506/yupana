package pe.yupana.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Conciliación determinista (sin IA): misma entrada, mismo resultado.
 * Orden de salida: primero lo que está en compras, luego lo que solo está en la propuesta RCE.
 */
public final class Conciliador {

    private final BigDecimal tolerancia;

    public Conciliador(BigDecimal tolerancia) {
        this.tolerancia = Objects.requireNonNull(tolerancia);
    }

    public List<ResultadoComparacion> comparar(List<Comprobante> compras, List<Comprobante> rce) {
        Map<LlaveComprobante, Comprobante> rcePorLlave = new LinkedHashMap<>();
        rce.forEach(c -> rcePorLlave.put(c.llave(), c));

        List<ResultadoComparacion> resultados = new ArrayList<>();
        Set<LlaveComprobante> comparadas = new HashSet<>();
        for (Comprobante deCompras : compras) {
            LlaveComprobante llave = deCompras.llave();
            comparadas.add(llave);
            Comprobante deRce = rcePorLlave.get(llave);
            resultados.add(deRce == null
                    ? ResultadoComparacion.soloCompras(deCompras)
                    : ResultadoComparacion.enAmbas(deCompras, deRce, diferenciasEntre(deCompras, deRce)));
        }
        rcePorLlave.forEach((llave, deRce) -> {
            if (!comparadas.contains(llave)) resultados.add(ResultadoComparacion.soloRce(deRce));
        });
        return resultados;
    }

    private List<Diferencia> diferenciasEntre(Comprobante compras, Comprobante rce) {
        List<Diferencia> diferencias = new ArrayList<>();
        for (CampoCotejable campo : CampoCotejable.values())
            if (campo.difiere(compras, rce, tolerancia))
                diferencias.add(new Diferencia(campo, campo.valorEn(compras), campo.valorEn(rce)));
        return diferencias;
    }

    /**
     * IGV de los comprobantes observados (el de compras si existe); las notas de crédito restan.
     * Indicativo, sujeto a revisión profesional.
     */
    public static BigDecimal igvBajoRevision(List<ResultadoComparacion> resultados) {
        return resultados.stream().filter(r -> r.tipo().estaObservado())
                .map(r -> conSigno(r, r.igvCompras() != null ? r.igvCompras() : r.igvRce()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal conSigno(ResultadoComparacion resultado, BigDecimal igv) {
        return resultado.comprobante().esNotaDeCredito() ? igv.negate() : igv;
    }

    /** Suma de |IGV compras − IGV RCE|; un lado ausente cuenta como 0. */
    public static BigDecimal diferenciaIgv(List<ResultadoComparacion> resultados) {
        return resultados.stream().filter(r -> r.tipo().estaObservado())
                .map(r -> Objects.requireNonNullElse(r.igvCompras(), BigDecimal.ZERO)
                        .subtract(Objects.requireNonNullElse(r.igvRce(), BigDecimal.ZERO)).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static long contarConciliados(List<ResultadoComparacion> resultados) {
        return resultados.stream().filter(r -> !r.tipo().estaObservado()).count();
    }
}
