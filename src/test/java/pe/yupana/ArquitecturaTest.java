package pe.yupana;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Reglas de arquitectura verificables (Clean Architecture): las dependencias apuntan hacia el dominio.
 * Se revisan los «import» de los fuentes; no hace falta una librería extra.
 */
class ArquitecturaTest {

    private static final Path FUENTES = Path.of("src/main/java/pe/yupana");
    /** Capa de aplicación: casos de uso y puertos. Ninguno conoce el SQL ni la web. */
    private static final List<String> APLICACION =
            List.of("carga", "conciliacion", "uso", "empresas", "acceso", "casos", "sire", "mensajes", "panel");

    private static List<String> importsDe(String paquete) throws IOException {
        try (Stream<Path> archivos = Files.walk(FUENTES.resolve(paquete))) {
            return archivos.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> {
                        try { return Files.readAllLines(p).stream().filter(l -> l.startsWith("import ")).map(l -> p.getFileName() + " → " + l); }
                        catch (IOException e) { throw new IllegalStateException(e); }
                    }).toList();
        }
    }

    private static void ningunoContiene(List<String> imports, String... prohibidos) {
        for (String prohibido : prohibidos)
            assertTrue(imports.stream().noneMatch(i -> i.contains(prohibido)), "Dependencia prohibida «" + prohibido + "»: "
                    + imports.stream().filter(i -> i.contains(prohibido)).toList());
    }

    @Test
    void elDominioNoDependeDeSpringNiDeOtrasCapas() throws IOException {
        ningunoContiene(importsDe("dominio"), "org.springframework", "jakarta.", "pe.yupana.carga", "pe.yupana.conciliacion",
                "pe.yupana.persistencia", "pe.yupana.web", "pe.yupana.seguridad", "com.fasterxml", "pe.yupana.empresas", "pe.yupana.acceso",
                "pe.yupana.casos", "pe.yupana.sire", "pe.yupana.mensajes", "pe.yupana.panel", "pe.yupana.uso", "pe.yupana.config");
    }

    @Test
    void laAplicacionNoConoceElAdaptadorJdbcNiLaWeb() throws IOException {
        for (String paquete : APLICACION) {
            ningunoContiene(importsDe(paquete), "pe.yupana.persistencia", "pe.yupana.web", "org.springframework.jdbc.core.simple");
        }
    }

    @Test
    void soloLaPersistenciaUsaJdbc() throws IOException {
        for (String paquete : Stream.concat(APLICACION.stream(), Stream.of("dominio", "web", "config")).toList())
            ningunoContiene(importsDe(paquete), "org.springframework.jdbc");
    }

    @Test
    void laWebNoTocaLaPersistencia() throws IOException {
        ningunoContiene(importsDe("web"), "pe.yupana.persistencia");
    }

    @Test
    void nadieDependeDelPanelSalvoLaWeb() throws IOException {
        for (String paquete : APLICACION.stream().filter(p -> !p.equals("panel")).toList())
            ningunoContiene(importsDe(paquete), "pe.yupana.panel");
    }
}
