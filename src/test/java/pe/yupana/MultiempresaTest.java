package pe.yupana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import pe.yupana.carga.RepositorioCargas;
import pe.yupana.carga.ServicioCargas;
import pe.yupana.casos.ServicioCasos;
import pe.yupana.conciliacion.ServicioConciliacion;
import pe.yupana.conciliacion.VistaResultado;
import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.dominio.Rol;
import pe.yupana.dominio.SimuladorSire;
import pe.yupana.empresas.ServicioEmpresas;
import pe.yupana.mensajes.ServicioMensajes;
import pe.yupana.panel.PanelEstudio;
import pe.yupana.seguridad.Sesion;
import pe.yupana.sire.ServicioSire;

/** H5 sobre la arquitectura por capas: aislamiento entre empresas, simulador SIRE, chat y panel del estudio. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:multi;DATABASE_TO_LOWER=TRUE", "yupana.semilla-datos=false"})
@DirtiesContext
class MultiempresaTest {

    static final long A = 1, B = 2; // empresas de la siembra

    @Autowired ServicioCargas cargas;
    @Autowired RepositorioCargas repositorioCargas;
    @Autowired ServicioConciliacion conciliacion;
    @Autowired ServicioCasos casos;
    @Autowired ServicioEmpresas empresas;
    @Autowired ServicioSire sire;
    @Autowired ServicioMensajes mensajes;
    @Autowired PanelEstudio panel;
    @Autowired JdbcClient jdbc;

    void demo(long empresa) throws Exception {
        cargas.importar(empresa, "compras", "c.csv", Files.readAllBytes(Path.of("datos_demo", "yupana_compras_demo.csv")));
        cargas.importar(empresa, "rce", "r.csv", Files.readAllBytes(Path.of("datos_demo", "yupana_rce_demo.csv")));
        conciliacion.conciliar(empresa);
    }

    @Test
    void altaDeEmpresa() {
        var nueva = empresas.crear("20999910042", "  Bodega Demo D S.A.C. ", "+51 999-000-111", "tester");
        assertEquals("Bodega Demo D S.A.C.", nueva.razonSocial());
        assertEquals("51999000111", nueva.telefono());
        assertThrows(ReglaIncumplida.class, () -> empresas.crear("20999910018", "Repetida", null, "tester")); // ya sembrada
        assertThrows(ReglaIncumplida.class, () -> empresas.crear("20999910051", "Tel malo", "abc", "tester"));
        assertThrows(ReglaIncumplida.class, () -> empresas.crear("20999910041", "Dígito malo", null, "tester"));
    }

    @Test
    void cadaEmpresaVeSoloLoSuyo() throws Exception {
        demo(A);
        var c = conciliacion.ultima(A).orElseThrow();
        long idCaso = c.resultados().get(2).id();

        assertTrue(conciliacion.ultima(B).isEmpty());
        assertTrue(cargas.ultimas(B).isEmpty());
        assertTrue(conciliacion.detalle(B, c.id()).isEmpty());
        assertTrue(conciliacion.csv(B, c.id()).isEmpty());
        assertTrue(cargas.detalle(B, c.idCompras(), 10).isEmpty());
        assertThrows(ReglaIncumplida.class, () -> casos.revisar(B, idCaso, "observar", null, "intruso"));
        casos.revisar(A, idCaso, "observar", null, "mype.a");

        cargas.reiniciar(B); // reiniciar B no toca A
        assertTrue(conciliacion.ultima(A).isPresent());

        // Panel del estudio: A con 3 observados y 2 pendientes (uno ya observado)
        var filaA = panel.filas().stream().filter(f -> f.id() == A).findFirst().orElseThrow();
        assertEquals(3, filaA.conciliacion().conDiferencia());
        assertEquals(2, filaA.conciliacion().pendientes());
        assertEquals(List.of("mype.a"), filaA.accesos());
        cargas.reiniciar(A);
    }

    @Test
    void simuladorSire() throws Exception {
        assertThrows(ReglaIncumplida.class, () -> sire.solicitar(B, "2026-10")); // sin compras
        cargas.importar(B, "compras", "c.csv", Files.readAllBytes(Path.of("datos_demo", "yupana_compras_demo.csv")));
        assertEquals("2026-10", sire.periodos(B).getFirst()); // se propone el mes con compras, no el actual vacío

        var t = sire.solicitar(B, "2026-10");
        assertEquals("EN_PROCESO", t.estado());
        assertFalse(t.variaciones().isEmpty());
        assertThrows(ReglaIncumplida.class, () -> sire.importar(B, t.id())); // aún en proceso
        assertThrows(ReglaIncumplida.class, () -> sire.ticket(A, t.id()));  // ticket de otra empresa

        jdbc.sql("UPDATE sire_tickets SET creado = ? WHERE id = ?").params(LocalDateTime.now().minusMinutes(1).withNano(0).toString(), t.id()).update();
        assertEquals("GENERADO", sire.ticket(B, t.id()).estado());
        long idCompras = repositorioCargas.ultimaDe(B, "compras").orElseThrow();
        assertEquals(sire.archivo(B, t.id()),
                ServicioSire.csv(SimuladorSire.para(repositorioCargas.comprobantes(idCompras), "2026-10").comprobantes()));

        sire.importar(B, t.id());
        assertEquals("IMPORTADO", sire.ticket(B, t.id()).estado());
        var c = conciliacion.detalle(B, conciliacion.conciliar(B)).orElseThrow();
        assertTrue(c.resultados().stream().map(VistaResultado::resultado).toList().contains("SOLO_RCE"));
        assertEquals("ACEPTADO_SIMULADO", sire.aceptar(B, t.id()).estado());

        cargas.reiniciar(B); // el Observer borra también los tickets
        assertTrue(sire.recientes(B).isEmpty());
    }

    @Test
    void chatSimulado() throws Exception {
        assertThrows(ReglaIncumplida.class, () -> mensajes.enviarAviso(A, "x")); // sin conciliación
        demo(A);
        String texto = mensajes.enviarAviso(A, "estudio.demo").texto();
        assertTrue(texto.startsWith("*Yupana (demo) · 3 de 3 casos por revisar*"), texto);
        assertFalse(texto.contains("/#caso-"));
        assertTrue(texto.contains("no es crédito fiscal perdido ni recuperado"));
        mensajes.escribir(new Sesion("mype.a", Rol.MYPE, A), "Ya pedí la nota de crédito");
        assertEquals(List.of("SALIENTE", "ENTRANTE"), mensajes.chat(A).mensajes().stream().map(m -> m.direccion()).toList());
        assertEquals("51900000001", mensajes.chat(A).telefono());
        assertTrue(mensajes.chat(B).mensajes().isEmpty());
        assertThrows(ReglaIncumplida.class, () -> mensajes.escribir(new Sesion("mype.a", Rol.MYPE, A), " "));
        cargas.reiniciar(A); // el Observer borra también el chat
        assertTrue(mensajes.chat(A).mensajes().isEmpty());
    }
}
