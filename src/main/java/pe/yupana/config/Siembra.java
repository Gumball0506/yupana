package pe.yupana.config;

import java.io.IOException;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import pe.yupana.acceso.ServicioAcceso;
import pe.yupana.carga.ServicioCargas;
import pe.yupana.conciliacion.ServicioConciliacion;
import pe.yupana.dominio.Rol;
import pe.yupana.empresas.ServicioEmpresas;

/** Base vacía → 3 empresas ficticias, un usuario del estudio y uno de la MYPE A (y, si se pide, datos demo en A). */
@Component
class Siembra implements ApplicationRunner {

    private static final String SISTEMA = "siembra";

    private final ServicioEmpresas empresas;
    private final ServicioAcceso accesos;
    private final ServicioCargas cargas;
    private final ServicioConciliacion conciliacion;
    private final PropiedadesYupana propiedades;

    Siembra(ServicioEmpresas empresas, ServicioAcceso accesos, ServicioCargas cargas, ServicioConciliacion conciliacion,
            PropiedadesYupana propiedades) {
        this.empresas = empresas;
        this.accesos = accesos;
        this.cargas = cargas;
        this.conciliacion = conciliacion;
        this.propiedades = propiedades;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!empresas.todas().isEmpty() || propiedades.semillaClave() == null) return;
        long a = empresas.crear("20999910018", "Comercial Demo A S.A.C.", "51900000001", SISTEMA).id();
        empresas.crear("20999910026", "Servicios Demo B E.I.R.L.", null, SISTEMA);
        empresas.crear("20999910034", "Taller Demo C S.R.L.", null, SISTEMA);
        accesos.crear("estudio.demo", propiedades.semillaClave(), Rol.ESTUDIO, null);
        accesos.crear("mype.a", propiedades.semillaClave(), Rol.MYPE, a);
        if (!propiedades.semillaDatos()) return;
        cargas.importar(a, "compras", "yupana_compras_demo.csv", leer("static/ejemplos/yupana_compras_demo.csv"));
        cargas.importar(a, "rce", "yupana_rce_demo.csv", leer("static/ejemplos/yupana_rce_demo.csv"));
        conciliacion.conciliar(a);
    }

    private static byte[] leer(String ruta) throws IOException {
        try (var entrada = new ClassPathResource(ruta).getInputStream()) {
            return entrada.readAllBytes();
        }
    }
}
