package pe.yupana.carga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Pruebas T01–T06 de la pestaña PRUEBAS (entrega 1.2). BD en memoria: no toca ./data. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:test;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false"})
class CargaTest {

    static final long E = 1; // empresa A de la siembra

    private static final String CABECERA = "ruc_proveedor,tipo_comprobante,serie,numero,fecha_emision,base_imponible,igv,total\n";

    @Autowired ServicioCargas cargas;

    long subir(String origen, String archivo) throws Exception {
        return cargas.importar(E, origen, archivo, Files.readAllBytes(Path.of("datos_demo", archivo)));
    }

    List<VistaComprobante> filas(long id) {
        return cargas.detalle(E, id, RepositorioCargas.TODAS_LAS_FILAS).orElseThrow().filas();
    }

    long importarTexto(String origen, String nombre, String csv) {
        return cargas.importar(E, origen, nombre, csv.getBytes(StandardCharsets.UTF_8));
    }

    List<String> errores(String origen, String archivo, byte[] contenido) {
        return assertThrows(ErrorCarga.class, () -> cargas.importar(E, origen, archivo, contenido)).errores();
    }

    List<String> erroresDeTexto(String archivo, String csv) {
        return errores("rce", archivo, csv.getBytes(StandardCharsets.UTF_8));
    }

    List<String> errores(String archivo) throws Exception {
        return errores("compras", archivo, Files.readAllBytes(Path.of("datos_demo", archivo)));
    }

    @Test
    void t01_t02_t03_validosCsvXlsxYPersistencia() throws Exception {
        for (String a : List.of("yupana_compras_demo.csv", "yupana_compras_demo.xlsx")) {
            var f = filas(subir("compras", a));
            assertEquals(10, f.size(), a);
            assertEquals("000001", f.get(0).numero());   // ceros preservados
            assertEquals("F001", f.get(0).serie());
            assertEquals(0, new BigDecimal("350").compareTo(f.get(2).baseImponible()));
        }
        for (String a : List.of("yupana_rce_demo.csv", "yupana_rce_demo.xlsx")) {
            var f = filas(subir("rce", a));
            assertEquals(10, f.size(), a);
            assertEquals("2026-10-10", f.get(6).fechaEmision()); // doc 7 sembrado
        }
        // T03: una consulta nueva (como tras recargar) devuelve lo guardado con su origen
        var u = cargas.ultimas(E);
        assertEquals(10, u.get("compras").totalFilas());
        assertEquals("Propuesta RCE (simulada)", u.get("rce").origenNombre());
    }

    @Test
    void t04_faltaColumnaIgv_noGuarda() throws Exception {
        Object antes = cargas.ultimas(E).get("compras");
        assertTrue(errores("error_T04_sin_igv.csv").contains("Falta la columna IGV."));
        assertEquals(antes, cargas.ultimas(E).get("compras"));
    }

    @Test
    void t05_soloEncabezados() throws Exception {
        assertTrue(errores("error_T05_vacio.csv").getFirst().contains("solo tiene encabezados"));
        assertEquals(List.of("El archivo está vacío."), errores("rce", "v.csv", new byte[0]));
    }

    @Test
    void t06_importeInvalido_indicaFilaYCampo() throws Exception {
        assertEquals(List.of("Fila 5, columna total: 'cuatrocientos' no es un número válido (usa punto decimal)."),
                errores("error_T06_importe_invalido.csv"));
    }

    @Test
    void validacionesExtra() {
        String puntoYComa = "ruc_proveedor;tipo_comprobante;serie;numero;fecha_emision;base_imponible;igv;total\n"
                + "20999910001;Factura;F001;000001;2026-10-03;100.00;18.00;118.00\n";
        assertEquals(1, filas(importarTexto("rce", "pc.csv", puntoYComa)).size());

        String malo = CABECERA
                + "2099,Factura,F001,000001,03/10/2026,-5,18,118\n"
                + "20999910001,Factura,F001,000002,2026-10-03,1,1,1\n"
                + "20999910001,Factura,F001,000002,2026-10-03,1,1,1\n";
        var e = erroresDeTexto("m.csv", malo);
        assertTrue(e.stream().anyMatch(x -> x.contains("ruc_proveedor")));
        assertTrue(e.stream().anyMatch(x -> x.contains("fecha_emision")));
        assertTrue(e.stream().anyMatch(x -> x.contains("negativo")));
        assertTrue(e.stream().anyMatch(x -> x.contains("duplicado")));

        String largo = CABECERA + "20999910001,Factura,F001," + "1".repeat(25) + ",2026-10-03,1,1,2\n";
        assertEquals(List.of("Fila 2, columna numero: admite hasta 20 caracteres."), erroresDeTexto("l.csv", largo));

        assertTrue(errores("rce", "x.pdf", "%PDF".getBytes()).getFirst().contains("no admitido"));
        assertTrue(errores("sunat", "x.csv", "a".getBytes()).getFirst().contains("Origen inválido"));
        assertEquals(List.of("a,b", "c"), LectorCsv.partir("\"a,b\",c", ','));
    }

