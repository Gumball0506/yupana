package pe.yupana.conciliacion;

import java.math.BigDecimal;
import java.util.List;

import pe.yupana.dominio.Conciliador;
import pe.yupana.dominio.ResultadoComparacion;

/** Una conciliación recién calculada, lista para guardarse. Los totales se derivan de los resultados. */
public record NuevaConciliacion(long empresaId, long idCompras, long idRce, List<ResultadoComparacion> resultados) {

    public int total() { return resultados.size(); }

    public int conciliados() { return (int) Conciliador.contarConciliados(resultados); }

    public int conDiferencia() { return total() - conciliados(); }

    public BigDecimal igvBajoRevision() { return Conciliador.igvBajoRevision(resultados); }

    public BigDecimal diferenciaIgv() { return Conciliador.diferenciaIgv(resultados); }
}
