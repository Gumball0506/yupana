package pe.yupana.conciliacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import pe.yupana.carga.ErrorCarga;
import pe.yupana.carga.ServicioCargas;

/** Pruebas T07–T12 (H2) con los archivos demo. BD en memoria propia. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:conc;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false"})
@DirtiesContext
class ConciliacionTest {

    static final long E = 1; // empresa A de la siembra

    @Autowired ServicioCargas cargas;
    @Autowired ServicioConciliacion conciliacion;

    void subir(String origen, String archivo) throws Exception {
        cargas.importar(E, origen, archivo, Files.readAllBytes(Path.of("datos_demo", archivo)));
    }

    VistaConciliacion conciliarDemo() throws Exception {
        subir("compras", "yupana_compras_demo.csv");
        subir("rce", "yupana_rce_demo.xlsx");
        return conciliacion.detalle(E, conciliacion.conciliar(E)).orElseThrow();
    }

    static VistaResultado doc(VistaConciliacion c, String numero) {
        return c.resultados().stream().filter(r -> r.numero().equals(numero)).findFirst().orElseThrow();
    }

    static List<List<String>> difs(VistaResultado r) {
        return r.diferencias().stream().map(d -> List.of(d.campo(), d.valorCompras(), d.valorRce())).toList();
    }

    @Test
    void t07_a_t12_demo() throws Exception {
        var c = conciliarDemo();
        // T07: 10 comprobantes, 7 concilian, 3 con diferencia
        assertEquals(List.of(10, 7, 3), List.of(c.total(), c.conciliados(), c.conDiferencia()));
        // T08: doc 3 → base, IGV y total
        assertEquals(List.of(List.of("base_imponible", "350.00", "300.00"), List.of("igv", "63.00", "54.00"),
                List.of("total", "413.00", "354.00")), difs(doc(c, "000003")));
        // T09 + T11: doc 7 → solo fecha; misma llave, NO es faltante
        assertEquals("DIFERENCIA", doc(c, "000007").resultado());
        assertEquals(List.of(List.of("fecha_emision", "2026-10-09", "2026-10-10")), difs(doc(c, "000007")));
        // T10: doc 9 → base, IGV y total
        assertEquals(List.of(List.of("base_imponible", "180.00", "160.00"), List.of("igv", "32.40", "28.80"),
                List.of("total", "212.40", "188.80")), difs(doc(c, "000009")));
        // T12: IGV bajo revisión = 63 + 216 + 32.40 (calculado a mano); diferencia entre fuentes = 9 + 0 + 3.60
        assertEquals(0, new BigDecimal("311.40").compareTo(c.igvRevision()));
        assertEquals(0, new BigDecimal("12.60").compareTo(c.diferenciaIgv()));
        // Hoja de cuadre: cada resultado trae los valores de ambas fuentes
        var d7 = doc(c, "000007");
        assertEquals("2026-10-09", d7.compras().fechaEmision());
        assertEquals("2026-10-10", d7.rce().fechaEmision());
        assertEquals(0, new BigDecimal("1416.00").compareTo(d7.rce().total()));

        // 2.1-B: reproducible en dos ejecuciones y persistido (ultima() = lo que ve el panel tras recargar)
        long id2 = conciliacion.conciliar(E);
        var c2 = conciliacion.ultima(E).orElseThrow();
        assertEquals(id2, c2.id());
        assertEquals(List.of(c.total(), c.conciliados(), c.conDiferencia()), List.of(c2.total(), c2.conciliados(), c2.conDiferencia()));
        assertEquals(c.igvRevision(), c2.igvRevision());

        // Causa guardada y exportación CSV
        assertEquals("Difiere fecha de emisión (compras 2026-10-09 · RCE 2026-10-10).", doc(c2, "000007").causa());
        assertEquals("2026-10", c2.periodo());
        var csv = conciliacion.csv(E, id2).orElseThrow().split("\r\n");
        assertEquals(11, csv.length); // encabezado + 10
        assertTrue(csv[0].startsWith("﻿nro,resultado,causa,"));
        assertTrue(csv[3].startsWith("3,DIFERENCIA,\"Difiere base imponible (compras 350.00 · RCE 300.00), IGV"), csv[3]);

        // «Reiniciar demo» también borra las conciliaciones (ON DELETE CASCADE)
        cargas.reiniciar(E);
        assertTrue(conciliacion.ultima(E).isEmpty());
    }

    @Test
    void demoConFaltantes() throws Exception {
        subir("compras", "yupana_compras_demo.csv");
        subir("rce", "yupana_rce_demo_faltantes.csv");
        var c = conciliacion.detalle(E, conciliacion.conciliar(E)).orElseThrow();
        assertEquals(List.of(11, 6, 5), List.of(c.total(), c.conciliados(), c.conDiferencia()));
        assertEquals("SOLO_COMPRAS", doc(c, "000010").resultado());
        assertEquals("SOLO_RCE", doc(c, "000011").resultado());
        cargas.reiniciar(E);
    }

    @Test
    void sinArchivosCargadosPideCargarlos() {
        cargas.reiniciar(E);
        var e = assertThrows(ErrorCarga.class, () -> conciliacion.conciliar(E));
        assertEquals(List.of("Primero carga ambos archivos: Compras y Propuesta RCE."), e.errores());
    }
}
