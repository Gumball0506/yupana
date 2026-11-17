package pe.yupana.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import pe.yupana.acceso.RelojMutable;

/** Bloqueo de ingreso visto desde afuera: 5 fallos → 429, desbloqueo por tiempo, mismo mensaje, reinicio al acertar. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:bloqueo;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false",
        "yupana.semilla-clave=Clave-de-prueba-1"})
@Import(BloqueoDeIngresoTest.Config.class)
@DirtiesContext
class BloqueoDeIngresoTest {

    static final String CLAVE = "Clave-de-prueba-1";

    @TestConfiguration
    static class Config {
        @Bean @Primary RelojMutable relojDePrueba() { return new RelojMutable(); }
    }

    @Autowired WebApplicationContext contexto;
    @Autowired JdbcClient jdbc;
    @Autowired RelojMutable reloj;

    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    ResultActions ingresar(String usuario, String clave, String ip) throws Exception {
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"clave\":\"" + clave + "\"}")
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    @Test
    void alQuintoFalloBloqueaYTrasElTiempoSeDesbloquea() throws Exception {
        for (int i = 1; i <= 5; i++)
            ingresar("mype.a", "mala", "10.0.0." + i).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errores[0]").value("Usuario o clave incorrectos."));
        // sexto intento: bloqueado aunque la clave sea la correcta, y desde otra IP
        ingresar("mype.a", CLAVE, "10.0.0.99").andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
        assertTrue(jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento = 'INGRESO_BLOQUEADO'").query(Long.class).single() >= 2);

        reloj.avanzar(Duration.ofMinutes(15));
        ingresar("mype.a", CLAVE, "10.0.0.99").andExpect(status().isOk());
    }

    @Test
    void unUsuarioInexistenteSeBloqueaIgualYNoRevelaQueNoExiste() throws Exception {
        for (int i = 1; i <= 5; i++)
            ingresar("fantasma", "x", "10.1.0." + i).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errores[0]").value("Usuario o clave incorrectos."));
        ingresar("fantasma", "x", "10.1.0.50").andExpect(status().isTooManyRequests());
    }

    @Test
    void laIpTambienSeBloqueaSiProbaVariosUsuarios() throws Exception {
        for (int i = 1; i <= 5; i++) ingresar("usuario" + i, "x", "10.2.0.1").andExpect(status().isUnauthorized());
        ingresar("estudio.demo", CLAVE, "10.2.0.1").andExpect(status().isTooManyRequests());
        ingresar("estudio.demo", CLAVE, "10.2.0.2").andExpect(status().isOk()); // otra IP sí puede
    }

    @Test
    void unIngresoCorrectoReiniciaElContadorDelUsuario() throws Exception {
        for (int i = 1; i <= 4; i++) ingresar("estudio.demo", "mala", "10.3.0." + i).andExpect(status().isUnauthorized());
        ingresar("estudio.demo", CLAVE, "10.3.0.9").andExpect(status().isOk());
        for (int i = 1; i <= 4; i++) ingresar("estudio.demo", "mala", "10.3.1." + i).andExpect(status().isUnauthorized());
        ingresar("estudio.demo", CLAVE, "10.3.1.9").andExpect(status().isOk()); // 4 fallos otra vez: aún no bloquea
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE evento = 'INGRESO_BLOQUEADO' AND detalle LIKE '%10.3.%'")
                .query(Long.class).single());
    }
}
