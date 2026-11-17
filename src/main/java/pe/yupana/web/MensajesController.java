package pe.yupana.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.yupana.mensajes.ChatEmpresa;
import pe.yupana.mensajes.ServicioMensajes;
import pe.yupana.mensajes.VistaMensaje;
import pe.yupana.seguridad.Sesion;

/** Chat simulado tipo WhatsApp entre el estudio y la MYPE de la empresa activa. */
@RestController
@RequestMapping("/api/mensajes")
class MensajesController {

    private final ServicioMensajes mensajes;

    MensajesController(ServicioMensajes mensajes) {
        this.mensajes = mensajes;
    }

    @GetMapping
    ChatEmpresa chat(Sesion sesion) {
        return mensajes.chat(sesion.empresa());
    }

    @PostMapping
    VistaMensaje escribir(@RequestBody Map<String, String> cuerpo, Sesion sesion) {
        return mensajes.escribir(sesion, cuerpo.get("texto"));
    }

    @PostMapping("/aviso")
    VistaMensaje enviarAviso(Sesion sesion) {
        sesion.exigirEstudio();
        return mensajes.enviarAviso(sesion.empresa(), sesion.usuario());
    }
}
