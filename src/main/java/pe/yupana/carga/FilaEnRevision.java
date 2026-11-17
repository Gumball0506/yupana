package pe.yupana.carga;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pe.yupana.dominio.Comprobante;

/** Una fila mientras las reglas la revisan: valores de texto por columna y los importes ya convertidos. */
class FilaEnRevision {

    private final int n;
    private final Map<String, String> valores = new LinkedHashMap<>();
    private final Map<String, BigDecimal> importes = new HashMap<>();

    FilaEnRevision(Fila fila, List<String> encabezado) {
        this.n = fila.n();
        for (String columna : EsquemaArchivo.COLUMNAS) {
            int i = encabezado.indexOf(columna);
            valores.put(columna, i < fila.celdas().size() ? fila.celdas().get(i).strip() : "");
        }
    }

    int n() { return n; }

    String valor(String columna) { return valores.get(columna); }

    void registrarImporte(String columna, BigDecimal importe) { importes.put(columna, importe); }

    boolean tieneTodosLosImportes() { return importes.keySet().containsAll(EsquemaArchivo.IMPORTES); }

    BigDecimal importe(String columna) { return importes.get(columna); }

    Comprobante comoComprobante() {
        return new Comprobante(valor("ruc_proveedor"), valor("tipo_comprobante"), valor("serie"), valor("numero"),
                valor("fecha_emision"), importe("base_imponible"), importe("igv"), importe("total"));
    }
}
