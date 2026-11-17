package pe.yupana.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import pe.yupana.uso.RepositorioEventos;

/** Si la base no responde, la API dice 503 con un mensaje claro y un código de soporte; nunca una traza ni un 500 mudo. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:base_caida;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false"})
@DirtiesContext
class BaseNoDisponibleTest {

    static final String EVENTO = "{\"sesion\":\"abcdef12-3456\",\"tipo\":\"demo_abierta\"}";

    @Autowired WebApplicationContext contexto;
    @MockitoBean RepositorioEventos eventos;

    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    @Test
    void sinConexionConLaBaseResponde503() throws Exception {
        doThrow(new CannotGetJdbcConnectionException("no hay conexión con jdbc:postgresql://db:5432/yupana")).when(eventos).registrar(any());
        mvc.perform(post("/api/eventos").contentType(MediaType.APPLICATION_JSON).content(EVENTO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.errores[0]").value(org.hamcrest.Matchers.startsWith("El servicio no puede acceder a la base de datos")))
                .andExpect(jsonPath("$.errores[0]").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc:"))));
    }

    @Test
    void unaConsultaQueSePasaDeTiempoTambienEs503() throws Exception {
        doThrow(new QueryTimeoutException("tiempo agotado")).when(eventos).registrar(any());
        mvc.perform(post("/api/eventos").contentType(MediaType.APPLICATION_JSON).content(EVENTO)).andExpect(status().isServiceUnavailable());
    }
}
