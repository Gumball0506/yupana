package pe.yupana.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Function;

/**
 * Campos que se cotejan cuando la llave coincide. Agregar un campo nuevo es agregar una constante:
 * el conciliador no cambia (principio abierto/cerrado).
 */
public enum CampoCotejable {
    FECHA_EMISION("fecha_emision", "fecha de emisión", Comprobante::fechaEmision, ComparadorDeValores.TEXTO),
    BASE_IMPONIBLE("base_imponible", "base imponible", Comprobante::baseImponible, ComparadorDeValores.IMPORTE),
    IGV("igv", "IGV", Comprobante::igv, ComparadorDeValores.IMPORTE),
    TOTAL("total", "total", Comprobante::total, ComparadorDeValores.IMPORTE);

    private final String columna;
    private final String etiqueta;
    private final Function<Comprobante, Object> extractor;
    private final ComparadorDeValores comparador;

    CampoCotejable(String columna, String etiqueta, Function<Comprobante, Object> extractor, ComparadorDeValores comparador) {
        this.columna = columna;
        this.etiqueta = etiqueta;
        this.extractor = extractor;
        this.comparador = comparador;
    }

    public String columna() { return columna; }

    public String etiqueta() { return etiqueta; }

    public boolean difiere(Comprobante compras, Comprobante rce, BigDecimal tolerancia) {
        return comparador.difieren(extractor.apply(compras), extractor.apply(rce), tolerancia);
    }

    public String valorEn(Comprobante comprobante) {
        Object valor = extractor.apply(comprobante);
        return valor instanceof BigDecimal importe
                ? importe.setScale(2, RoundingMode.HALF_UP).toPlainString()
                : String.valueOf(valor);
    }
}
