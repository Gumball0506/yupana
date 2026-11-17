package pe.yupana.mensajes;

import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import pe.yupana.carga.DemoReiniciada;
import pe.yupana.casos.ServicioCasos;
import pe.yupana.casos.VistaAviso;
import pe.yupana.dominio.Fechas;
import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.empresas.Empresa;
import pe.yupana.empresas.ServicioEmpresas;
import pe.yupana.seguridad.Sesion;

/**
 * Chat SIMULADO entre el estudio y la MYPE, con forma de WhatsApp. Nada sale de la aplicación: el envío real lo hace
 * la persona con el enlace wa.me que arma la pantalla.
 */
@Service
public class ServicioMensajes {

    private static final int TEXTO_MAXIMO = 1000;
    private static final String SALIENTE = "SALIENTE";
    private static final String ENTRANTE = "ENTRANTE";

    private final RepositorioMensajes repositorio;
    private final ServicioCasos casos;
    private final ServicioEmpresas empresas;

    public ServicioMensajes(RepositorioMensajes repositorio, ServicioCasos casos, ServicioEmpresas empresas) {
        this.repositorio = repositorio;
        this.casos = casos;
        this.empresas = empresas;
    }

    public ChatEmpresa chat(long empresaId) {
        String telefono = empresas.buscar(empresaId).map(Empresa::telefono).orElse(null);
        return new ChatEmpresa(telefono, repositorio.listar(empresaId));
    }

    /** El estudio escribe → SALIENTE; la MYPE responde → ENTRANTE. */
    public VistaMensaje escribir(Sesion sesion, String texto) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty() || limpio.length() > TEXTO_MAXIMO)
            throw new ReglaIncumplida("El mensaje debe tener entre 1 y " + TEXTO_MAXIMO + " caracteres.");
        return guardar(sesion.empresa(), sesion.esEstudio() ? SALIENTE : ENTRANTE, limpio, sesion.usuario());
    }

    /** Aviso del día (3.1-A) convertido en mensaje: *negritas* como en WhatsApp y sin enlaces internos de la aplicación. */
    public VistaMensaje enviarAviso(long empresaId, String usuario) {
        VistaAviso aviso = casos.simularAviso(empresaId);
        String cuerpo = aviso.cuerpo().replaceAll(" /#caso-\\d+", "")
                .replace("Aviso de demostración: no se envió por correo ni por WhatsApp.\n\n", "");
        return guardar(empresaId, SALIENTE, "*" + aviso.asunto() + "*\n\n" + cuerpo
                + "\nResponde a este chat si tienes el comprobante corregido.", usuario);
    }

    private VistaMensaje guardar(long empresaId, String direccion, String texto, String usuario) {
        String fecha = Fechas.ahora();
        long id = repositorio.guardar(empresaId, direccion, texto, fecha, usuario);
        return new VistaMensaje(id, direccion, texto, fecha, usuario);
    }

    @EventListener
    public void alReiniciar(DemoReiniciada evento) {
        repositorio.borrarDeEmpresa(evento.empresaId());
    }

    public Optional<String> ultimaFecha(long empresaId) {
        return repositorio.ultimaFecha(empresaId);
    }
}
