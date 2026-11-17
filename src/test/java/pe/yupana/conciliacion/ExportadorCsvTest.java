package pe.yupana.conciliacion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Seguridad de la exportación: ninguna celda debe poder ejecutarse como fórmula en Excel (inyección CSV). */
class ExportadorCsvTest {

    @Test
    void neutralizaFormulas() {
        assertEquals("'=1+1", ExportadorCsv.celda("=1+1"));
        assertEquals("'+cmd", ExportadorCsv.celda("+cmd"));
        assertEquals("'@SUMA(A1)", ExportadorCsv.celda("@SUMA(A1)"));
        assertEquals("'-2+3", ExportadorCsv.celda("-2+3"));
    }

    @Test
    void losNumerosNegativosNoSeAlteran() {
        assertEquals("-5.00", ExportadorCsv.celda(new BigDecimal("-5")));
    }

    @Test
    void escapaComillasYComas() {
        assertEquals("\"a,b\"", ExportadorCsv.celda("a,b"));
        assertEquals("\"di \"\"hola\"\"\"", ExportadorCsv.celda("di \"hola\""));
        assertEquals("", ExportadorCsv.celda(null));
    }
}
