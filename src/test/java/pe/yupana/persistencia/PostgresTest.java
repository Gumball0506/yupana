package pe.yupana.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import pe.yupana.acceso.ServicioAcceso;
import pe.yupana.carga.ServicioCargas;
import pe.yupana.casos.ServicioCasos;
import pe.yupana.conciliacion.ServicioConciliacion;
import pe.yupana.dominio.Rol;

/**
 * El mismo flujo del MVP contra un PostgreSQL REAL (binarios embebidos, sin Docker): esquema, carga, conciliación,
 * Confirmar / Observar, ingreso y borrado en cascada. Se corre aparte: {@code mvnw test -Ppostgres}.
 */
@Tag("postgres")
@SpringBootTest(properties = {"yupana.semilla-datos=false", "yupana.semilla-clave=Clave-de-prueba-1"})
class PostgresTest {

    private static final EmbeddedPostgres POSTGRES = iniciar();

    private static EmbeddedPostgres iniciar() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar PostgreSQL embebido", e);
        }
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registro.add("spring.datasource.username", () -> "postgres");
        registro.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void detener() throws IOException {
        POSTGRES.close();
    }

    static final long E = 1; // empresa A de la siembra

    @Autowired JdbcClient jdbc;
    @Autowired ServicioCargas cargas;
    @Autowired ServicioConciliacion conciliacion;
    @Autowired ServicioCasos casos;
    @Autowired ServicioAcceso acceso;

    @Test
    void elEsquemaYLosIndicesSeCreanEnPostgreSql() {
        assertTrue(jdbc.sql("SELECT version()").query(String.class).single().startsWith("PostgreSQL 16"));
        assertEquals(15, jdbc.sql("SELECT COUNT(*) FROM pg_indexes WHERE indexname LIKE 'ix\\_%'").query(Long.class).single());
        assertEquals(3, jdbc.sql("SELECT COUNT(*) FROM empresas").query(Long.class).single()); // siembra
    }

    @Test
    void cargaConcilia_confirmaYObservaUnCaso() throws Exception {
        cargas.importar(E, "compras", "c.csv", Files.readAllBytes(Path.of("datos_demo", "yupana_compras_demo.csv")));
        cargas.importar(E, "rce", "r.xlsx", Files.readAllBytes(Path.of("datos_demo", "yupana_rce_demo.xlsx")));
        long id = conciliacion.conciliar(E);

        var c = conciliacion.detalle(E, id).orElseThrow();
        assertEquals(10, c.total());
        assertEquals(7, c.conciliados());
        assertEquals(3, c.conDiferencia());
        assertEquals(0, new java.math.BigDecimal("311.40").compareTo(c.igvRevision()));
        assertEquals(0, new java.math.BigDecimal("12.60").compareTo(c.diferenciaIgv()));

        long caso3 = c.resultados().stream().filter(r -> r.numero().equals("000003")).findFirst().orElseThrow().id();
        long caso9 = c.resultados().stream().filter(r -> r.numero().equals("000009")).findFirst().orElseThrow().id();
        casos.revisar(E, caso3, "observar", "Pedir nota de crédito", "tester");
        casos.revisar(E, caso9, "confirmar", null, "tester");

        var despues = conciliacion.ultima(E).orElseThrow();
        assertEquals("OBSERVADO", despues.resultados().stream().filter(r -> r.id() == caso3).findFirst().orElseThrow().revision().accion());
        assertEquals("CONFIRMADO", despues.resultados().stream().filter(r -> r.id() == caso9).findFirst().orElseThrow().revision().accion());

        // volver a conciliar conserva la revisión (misma llave) y el CSV la incluye
        conciliacion.conciliar(E);
        assertTrue(conciliacion.csv(E, conciliacion.ultima(E).orElseThrow().id()).orElseThrow().contains(",OBSERVADO,Pedir nota de crédito"));

        // borrado en cascada: «Reiniciar demo» limpia resultados, diferencias y acciones
        cargas.reiniciar(E);
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM resultados").query(Long.class).single());
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM acciones_caso").query(Long.class).single());
    }

    @Test
    void elIngresoFuncionaConLaSemilla() {
        var sesion = acceso.ingresar("estudio.demo", "Clave-de-prueba-1", "127.0.0.1");
        assertEquals(Rol.ESTUDIO, sesion.rol());
    }
}
