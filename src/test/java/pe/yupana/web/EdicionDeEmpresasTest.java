package pe.yupana.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import pe.yupana.carga.ServicioCargas;

/** Editar y desactivar empresas: solo el estudio, sin borrar datos, con auditoría y cerrando las sesiones de la MYPE. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:empresas_edicion;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false",
        "yupana.semilla-clave=Clave-de-prueba-1"})
@DirtiesContext
class EdicionDeEmpresasTest {

    static final String CLAVE = "Clave-de-prueba-1";

    @Autowired WebApplicationContext contexto;
    @Autowired JdbcClient jdbc;
    @Autowired ServicioCargas cargas;

    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    ResultActions login(String usuario, String ip) throws Exception {
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"clave\":\"" + CLAVE + "\"}").with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    MockHttpSession sesion(String usuario, String ip) throws Exception {
        return (MockHttpSession) login(usuario, ip).andExpect(status().isOk()).andReturn().getRequest().getSession();
    }

    String json(String razon, String telefono) {
        return "{\"razon_social\":\"" + razon + "\",\"telefono\":" + (telefono == null ? "null" : "\"" + telefono + "\"") + "}";
    }

    @Test
    void elEstudioEditaRazonSocialYTelefonoPeroNoElRuc() throws Exception {
        var estudio = sesion("estudio.demo", "10.8.0.1");
        mvc.perform(put("/api/empresas/2").session(estudio).contentType(MediaType.APPLICATION_JSON).content(json("  Servicios Nuevos S.A.C. ", "+51 999-111-222")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razon_social").value("Servicios Nuevos S.A.C."))
                .andExpect(jsonPath("$.telefono").value("51999111222"))
                .andExpect(jsonPath("$.ruc").value("20999910026"))
                .andExpect(jsonPath("$.activa").value(true));
        mvc.perform(put("/api/empresas/2").session(estudio).contentType(MediaType.APPLICATION_JSON).content(json("", null)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/empresas/2").session(estudio).contentType(MediaType.APPLICATION_JSON).content(json("X", "abc")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/empresas/999").session(estudio).contentType(MediaType.APPLICATION_JSON).content(json("X", null)))
                .andExpect(status().isUnprocessableContent());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento = 'EMPRESA_EDITADA'").query(Long.class).single());
    }

    @Test
    void unaMypeNoPuedeEditarNiDesactivarNada() throws Exception {
        var mype = sesion("mype.a", "10.8.1.1");
        mvc.perform(put("/api/empresas/1").session(mype).contentType(MediaType.APPLICATION_JSON).content(json("Hackeada", null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/empresas/1/desactivar").session(mype)).andExpect(status().isForbidden());
        mvc.perform(post("/api/empresas/1/reactivar").session(mype)).andExpect(status().isForbidden());
        assertEquals("Comercial Demo A S.A.C.", jdbc.sql("SELECT razon_social FROM empresas WHERE id = 1").query(String.class).single());
    }

    @Test
    void desactivarConservaLosDatosCierraLaSesionDeLaMypeYSePuedeReactivar() throws Exception {
        cargas.importar(3, "compras", "c.csv", Files.readAllBytes(Path.of("datos_demo", "yupana_compras_demo.csv")));
        var estudio = sesion("estudio.demo", "10.8.2.1");
        // la MYPE de la empresa A tiene sesión abierta
        var mype = sesion("mype.a", "10.8.2.2");
        mvc.perform(get("/api/yo").session(mype)).andExpect(status().isOk());

        mvc.perform(post("/api/empresas/1/desactivar").session(estudio)).andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));

        mvc.perform(get("/api/yo").session(mype)).andExpect(status().isUnauthorized());          // su sesión se cerró
        login("mype.a", "10.8.2.3").andExpect(status().isUnauthorized())                        // y no puede volver a entrar
                .andExpect(jsonPath("$.errores[0]").value("La empresa de esta cuenta está desactivada. Habla con tu estudio contable."));
        mvc.perform(post("/api/empresas/1/activar").session(estudio)).andExpect(status().isUnprocessableContent()); // el estudio no la abre

        // el panel la sigue mostrando, marcada como desactivada, y los datos de las demás no se tocan
        mvc.perform(get("/api/empresas").session(estudio)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==1)].activa").value(false))
                .andExpect(jsonPath("$[?(@.id==3)].cargas").value(1));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM empresas WHERE id = 1").query(Long.class).single());     // no se borró

        mvc.perform(post("/api/empresas/1/reactivar").session(estudio)).andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(true));
        login("mype.a", "10.8.2.4").andExpect(status().isOk());
        mvc.perform(post("/api/empresas/1/activar").session(estudio)).andExpect(status().isOk());

        assertTrue(jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento IN ('EMPRESA_DESACTIVADA','EMPRESA_REACTIVADA')").query(Long.class).single() >= 2);
    }

    @Test
    void sinSesionNadaFunciona() throws Exception {
        mvc.perform(put("/api/empresas/1").contentType(MediaType.APPLICATION_JSON).content(json("X", null))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/empresas/1/desactivar")).andExpect(status().isUnauthorized());
    }
}
