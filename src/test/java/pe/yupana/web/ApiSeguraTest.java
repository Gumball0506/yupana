package pe.yupana.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import pe.yupana.dominio.Rol;
import pe.yupana.seguridad.Sesion;

/**
 * Controles de seguridad vistos desde el exterior (ISO 27001 A.8.26, A.8.12, A.8.15, A.8.3) y contrato JSON del panel.
 * Reinicio deshabilitado y 3 filas como máximo para poder probar esos límites.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:api;DATABASE_TO_LOWER=TRUE",
        "yupana.reinicio-habilitado=false", "yupana.max-filas=3", "yupana.semilla-datos=false"})
@DirtiesContext
class ApiSeguraTest {

    private static final String CABECERA = "ruc_proveedor,tipo_comprobante,serie,numero,fecha_emision,base_imponible,igv,total\n";

    @Autowired WebApplicationContext contexto;
    @Autowired JdbcClient jdbc;
    @Autowired FiltroCabecerasSeguridad filtro;

    MockMvc mvc;

    @BeforeEach
    void preparar() {
        MockHttpSession estudio = new MockHttpSession();
        estudio.setAttribute(ConfiguracionWeb.ATRIBUTO, new Sesion("estudio.demo", Rol.ESTUDIO, 1L));
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtro).defaultRequest(get("/").session(estudio)).build();
    }

    MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "text/csv", contenido);
    }

    MockMultipartFile csv(String nombre, String texto) {
        return archivo(nombre, texto.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cabecerasDeSeguridadEnLasRespuestas() throws Exception {
        mvc.perform(get("/api/cargas/historial"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", FiltroCabecerasSeguridad.POLITICA_CONTENIDO));
        assertFalse(FiltroCabecerasSeguridad.POLITICA_CONTENIDO.contains("script-src 'self' 'unsafe-inline'"),
                "los scripts en línea no deben permitirse");
    }

    @Test
    void reinicioDeshabilitadoDevuelve403YQuedaAuditado() throws Exception {
        mvc.perform(delete("/api/cargas"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errores[0]").value("«Reiniciar demo» está deshabilitado en este entorno."));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento = 'REINICIO_DENEGADO'").query(Long.class).single());
    }

    @Test
    void unaCargaAceptadaYUnaRechazadaDejanRastroSinContenido() throws Exception {
        long antes = jdbc.sql("SELECT COUNT(*) FROM auditoria").query(Long.class).single();
        String bueno = CABECERA + "20999910001,Factura,F001,000001,2026-10-03,100.00,18.00,118.00\n";
        mvc.perform(multipart("/api/cargas").file(csv("ok.csv", bueno)).param("origen", "compras")).andExpect(status().isOk());
        mvc.perform(multipart("/api/cargas").file(csv("mal.csv", "ruc_proveedor\n1\n")).param("origen", "compras"))
                .andExpect(status().isUnprocessableContent());
        var eventos = jdbc.sql("SELECT evento, detalle FROM auditoria ORDER BY id").query().listOfRows();
        assertEquals(antes + 2, eventos.size());
        assertEquals("CARGA_ACEPTADA", eventos.get(eventos.size() - 2).get("evento"));
        assertEquals("CARGA_RECHAZADA", eventos.getLast().get("evento"));
        assertFalse(eventos.stream().anyMatch(e -> e.get("detalle").toString().contains("20999910001")),
                "la auditoría no debe copiar datos del archivo");
    }

    @Test
    void archivoConMasFilasQueElTopeSeRechaza() throws Exception {
        String fila = "20999910001,Factura,F001,00000%d,2026-10-03,1,1,2\n";
        StringBuilder grande = new StringBuilder(CABECERA);
        for (int i = 1; i <= 6; i++) grande.append(fila.formatted(i));
        mvc.perform(multipart("/api/cargas").file(csv("grande.csv", grande.toString())).param("origen", "rce"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errores[0]").value("El archivo supera el máximo de 3 filas."));
    }

    @Test
    void xlsxFalsoSeRechazaSinAbrirlo() throws Exception {
        mvc.perform(multipart("/api/cargas").file(archivo("falso.xlsx", "esto no es un zip".getBytes(StandardCharsets.UTF_8)))
                        .param("origen", "rce"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errores[0]").value("No se pudo abrir el Excel. Verifica que sea un .xlsx válido."));
    }

    @Test
    void valoresHostilesNoSeRepitenCompletosEnElError() throws Exception {
        String hostil = "<script>alert(1)</script>".repeat(20);
        String texto = CABECERA + hostil + ",Factura,F001,000001,2026-10-03,1,1,2\n";
        String cuerpo = mvc.perform(multipart("/api/cargas").file(csv("x.csv", texto)).param("origen", "rce"))
                .andExpect(status().isUnprocessableContent()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(cuerpo.contains("…"), "el valor ajeno debe llegar recortado");
        assertFalse(cuerpo.contains(hostil));
    }

    @Test
    void nombreDeArchivoConRutaSeGuardaSoloComoNombre() throws Exception {
        String bueno = CABECERA + "20999910002,Factura,F001,000002,2026-10-03,100.00,18.00,118.00\n";
        mvc.perform(multipart("/api/cargas").file(csv("..\\..\\secreto\\ruta.csv", bueno)).param("origen", "compras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre_archivo").value("ruta.csv"));
    }

    @Test
    void errorInternoNoFiltraDetallesNiTrazas() throws Exception {
        String cuerpo = mvc.perform(get("/api/cargas/no-es-un-numero")).andReturn().getResponse().getContentAsString();
        assertFalse(cuerpo.contains("Exception"));
        assertFalse(cuerpo.contains("at pe.yupana"));
    }

    @Test
    void contratoJsonDelPanel() throws Exception {
        String compras = CABECERA + "20999910003,Factura,F001,000003,2026-10-05,350.00,63.00,413.00\n";
        String rce = CABECERA + "20999910003,Factura,F001,000003,2026-10-05,300.00,54.00,354.00\n";
        mvc.perform(multipart("/api/cargas").file(csv("c.csv", compras)).param("origen", "compras")).andExpect(status().isOk());
        mvc.perform(multipart("/api/cargas").file(csv("r.csv", rce)).param("origen", "rce")).andExpect(status().isOk());
        mvc.perform(post("/api/conciliar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.con_diferencia").value(1))
                .andExpect(jsonPath("$.igv_revision").value(63.0))
                .andExpect(jsonPath("$.diferencia_igv").value(9.0))
                .andExpect(jsonPath("$.archivo_compras").value("c.csv"))
                .andExpect(jsonPath("$.periodo").value("2026-10"))
                .andExpect(jsonPath("$.resultados[0].resultado").value("DIFERENCIA"))
                .andExpect(jsonPath("$.resultados[0].compras.base_imponible").value(350.0))
                .andExpect(jsonPath("$.resultados[0].rce.fecha_emision").value("2026-10-05"))
                .andExpect(jsonPath("$.resultados[0].diferencias[0].valor_compras").value("350.00"));
        mvc.perform(get("/api/cargas"))
                .andExpect(jsonPath("$.compras.origen_nombre").value("Compras de la empresa"))
                .andExpect(jsonPath("$.rce.resumen.base_imponible").value(300.0));
        mvc.perform(get("/api/eventos/resumen")).andExpect(jsonPath("$.motivos_fallo").exists());
    }
}
