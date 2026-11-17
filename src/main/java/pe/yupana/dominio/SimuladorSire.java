package pe.yupana.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma una propuesta RCE SIMULADA a partir de las compras de un periodo. No se conecta con SUNAT: en el SIRE real
 * la propuesta sale de los comprobantes electrónicos que emiten los proveedores.
 * Regla determinista por comprobante (misma entrada → misma propuesta): 1 de cada 10 «el proveedor no lo informó»,
 * 1 de cada 10 con base −10 %, 1 de cada 10 con fecha +1 día; además aparece un comprobante que no está en las compras.
 */
public final class SimuladorSire {

    private static final BigDecimal IGV = new BigDecimal("0.18");
    private static final BigDecimal NOVENTA_POR_CIENTO = new BigDecimal("0.90");
    public static final String RUC_PROVEEDOR_EXTRA = "20999910093";

    private SimuladorSire() {}

    /** Qué cambió en un comprobante y por qué (para explicarlo en pantalla). */
    public record Variacion(String documento, String cambio) {}

    public record Propuesta(List<Comprobante> comprobantes, List<Variacion> variaciones) {}

    public static Propuesta para(List<Comprobante> compras, String periodo) {
        List<Comprobante> propuesta = new ArrayList<>();
        List<Variacion> variaciones = new ArrayList<>();
        for (Comprobante c : compras) {
            if (!c.fechaEmision().startsWith(periodo)) continue;
            String documento = c.serie() + "-" + c.numero();
            switch (Math.floorMod(huella(c.llave()), 10)) {
                case 0 -> variaciones.add(new Variacion(documento, "El proveedor no lo informó: no aparece en la propuesta."));
                case 1 -> {
                    propuesta.add(conBaseMenor(c));
                    variaciones.add(new Variacion(documento, "El proveedor informó una base 10 % menor."));
                }
                case 2 -> {
                    propuesta.add(conFechaSiguiente(c));
                    variaciones.add(new Variacion(documento, "El proveedor informó la fecha de emisión un día después."));
                }
                default -> propuesta.add(c);
            }
        }
        Comprobante extra = extraDelPeriodo(periodo);
        propuesta.add(extra);
        variaciones.add(new Variacion(extra.serie() + "-" + extra.numero(), "Factura de un proveedor que no está en tus compras."));
        return new Propuesta(propuesta, variaciones);
    }

    private static int huella(LlaveComprobante llave) {
        return String.join("|", llave.ruc(), llave.tipo(), llave.serie(), llave.numero()).hashCode();
    }

    private static Comprobante conBaseMenor(Comprobante c) {
        BigDecimal base = c.baseImponible().multiply(NOVENTA_POR_CIENTO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igv = base.multiply(IGV).setScale(2, RoundingMode.HALF_UP);
        return new Comprobante(c.rucProveedor(), c.tipoComprobante(), c.serie(), c.numero(), c.fechaEmision(), base, igv, base.add(igv));
    }

    private static Comprobante conFechaSiguiente(Comprobante c) {
        String fecha = LocalDate.parse(c.fechaEmision()).plusDays(1).toString();
        return new Comprobante(c.rucProveedor(), c.tipoComprobante(), c.serie(), c.numero(), fecha, c.baseImponible(), c.igv(), c.total());
    }

    private static Comprobante extraDelPeriodo(String periodo) {
        return new Comprobante(RUC_PROVEEDOR_EXTRA, "Factura", "E001", "000" + periodo.replace("-", "").substring(2),
                periodo + "-15", new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"));
    }
}
