package pe.yupana.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Reglas de conciliación sobre el dominio puro: sin Spring, sin base de datos, en milisegundos. */
class ConciliadorTest {

    private final Conciliador conciliador = new Conciliador(new BigDecimal("0.01"));

    static Comprobante fila(String numero, String fecha, String base, String igv, String total) {
        return new Comprobante("20999910001", "Factura", "F001", numero, fecha,
                new BigDecimal(base), new BigDecimal(igv), new BigDecimal(total));
    }

    static List<TipoResultado> tipos(List<ResultadoComparacion> rs) {
        return rs.stream().map(ResultadoComparacion::tipo).toList();
    }

    @Test
    void ausentesYToleranciaDeRedondeo() {
        var compras = List.of(fila("000001", "2026-10-03", "100.00", "18.00", "118.00"),
                fila("000002", "2026-10-03", "50.00", "9.00", "59.00"));
        var rce = List.of(fila("000001", "2026-10-03", "100.01", "18.00", "118.00"),  // 0.01 = redondeo, concilia
                fila("000099", "2026-10-03", "10.00", "1.80", "11.80"));
        var rs = conciliador.comparar(compras, rce);
        assertEquals(List.of(TipoResultado.CONCILIADO, TipoResultado.SOLO_COMPRAS, TipoResultado.SOLO_RCE), tipos(rs));
        assertEquals(0, new BigDecimal("10.80").compareTo(Conciliador.igvBajoRevision(rs)));
    }

    @Test
    void masDeLaToleranciaEsDiferencia() {
        var rs = conciliador.comparar(List.of(fila("000001", "2026-10-03", "100.00", "18.00", "118.00")),
                List.of(fila("000001", "2026-10-03", "100.02", "18.00", "118.00")));
        assertEquals(List.of(TipoResultado.DIFERENCIA), tipos(rs));
        assertEquals(List.of(CampoCotejable.BASE_IMPONIBLE), rs.getFirst().diferencias().stream().map(Diferencia::campo).toList());
    }

    @Test
    void ceroCoincidenciasNoSeCae() {
        var compras = List.of(fila("000001", "2026-10-03", "100.00", "18.00", "118.00"));
        var nada = conciliador.comparar(compras, List.of(fila("000777", "2026-10-03", "1", "1", "1")));
        assertTrue(nada.stream().noneMatch(r -> r.tipo() == TipoResultado.CONCILIADO));
    }

    @Test
    void codigoYMayusculasCruzanConElNombre() {
        var compras = List.of(fila("000001", "2026-10-03", "100.00", "18.00", "118.00"));
        var codigo = new Comprobante("20999910001", "01", "f001", "000001", "2026-10-03",
                new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"));
        assertEquals(TipoResultado.CONCILIADO, conciliador.comparar(compras, List.of(codigo)).getFirst().tipo());
    }

    @Test
    void cerosAIzquierdaImportan() {
        var rs = conciliador.comparar(List.of(fila("000001", "2026-10-03", "100", "18", "118")),
                List.of(fila("1", "2026-10-03", "100", "18", "118")));
        assertEquals(TipoResultado.SOLO_COMPRAS, rs.getFirst().tipo());
    }

    @Test
    void esDeterminista() {
        var compras = List.of(fila("000001", "2026-10-03", "100", "18", "118"), fila("000002", "2026-10-04", "50", "9", "59"));
        var rce = List.of(fila("000002", "2026-10-05", "50", "9", "59"));
        assertEquals(conciliador.comparar(compras, rce), conciliador.comparar(compras, rce));
    }

    @Test
    void diferenciaDeIgvSumaLoAbsoluto() {
        var compras = List.of(fila("000001", "2026-10-03", "100", "18.00", "118"), fila("000002", "2026-10-03", "50", "9.00", "59"));
        var rce = List.of(fila("000001", "2026-10-03", "90", "16.20", "106.20"));
        var rs = conciliador.comparar(compras, rce);
        // 000001: |18.00 − 16.20| = 1.80 ; 000002 solo en compras: |9.00 − 0| = 9.00
        assertEquals(0, new BigDecimal("10.80").compareTo(Conciliador.diferenciaIgv(rs)));
    }

    @Test
    void tiposDeComprobanteSeNormalizan() {
        assertEquals("FACTURA", TipoComprobante.canonico("01"));
        assertEquals("FACTURA", TipoComprobante.canonico(" factura "));
        assertEquals("NOTA DE CREDITO", TipoComprobante.canonico("nc"));
        assertEquals("NOTA DE CREDITO", TipoComprobante.canonico("Nota de Crédito"));
        assertEquals("OTRO", TipoComprobante.canonico("otro"));
    }

    @Test
    void textosAjenosSeSaneanAntesDeUsarse() {
        assertEquals("a.csv", Textos.nombreSeguro("C:\\carpeta\\..\\a.csv"));
        assertEquals("a.csv", Textos.nombreSeguro("../../etc/a.csv"));
        assertEquals("archivo", Textos.nombreSeguro(null));
        assertEquals("ab", Textos.nombreSeguro("a\nb"));
        assertEquals(255, Textos.nombreSeguro("x".repeat(400)).length());
        assertEquals("x y", Textos.paraBitacora("x\ny"));
        assertEquals("abcde…", Textos.acortar("abcdefgh", 5));
    }

    @Test
    void laNotaDeCreditoRestaAlSumarElIgvBajoRevision() {
        var factura = fila("000001", "2026-10-03", "100.00", "18.00", "118.00");
        var nota = new Comprobante("20999910001", "07", "FC01", "000001", "2026-10-03",
                new BigDecimal("50.00"), new BigDecimal("9.00"), new BigDecimal("59.00"));
        var rs = conciliador.comparar(List.of(factura, nota), List.of());
        // factura 18.00 − nota 9.00
        assertEquals(0, new BigDecimal("9.00").compareTo(Conciliador.igvBajoRevision(rs)));
    }

    @Test
    void concilia1500ComprobantesEnMenosDeDosSegundos() {
        var compras = new java.util.ArrayList<Comprobante>();
        var rce = new java.util.ArrayList<Comprobante>();
        for (int i = 1; i <= 1500; i++) {
            String numero = "%06d".formatted(i);
            compras.add(fila(numero, "2026-10-03", "100.00", "18.00", "118.00"));
            rce.add(fila(numero, "2026-10-03", i % 10 == 0 ? "90.00" : "100.00", i % 10 == 0 ? "16.20" : "18.00", "118.00"));
        }
        long inicio = System.nanoTime();
        var rs = conciliador.comparar(compras, rce);
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
        assertEquals(150, rs.stream().filter(r -> r.tipo() == TipoResultado.DIFERENCIA).count());
        assertTrue(milisegundos < 2000, "tardó " + milisegundos + " ms");
        System.out.println("RENDIMIENTO 1500 comprobantes: " + milisegundos + " ms");
    }
}
