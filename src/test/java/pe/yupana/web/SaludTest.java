package pe.yupana.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** /actuator/health responde sin sesión y sin detalles; ningún otro endpoint de actuator queda a la vista. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:salud;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false"})
@DirtiesContext
class SaludTest {

    @Autowired WebApplicationContext contexto;

    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    @Test
    void healthEstaArribaSinDetalles() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otrosEndpointsNoEstanExpuestos() throws Exception {
        for (String ruta : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/metrics"})
            mvc.perform(get(ruta)).andExpect(status().isNotFound());
    }
}
