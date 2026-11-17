package pe.yupana.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Control de acceso visto desde afuera (ISO 27001 A.5.15, A.8.3, A.8.5): login, roles, empresa activa y salida. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:acceso;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false",
        "yupana.semilla-clave=Clave-de-prueba-1"})
@DirtiesContext
class AccesoTest {

    static final String CLAVE = "Clave-de-prueba-1";

    @Autowired WebApplicationContext contexto;
    @Autowired JdbcClient jdbc;

    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    MockHttpSession ingresar(String usuario) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"clave\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
    }

    @Test
    void sinSesionTodoDevuelve401YLaClaveNoSeGuardaEnTexto() throws Exception {
        mvc.perform(get("/api/yo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cargas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/eventos/resumen")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"mype.a\",\"clave\":\"mala\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errores[0]").value("Usuario o clave incorrectos."));
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"no.existe\",\"clave\":\"x\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errores[0]").value("Usuario o clave incorrectos."));
        String hash = jdbc.sql("SELECT clave_hash FROM usuarios WHERE usuario = 'mype.a'").query(String.class).single();
        assertTrue(hash.startsWith("pbkdf2$") && !hash.contains(CLAVE));
        assertEquals(2, jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento = 'INGRESO_FALLIDO'").query(Long.class).single());
    }

    @Test
    void mypeSoloVeSuEmpresaYNoUsaFuncionesDelEstudio() throws Exception {
        var mype = ingresar("mype.a");
        mvc.perform(get("/api/yo").session(mype)).andExpect(jsonPath("$.rol").value("MYPE"))
                .andExpect(jsonPath("$.empresa.ruc").value("20999910018"));
        mvc.perform(get("/api/conciliaciones/ultima").session(mype)).andExpect(status().isOk());
        mvc.perform(post("/api/conciliar").session(mype)).andExpect(status().isForbidden());
        mvc.perform(get("/api/empresas").session(mype)).andExpect(status().isForbidden());
        mvc.perform(post("/api/empresas/2/activar").session(mype)).andExpect(status().isForbidden());
        mvc.perform(get("/api/sire/periodos").session(mype)).andExpect(status().isForbidden());
        mvc.perform(post("/api/mensajes").session(mype).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Hola\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.direccion").value("ENTRANTE"));
        assertTrue(jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento = 'ACCESO_DENEGADO'").query(Long.class).single() >= 4);
    }

    @Test
    void estudioEligeEmpresaYPuedeSalir() throws Exception {
        var estudio = ingresar("estudio.demo");
        mvc.perform(get("/api/cargas").session(estudio)).andExpect(status().isConflict());
        mvc.perform(get("/api/empresas").session(estudio)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].razon_social").exists());
        mvc.perform(post("/api/empresas").session(estudio).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruc\":\"20999910041\",\"razon_social\":\"X\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/empresas/2/activar").session(estudio)).andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa.ruc").value("20999910026"));
        mvc.perform(get("/api/mensajes").session(estudio)).andExpect(status().isOk()).andExpect(jsonPath("$.mensajes.length()").value(0));
        mvc.perform(post("/api/logout").session(estudio)).andExpect(status().isNoContent());
        mvc.perform(get("/api/yo").session(estudio)).andExpect(status().isUnauthorized());
    }
}
