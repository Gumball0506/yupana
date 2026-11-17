package pe.yupana.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import pe.yupana.acceso.ServicioAcceso;
import pe.yupana.dominio.Rol;

/** Cambio de clave (ISO 27001 A.5.17): pide la actual, valida la nueva, cierra las otras sesiones y queda auditado. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:clave;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false",
        "yupana.semilla-clave=Clave-de-prueba-1"})
@DirtiesContext
class CambioDeClaveTest {

    static final String CLAVE = "Clave-de-prueba-1";

    @Autowired WebApplicationContext contexto;
    @Autowired JdbcClient jdbc;
    @Autowired ServicioAcceso acceso;

    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        // un usuario por prueba: el bloqueo y el cambio de clave de una no afectan a las otras
        for (String u : new String[] {"usuario.cambio", "usuario.valida", "usuario.bloqueo"})
            if (jdbc.sql("SELECT COUNT(*) FROM usuarios WHERE usuario = ?").param(u).query(Long.class).single() == 0)
                acceso.crear(u, CLAVE, Rol.ESTUDIO, null);
    }

    ResultActions login(String usuario, String clave, String ip) throws Exception {
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"clave\":\"" + clave + "\"}").with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    MockHttpSession sesion(String usuario, String clave, String ip) throws Exception {
        return (MockHttpSession) login(usuario, clave, ip).andExpect(status().isOk()).andReturn().getRequest().getSession();
    }

    ResultActions cambiar(MockHttpSession s, String actual, String nueva, String ip) throws Exception {
        return mvc.perform(post("/api/clave").session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actual\":\"" + actual + "\",\"nueva\":\"" + nueva + "\"}").with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    @Test
    void cambiaLaClaveYCierraLasOtrasSesiones() throws Exception {
        var s1 = sesion("usuario.cambio", CLAVE, "10.5.0.1");
        var s2 = sesion("usuario.cambio", CLAVE, "10.5.0.2");
        String antes = jdbc.sql("SELECT clave_hash FROM usuarios WHERE usuario = 'usuario.cambio'").query(String.class).single();

        cambiar(s1, CLAVE, "Clave-nueva-2026", "10.5.0.1").andExpect(status().isNoContent());

        mvc.perform(get("/api/yo").session(s1)).andExpect(status().isOk());              // la actual sigue abierta
        mvc.perform(get("/api/yo").session(s2)).andExpect(status().isUnauthorized());    // la otra se cerró
        login("usuario.cambio", CLAVE, "10.5.0.3").andExpect(status().isUnauthorized());          // la vieja ya no sirve
        login("usuario.cambio", "Clave-nueva-2026", "10.5.0.4").andExpect(status().isOk());       // la nueva sí

        String despues = jdbc.sql("SELECT clave_hash FROM usuarios WHERE usuario = 'usuario.cambio'").query(String.class).single();
        assertNotEquals(antes, despues);
        assertTrue(despues.startsWith("pbkdf2$") && !despues.contains("Clave-nueva-2026"));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento = 'CLAVE_CAMBIADA' AND detalle LIKE '%usuario.cambio%'")
                .query(Long.class).single());
    }

    @Test
    void validaLaClaveNuevaYLaActual() throws Exception {
        var s = sesion("usuario.valida", CLAVE, "10.6.0.1");
        cambiar(s, "otra-clave", "Clave-nueva-2026", "10.6.0.1").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errores[0]").value("La clave actual no es correcta."));
        cambiar(s, CLAVE, "corta", "10.6.0.1").andExpect(status().isUnprocessableContent());
        cambiar(s, CLAVE, CLAVE, "10.6.0.1").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errores[0]").value("La clave nueva debe ser distinta de la actual."));
        cambiar(s, CLAVE, "Usuario.Valida", "10.6.0.1").andExpect(status().isUnprocessableContent());
        // nada cambió: la clave original sigue sirviendo
        login("usuario.valida", CLAVE, "10.6.0.2").andExpect(status().isOk());
        assertTrue(jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento = 'CLAVE_CAMBIO_FALLIDO'").query(Long.class).single() >= 1);
    }

    @Test
    void sinSesionNoSePuedeCambiar() throws Exception {
        mvc.perform(post("/api/clave").contentType(MediaType.APPLICATION_JSON).content("{\"actual\":\"x\",\"nueva\":\"y\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adivinarLaActualBloqueaIgualQueElIngreso() throws Exception {
        // otro usuario para no afectar a las demás pruebas
        var s = sesion("usuario.bloqueo", CLAVE, "10.7.0.1");
        for (int i = 1; i <= 5; i++) cambiar(s, "mala-" + i, "Clave-nueva-2026", "10.7.0." + i).andExpect(status().isUnprocessableContent());
        cambiar(s, CLAVE, "Clave-nueva-2026", "10.7.0.9").andExpect(status().isTooManyRequests());
        assertFalse(jdbc.sql("SELECT clave_hash FROM usuarios WHERE usuario = 'usuario.cambio'").query(String.class).single().isEmpty());
    }
}
