package pe.yupana.carga;

import java.util.List;
import java.util.Map;

import pe.yupana.dominio.Textos;

/** Contrato de los archivos de entrada: 8 columnas normalizadas (no es el layout oficial de SUNAT). */
public final class EsquemaArchivo {

    public static final List<String> COLUMNAS = List.of("ruc_proveedor", "tipo_comprobante", "serie", "numero",
            "fecha_emision", "base_imponible", "igv", "total");
    public static final List<String> IMPORTES = List.of("base_imponible", "igv", "total");
    /** Largos máximos de las columnas de texto; deben coincidir con schema.sql. */
    public static final Map<String, Integer> LARGO_MAXIMO = Map.of("tipo_comprobante", 30, "serie", 20, "numero", 20);
    public static final Map<String, String> ORIGENES = Map.of(
            "compras", "Compras de la empresa",
            "rce", "Propuesta RCE (simulada)");

    private EsquemaArchivo() {}

    /** «RUC Proveedor», «Fecha Emisión » o «IGV» → ruc_proveedor, fecha_emision, igv. */
    public static String normalizarEncabezado(String s) {
        return Textos.sinTildes(s).toLowerCase().replaceAll("[\\s.\\-]+", "_").replaceAll("^_|_$", "");
    }

    /** «118,00» (CSV de Excel en español) → «118.00». «1,416» no se toca: podría ser separador de miles. */
    public static String normalizarDecimal(String v) {
        return v.matches("\\d+,\\d{1,2}") ? v.replace(',', '.') : v;
    }
}
