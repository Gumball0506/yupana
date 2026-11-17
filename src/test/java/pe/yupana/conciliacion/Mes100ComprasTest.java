package pe.yupana.conciliacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import pe.yupana.carga.ServicioCargas;
import pe.yupana.dominio.LlaveComprobante;
import pe.yupana.sire.ServicioSire;

/**
 * Prueba del mes completo: 100 compras de 20 proveedores (octubre 2026) con diferencias plantadas a propósito.
 * Los archivos y la hoja de respuestas los genera scripts/generar_mes_100_compras.py con semilla fija; la hoja se calcula
 * por construcción (no ejecutando el sistema), así que esta prueba compara el sistema contra una respuesta independiente.
 * Dos caminos: propuesta RCE por archivo y propuesta del simulador SIRE (predicha por el mismo script).
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:mes100;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false"})
@DirtiesContext
class Mes100ComprasTest {

    static final Path CARPETA = Path.of("datos_demo", "mes_100_compras");
    static final long A = 1, B = 2; // empresas de la siembra

    @Autowired ServicioCargas cargas;
    @Autowired ServicioConciliacion conciliacion;
    @Autowired ServicioSire sire;
    @Autowired JdbcClient jdbc;

    static Properties esperado() throws IOException {
        var p = new Properties();
        try (var in = Files.newBufferedReader(CARPETA.resolve("resumen_esperado.properties"))) {
            p.load(in);
        }
        return p;
    }

    /** llave del comprobante → (resultado esperado, motivo) de la hoja de respuestas. */
    static Map<LlaveComprobante, String[]> respuestas() throws IOException {
        Map<LlaveComprobante, String[]> hoja = new HashMap<>();
        List<String> lineas = Files.readAllLines(CARPETA.resolve("respuestas_esperadas.csv"));
        for (String linea : lineas.subList(1, lineas.size())) {
            String[] c = linea.split(",", -1);
            hoja.put(LlaveComprobante.de(c[0], c[1], c[2], c[3]), new String[] {c[4], c[5]});
        }
        return hoja;
    }

    static void igual(String esperado, BigDecimal real, String que) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), que + ": esperado " + esperado + " y llegó " + real);
    }

    @Test
    void propuestaPorArchivo_81Cuadran_22NoCuadran() throws Exception {
        cargas.importar(A, "compras", "compras_100.csv", Files.readAllBytes(CARPETA.resolve("compras_100.csv")));
        cargas.importar(A, "rce", "rce_100.csv", Files.readAllBytes(CARPETA.resolve("rce_100.csv")));
        var c = conciliacion.detalle(A, conciliacion.conciliar(A)).orElseThrow();
        var p = esperado();

        assertEquals(Integer.parseInt(p.getProperty("archivo.total")), c.total());
        assertEquals(Integer.parseInt(p.getProperty("archivo.conciliados")), c.conciliados());
        assertEquals(Integer.parseInt(p.getProperty("archivo.no_cuadran")), c.conDiferencia());
        igual(p.getProperty("archivo.igv_bajo_revision"), c.igvRevision(), "IGV bajo revisión");
        igual(p.getProperty("archivo.diferencia_igv"), c.diferenciaIgv(), "diferencia de IGV");

        // comprobante por comprobante, contra la hoja de respuestas
        var hoja = respuestas();
        assertEquals(hoja.size(), c.resultados().size());
        for (var r : c.resultados()) {
            var k = LlaveComprobante.de(r.rucProveedor(), r.tipoComprobante(), r.serie(), r.numero());
            String[] esperadoPorLlave = hoja.get(k);
            assertTrue(esperadoPorLlave != null, "comprobante que no está en la hoja: " + k);
            assertEquals(esperadoPorLlave[0], r.resultado(), k + " (" + esperadoPorLlave[1] + ")");
        }
        // 15 con diferencia, 4 solo en compras, 3 solo en RCE y 7 que cuadran aunque se vean distintas
        assertEquals(15, c.resultados().stream().filter(r -> r.resultado().equals("DIFERENCIA")).count());
        assertEquals(4, c.resultados().stream().filter(r -> r.resultado().equals("SOLO_COMPRAS")).count());
        assertEquals(3, c.resultados().stream().filter(r -> r.resultado().equals("SOLO_RCE")).count());
        assertEquals(7, hoja.values().stream().filter(v -> v[0].equals("CONCILIADO") && !v[1].equals("igual")).count());
    }

    @Test
    void propuestaDelSimuladorSire() throws Exception {
        cargas.importar(B, "compras", "compras_100.csv", Files.readAllBytes(CARPETA.resolve("compras_100.csv")));
        var ticket = sire.solicitar(B, "2026-10");
        jdbc.sql("UPDATE sire_tickets SET creado = ? WHERE id = ?")
                .params(LocalDateTime.now().minusMinutes(1).withNano(0).toString(), ticket.id()).update();
        assertEquals("GENERADO", sire.ticket(B, ticket.id()).estado());
        sire.importar(B, ticket.id());
        var c = conciliacion.detalle(B, conciliacion.conciliar(B)).orElseThrow();
        var p = esperado();

        assertEquals(Integer.parseInt(p.getProperty("sire.total")), c.total());
        assertEquals(Integer.parseInt(p.getProperty("sire.conciliados")), c.conciliados());
        assertEquals(Integer.parseInt(p.getProperty("sire.no_cuadran")), c.conDiferencia());
        assertEquals(Integer.parseInt(p.getProperty("sire.solo_compras")),
                c.resultados().stream().filter(r -> r.resultado().equals("SOLO_COMPRAS")).count());
        assertEquals(Integer.parseInt(p.getProperty("sire.solo_rce")),
                c.resultados().stream().filter(r -> r.resultado().equals("SOLO_RCE")).count());
        assertEquals(Integer.parseInt(p.getProperty("sire.con_diferencia")),
                c.resultados().stream().filter(r -> r.resultado().equals("DIFERENCIA")).count());
        igual(p.getProperty("sire.igv_bajo_revision"), c.igvRevision(), "IGV bajo revisión (SIRE)");
        igual(p.getProperty("sire.diferencia_igv"), c.diferenciaIgv(), "diferencia de IGV (SIRE)");
    }
}
