package pe.yupana.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Límite de peticiones por minuto y por IP en el ingreso y en la carga de archivos (ISO 27001 A.8.6). */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:limite;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false",
        "yupana.semilla-clave=Clave-de-prueba-1", "yupana.limite.login-por-minuto=4", "yupana.limite.carga-por-minuto=3"})
@DirtiesContext
class LimiteDePeticionesTest {

    static final String CLAVE = "Clave-de-prueba-1";
    static final String CSV = "ruc_proveedor,tipo_comprobante,serie,numero,fecha_emision,base_imponible,igv,total\n"
            + "20999910001,Factura,F001,000001,2026-10-03,100.00,18.00,118.00\n";

    @Autowired WebApplicationContext contexto;

    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    ResultActions login(String usuario, String clave, String ip) throws Exception {
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"clave\":\"" + clave + "\"}").with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    @Test
    void elIngresoTieneLimiteYCadaIpTieneElSuyo() throws Exception {
        // 4 por minuto desde la misma IP (con usuarios distintos para no activar el bloqueo por fallos)
        for (int i = 1; i <= 4; i++) login("nadie" + i, "x", "10.20.0.1").andExpect(status().isUnauthorized());
        login("estudio.demo", CLAVE, "10.20.0.1").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errores[0]").value(org.hamcrest.Matchers.startsWith("Demasiadas peticiones seguidas")));
        login("estudio.demo", CLAVE, "10.20.0.2").andExpect(status().isOk());   // otra IP no se ve afectada
    }

    @Test
    void laCargaDeArchivosTieneLimite() throws Exception {
        var sesion = (MockHttpSession) login("estudio.demo", CLAVE, "10.21.0.1").andExpect(status().isOk()).andReturn().getRequest().getSession();
        mvc.perform(post("/api/empresas/1/activar").session(sesion)).andExpect(status().isOk());
        for (int i = 1; i <= 3; i++)
            mvc.perform(multipart("/api/cargas").file(new MockMultipartFile("archivo", "c" + i + ".csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8)))
                    .param("origen", "compras").session(sesion).with(r -> { r.setRemoteAddr("10.21.0.9"); return r; })).andExpect(status().isOk());
        mvc.perform(multipart("/api/cargas").file(new MockMultipartFile("archivo", "c4.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8)))
                .param("origen", "compras").session(sesion).with(r -> { r.setRemoteAddr("10.21.0.9"); return r; }))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }
}
