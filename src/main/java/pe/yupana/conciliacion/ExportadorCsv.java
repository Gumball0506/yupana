package pe.yupana.conciliacion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.yupana.carga.VistaComprobante;
import pe.yupana.dominio.CampoCotejable;
import pe.yupana.dominio.Csv;

/** Resultado en CSV para llevarlo a Excel: UTF-8 con BOM (respeta las tildes), una fila por comprobante y su revisión. */
@Component
public class ExportadorCsv {

    public String generar(VistaConciliacion conciliacion) {
        StringBuilder csv = new StringBuilder(Csv.BOM).append(String.join(",", encabezado())).append(Csv.SALTO);
        for (VistaResultado r : conciliacion.resultados()) csv.append(fila(r)).append(Csv.SALTO);
        return csv.toString();
    }

    private static List<String> encabezado() {
        List<String> columnas = new ArrayList<>(List.of("nro", "resultado", "causa", "ruc_proveedor", "tipo_comprobante", "serie", "numero"));
        for (String fuente : List.of("compras", "rce"))
            for (CampoCotejable campo : CampoCotejable.values()) columnas.add(campo.columna() + "_" + fuente);
        columnas.addAll(List.of("revision", "nota_revision"));
        return columnas;
    }

    private static String fila(VistaResultado r) {
        List<Object> valores = new ArrayList<>(List.of(r.orden(), r.resultado(), r.causa(), r.rucProveedor(), r.tipoComprobante(),
                r.serie(), r.numero()));
        valores.addAll(camposDe(r.compras()));
        valores.addAll(camposDe(r.rce()));
        valores.add(estadoDeRevision(r));
        valores.add(r.revision() == null ? null : r.revision().nota());
        return valores.stream().map(Csv::celda).collect(Collectors.joining(","));
    }

    /** Los que cuadran no se revisan; los demás quedan «PENDIENTE» hasta que alguien los confirme u observe. */
    private static String estadoDeRevision(VistaResultado r) {
        if (r.revision() != null) return r.revision().accion();
        return "CONCILIADO".equals(r.resultado()) ? "" : "PENDIENTE";
    }

    private static List<Object> camposDe(VistaComprobante c) {
        if (c == null) return Arrays.asList(null, null, null, null);
        return Arrays.asList(c.fechaEmision(), c.baseImponible(), c.igv(), c.total());
    }

    static String celda(Object valor) {
        return Csv.celda(valor);
    }
}
