package pe.yupana.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Reglas puras de H5 (sin Spring): RUC, acción sobre un caso y simulador SIRE. */
class ReglasNuevasTest {

    @Test
    void rucConDigitoVerificador() {
        assertEquals(8, Ruc.digitoVerificador("2099991001"));
        assertEquals(5, Ruc.digitoVerificador("2013131295")); // RUC real conocido 20131312955, solo para probar la fórmula
        new Ruc("20999910042");
        assertTrue(assertThrows(ReglaIncumplida.class, () -> new Ruc("20999910041")).getMessage().contains("debe ser 2"));
        assertThrows(ReglaIncumplida.class, () -> new Ruc("20131312955")); // válido pero no ficticio
        assertThrows(ReglaIncumplida.class, () -> new Ruc("2099991004"));
        assertThrows(ReglaIncumplida.class, () -> new Ruc(null));
    }

    @Test
    void accionCaso() {
        assertEquals(AccionCaso.OBSERVADO, AccionCaso.desde("observar"));
        assertEquals(AccionCaso.CONFIRMADO, AccionCaso.desde("confirmar"));
        assertThrows(ReglaIncumplida.class, () -> AccionCaso.desde("presentar_sunat"));
    }

    static Comprobante compra(String numero, String fecha) {
        return new Comprobante("209999100" + numero.substring(4), "Factura", "F001", numero, fecha,
                new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"));
    }

    @Test
    void simuladorSireEsDeterministaYExplicable() {
        List<Comprobante> compras = List.of(compra("000001", "2026-10-03"), compra("000002", "2026-10-04"),
                compra("000003", "2026-09-30"));
        var a = SimuladorSire.para(compras, "2026-10");
        assertEquals(a, SimuladorSire.para(compras, "2026-10"));                  // misma entrada → misma propuesta
        assertTrue(a.comprobantes().stream().noneMatch(c -> c.numero().equals("000003"))); // otro periodo
        assertEquals(SimuladorSire.RUC_PROVEEDOR_EXTRA, a.comprobantes().getLast().rucProveedor());
        assertEquals("E001-0002610", a.variaciones().getLast().documento());
        // Toda diferencia con las compras está explicada en «variaciones»
        long cambiados = compras.stream().filter(c -> c.fechaEmision().startsWith("2026-10"))
                .filter(c -> !a.comprobantes().contains(c)).count();
        assertEquals(cambiados + 1, a.variaciones().size());
    }

    @Test
    void csvSeguroContraFormulas() {
        assertEquals("'=1+1", Csv.celda("=1+1"));
        assertEquals("\"a,b\"", Csv.celda("a,b"));
        assertEquals("12.50", Csv.celda(new BigDecimal("12.5")));
    }
}