    @Test
    void comaDecimalYDuplicadosSinDistinguirMayusculas() {
        // CSV de Excel en español: ';' como separador y ',' como decimal
        String pc = "ruc_proveedor;tipo_comprobante;serie;numero;fecha_emision;base_imponible;igv;total\n"
                + "20999910001;Factura;F001;000001;2026-10-03;100,00;18,00;118,00\n";
        var f = filas(importarTexto("rce", "pc.csv", pc));
        assertEquals(0, new BigDecimal("118.00").compareTo(f.getFirst().total()));
        // «1,416» es ambiguo (¿miles?): se rechaza en vez de leerlo como 1.416
        String miles = "ruc_proveedor;tipo_comprobante;serie;numero;fecha_emision;base_imponible;igv;total\n"
                + "20999910001;Factura;F001;000001;2026-10-03;1,200;216;1,416\n";
        assertTrue(erroresDeTexto("m.csv", miles).getFirst().contains("no es un número"));

        // f001 = F001 y «01» = «Factura»: es el mismo comprobante → duplicado
        String dup = CABECERA + "20999910001,Factura,F001,000001,2026-10-03,1,1,2\n"
                + "20999910001,01,f001,000001,2026-10-03,1,1,2\n";
        assertTrue(erroresDeTexto("d.csv", dup).getFirst().contains("duplicado"));
    }

    @Test
    void t06_señalaLaCeldaExacta() {
        var e = assertThrows(ErrorCarga.class, () -> subir("compras", "error_T06_importe_invalido.csv"));
        assertEquals(List.of(new Celda(5, "total")), e.celdas());
        assertEquals(1, e.vista().filas().size()); // solo la fila con error
        assertEquals(5, e.vista().filas().getFirst().n());
    }

    @Test
    void encabezadosTolerantesYAdvertencias() {
        String csv = "RUC Proveedor,Tipo Comprobante,Serie,Número,Fecha Emisión,Base Imponible,IGV,Total,Glosa\n"
                + "20999910001,Factura,F001,000001,2026-10-03,100.00,18.00,118.00,x\n"
                + "20999910002,Factura,F001,000002,2026-10-03,100.00,18.00,120.00,y\n";
        long id = importarTexto("rce", "t.csv", csv);
        var adv = cargas.detalle(E, id, RepositorioCargas.TODAS_LAS_FILAS).orElseThrow().advertencias();
        assertEquals(2, filas(id).size());
        assertTrue(adv.getFirst().contains("Glosa"));                  // columna extra ignorada
        assertTrue(adv.get(1).startsWith("Fila 3: el total (120.00)")); // total ≠ base + IGV: avisa, no bloquea
        assertEquals("fecha_emision", EsquemaArchivo.normalizarEncabezado(" Fecha  Emisión "));
    }

    @Test
    void resumenHistorialYReinicio() throws Exception {
        long id = subir("compras", "yupana_compras_demo.csv");
        var r = cargas.detalle(E, id, 10).orElseThrow().resumen();
        assertEquals(10L, r.comprobantes());
        assertEquals("2026-10-03", r.desde());
        assertEquals("2026-10-12", r.hasta());
        assertEquals(0, new BigDecimal("706.50").compareTo(r.igv())); // suma a mano de la columna igv
        assertEquals(id, cargas.historial(E).getFirst().id());

        assertTrue(cargas.reiniciar(E) > 0);
        assertTrue(cargas.historial(E).isEmpty());
        assertTrue(cargas.ultimas(E).isEmpty());
    }
}
