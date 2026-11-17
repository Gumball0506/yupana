package pe.yupana.acceso;

import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.dominio.Rol;
import pe.yupana.empresas.ServicioEmpresas;
import pe.yupana.seguridad.Claves;
import pe.yupana.seguridad.EventoAuditoria;
import pe.yupana.seguridad.EventoAuditoria.Tipo;
import pe.yupana.seguridad.Sesion;
import pe.yupana.seguridad.SinSesion;

/** Ingreso y cuentas de acceso (ISO 27001 A.5.15 control de acceso, A.5.17 información de autenticación). */
@Service
public class ServicioAcceso {

    private static final String CLAVE_INCORRECTA = "Usuario o clave incorrectos.";
    private static final int CLAVE_MINIMA = 8;
    private static final int CLAVE_MAXIMA = 100;
    /** Hash de relleno: un usuario inexistente tarda lo mismo que una clave equivocada (no revela qué usuarios existen). */
    private static final String HASH_DE_RELLENO = Claves.cifrar("relleno-para-tiempo-constante");

    private final RepositorioUsuarios usuarios;
    private final ServicioEmpresas empresas;
    private final ApplicationEventPublisher eventos;

    public ServicioAcceso(RepositorioUsuarios usuarios, ServicioEmpresas empresas, ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.eventos = eventos;
    }

    // ponytail: sin límite de intentos; agregar bloqueo por IP/usuario antes de exponerlo a internet
    public Sesion ingresar(String usuario, String clave) {
        String nombre = usuario == null ? "" : usuario.strip().toLowerCase(Locale.ROOT);
        var cuenta = usuarios.buscar(nombre);
        boolean correcta = Claves.coincide(clave == null ? "" : clave, cuenta.map(Usuario::claveCifrada).orElse(HASH_DE_RELLENO));
        if (cuenta.isEmpty() || !correcta) {
            eventos.publishEvent(new EventoAuditoria(Tipo.INGRESO_FALLIDO, "usuario=" + nombre));
            throw new SinSesion(CLAVE_INCORRECTA);
        }
        Usuario u = cuenta.get();
        eventos.publishEvent(new EventoAuditoria(Tipo.INGRESO_EXITOSO, "usuario=" + u.usuario() + " rol=" + u.rol()));
        return new Sesion(u.usuario(), u.rol(), u.empresaId());
    }

    /** Cuenta para la MYPE: solo verá su empresa. */
    public void crearAccesoMype(long empresaId, String usuario, String clave, String creadoPor) {
        if (empresas.buscar(empresaId).isEmpty()) throw new ReglaIncumplida("Empresa no encontrada.");
        crear(usuario, clave, Rol.MYPE, empresaId);
        eventos.publishEvent(new EventoAuditoria(Tipo.ACCESO_CREADO, "usuario=" + usuario + " empresa=" + empresaId + " por=" + creadoPor));
    }

    public void crear(String usuario, String clave, Rol rol, Long empresaId) {
        if (usuario == null || !usuario.matches("[a-z0-9._-]{3,40}"))
            throw new ReglaIncumplida("Usuario: 3 a 40 caracteres en minúscula, números, punto, guion o guion bajo.");
        if (clave == null || clave.length() < CLAVE_MINIMA || clave.length() > CLAVE_MAXIMA)
            throw new ReglaIncumplida("La clave debe tener al menos " + CLAVE_MINIMA + " caracteres.");
        if ((rol == Rol.MYPE) != (empresaId != null)) throw new ReglaIncumplida("Solo una cuenta MYPE va unida a una empresa.");
        if (usuarios.existe(usuario)) throw new ReglaIncumplida("Ese usuario ya existe.");
        usuarios.crear(new Usuario(usuario, Claves.cifrar(clave), rol, empresaId));
    }

    public List<String> accesosDe(long empresaId) {
        return usuarios.deEmpresa(empresaId);
    }
}
