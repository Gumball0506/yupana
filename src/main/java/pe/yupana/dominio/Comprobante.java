package pe.yupana.dominio;

import java.math.BigDecimal;

/** Un comprobante de compra tal como lo registró el cliente o lo propone la fuente externa. */
public record Comprobante(String rucProveedor, String tipoComprobante, String serie, String numero,
                          String fechaEmision, BigDecimal baseImponible, BigDecimal igv, BigDecimal total) {

    public LlaveComprobante llave() {
        return LlaveComprobante.de(rucProveedor, tipoComprobante, serie, numero);
    }

    /** Una nota de crédito reduce el IGV: al sumar se cuenta con signo negativo (los importes se cargan positivos). */
    public boolean esNotaDeCredito() {
        return TipoComprobante.NOTA_DE_CREDITO.nombre().equals(llave().tipo());
    }
}
