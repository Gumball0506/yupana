package pe.yupana.casos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import pe.yupana.carga.ServicioCargas;
import pe.yupana.conciliacion.ServicioConciliacion;
import pe.yupana.conciliacion.VistaResultado;
import pe.yupana.dominio.ReglaIncumplida;

/** Pruebas T13–T14 (H3): aviso del día y Confirmar / Observar persistente. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:casos;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false"})
@DirtiesContext
class CasosTest {

    static final long E = 1; // empresa A de la siembra

    @Autowired ServicioCargas cargas;
    @Autowired ServicioConciliacion conciliacion;
    @Autowired ServicioCasos casos;

    VistaResultado caso(String numero) {
        return conciliacion.ultima(E).orElseThrow().resultados().stream().filter(r -> r.numero().equals(numero)).findFirst().orElseThrow();
    }

    long id(String numero) {
        return caso(numero).id();
    }

    @Test
    void t13_t14_avisoYEstadoPersistente() throws Exception {
        cargas.importar(E, "compras", "c.csv", Files.readAllBytes(Path.of("datos_demo", "yupana_compras_demo.csv")));
        cargas.importar(E, "rce", "r.csv", Files.readAllBytes(Path.of("datos_demo", "yupana_rce_demo.csv")));
        conciliacion.conciliar(E);

        // T14: caso 000003 → «Observar»; una consulta nueva (= recargar) lo sigue mostrando observado
        assertNull(caso("000003").revision());
        casos.revisar(E, id("000003"), "observar", "  Pedir nota de crédito al proveedor  ", "tester");
        assertEquals("OBSERVADO", caso("000003").revision().accion());
        assertEquals("Pedir nota de crédito al proveedor", caso("000003").revision().nota());
        assertEquals("tester", caso("000003").revision().usuario());
        // Manda la última acción; el historial queda en acciones_caso
        casos.revisar(E, id("000009"), "observar", null, "tester");
        casos.revisar(E, id("000009"), "confirmar", null, "tester");
        assertEquals("CONFIRMADO", caso("000009").revision().accion());

        assertThrows(ReglaIncumplida.class, () -> casos.revisar(E, id("000003"), "presentar_sunat", null, "tester"));
        assertThrows(ReglaIncumplida.class, () -> casos.revisar(E, id("000001"), "confirmar", null, "tester")); // cuadra
        assertThrows(ReglaIncumplida.class, () -> casos.revisar(E, 999_999, "confirmar", null, "tester"));
        assertThrows(ReglaIncumplida.class, () -> casos.revisar(E, id("000003"), "observar", "x".repeat(301), "tester"));

        // T13: el aviso lista lo pendiente con enlace, lo revisado y el IGV con su leyenda
        VistaAviso aviso = casos.simularAviso(E);
        assertEquals("Yupana (demo) · 1 de 3 casos por revisar", aviso.asunto());
        assertTrue(aviso.cuerpo().contains("F001-000007 — Difiere fecha de emisión") && aviso.cuerpo().contains("/#caso-" + id("000007")), aviso.cuerpo());
        assertTrue(aviso.cuerpo().contains("F001-000003 — Observado"));
        assertTrue(aviso.cuerpo().contains("S/ 311.40") && aviso.cuerpo().contains("no es crédito fiscal perdido ni recuperado"));
        assertFalse(aviso.cuerpo().toLowerCase().contains("ahorro"));
        assertEquals(aviso.id(), casos.avisos(E).getFirst().id()); // rastreable

        // Volver a conciliar con los mismos archivos conserva las revisiones (Observer sobre ConciliacionEjecutada)
        conciliacion.conciliar(E);
        assertEquals("OBSERVADO", caso("000003").revision().accion());
        assertNull(caso("000007").revision());
        assertTrue(conciliacion.csv(E, conciliacion.ultima(E).orElseThrow().id()).orElseThrow()
                .contains(",OBSERVADO,Pedir nota de crédito al proveedor"));

        cargas.reiniciar(E);
        assertTrue(casos.avisos(E).isEmpty());
    }
}
