package pe.yupana.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

import pe.yupana.acceso.ServicioAcceso;
import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.empresas.Empresa;
import pe.yupana.empresas.ServicioEmpresas;
import pe.yupana.panel.FilaPanel;
import pe.yupana.panel.PanelEstudio;
import pe.yupana.seguridad.Sesion;

/** Panel multiempresa del estudio contable. Todo aquí es solo para el rol ESTUDIO. */
@RestController
@RequestMapping("/api/empresas")
class EmpresasController {

    private final ServicioEmpresas empresas;
    private final ServicioAcceso acceso;
    private final PanelEstudio panel;

    EmpresasController(ServicioEmpresas empresas, ServicioAcceso acceso, PanelEstudio panel) {
        this.empresas = empresas;
        this.acceso = acceso;
        this.panel = panel;
    }

    @GetMapping
    List<FilaPanel> panel(Sesion sesion) {
        sesion.exigirEstudio();
        return panel.filas();
    }

    @PostMapping
    Empresa crear(@RequestBody Map<String, String> cuerpo, Sesion sesion) {
        sesion.exigirEstudio();
        return empresas.crear(cuerpo.get("ruc"), cuerpo.get("razon_social"), cuerpo.get("telefono"), sesion.usuario());
    }

    /** Cambia la empresa activa DENTRO de la sesión del servidor; las demás peticiones la toman de ahí. */
    @PostMapping("/{id}/activar")
    AccesoController.Yo activar(@PathVariable long id, Sesion sesion, HttpSession http) {
        sesion.exigirEstudio();
        Empresa empresa = empresas.buscar(id).orElseThrow(() -> new ReglaIncumplida("Empresa no encontrada."));
        Sesion activa = new Sesion(sesion.usuario(), sesion.rol(), empresa.id());
        http.setAttribute(ConfiguracionWeb.ATRIBUTO, activa);
        return new AccesoController.Yo(activa.usuario(), activa.rol().name(), empresa);
    }

    @PostMapping("/{id}/accesos")
    ResponseEntity<Void> crearAcceso(@PathVariable long id, @RequestBody Map<String, String> cuerpo, Sesion sesion) {
        sesion.exigirEstudio();
        acceso.crearAccesoMype(id, cuerpo.get("usuario"), cuerpo.get("clave"), sesion.usuario());
        return ResponseEntity.noContent().build();
    }
}
