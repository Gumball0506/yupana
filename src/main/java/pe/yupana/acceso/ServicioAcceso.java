package pe.yupana.acceso;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import pe.yupana.config.PropiedadesYupana;
import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.dominio.Rol;
import pe.yupana.dominio.Textos;
import pe.yupana.empresas.Empresa;
import pe.yupana.empresas.ServicioEmpresas;
import pe.yupana.seguridad.Claves;
import pe.yupana.seguridad.DemasiadosIntentos;
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
    private static final int LLAVE_MAXIMA = 64;
    /** Hash de relleno: un usuario inexistente tarda lo mismo que una clave equivocada (no revela qué usuarios existen). */
    private static final String HASH_DE_RELLENO = Claves.cifrar("relleno-para-tiempo-constante");

    private final RepositorioUsuarios usuarios;
    private final ServicioEmpresas empresas;
    private final ApplicationEventPublisher eventos;
    private final LimitadorDeIntentos limitador;

    public ServicioAcceso(RepositorioUsuarios usuarios, ServicioEmpresas empresas, ApplicationEventPublisher eventos,
                          PropiedadesYupana propiedades, Clock reloj) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.eventos = eventos;
        this.limitador = new LimitadorDeIntentos(propiedades.login().maxIntentos(),
                Duration.ofMinutes(propiedades.login().bloqueoMinutos()), reloj);
    }

    /**
     * Ingreso con bloqueo temporal: tras N fallos seguidos, la cuenta tecleada y la IP quedan bloqueadas un rato. La cuenta
     * se bloquea aunque no exista, así que el bloqueo no revela qué usuarios hay. Un ingreso correcto reinicia solo el
     * contador del usuario (no el de la IP, para que quien conoce una cuenta propia no pueda reiniciar su propia cuenta de IP).
     */
    public Sesion ingresar(String usuario, String clave, String ip) {
        String nombre = usuario == null ? "" : usuario.strip().toLowerCase(Locale.ROOT);
        String llaveUsuario = "u:" + Textos.acortar(nombre, LLAVE_MAXIMA);
        String llaveIp = "i:" + (ip == null || ip.isBlank() ? "desconocida" : Textos.acortar(ip, LLAVE_MAXIMA));
        exigirNoBloqueado(nombre, llaveUsuario, llaveIp);
        var cuenta = usuarios.buscar(nombre);
        boolean correcta = Claves.coincide(clave == null ? "" : clave, cuenta.map(Usuario::claveCifrada).orElse(HASH_DE_RELLENO));
        if (cuenta.isEmpty() || !correcta) {
            eventos.publishEvent(new EventoAuditoria(Tipo.INGRESO_FALLIDO, "usuario=" + nombre));
            boolean porUsuario = limitador.registrarFallo(llaveUsuario);
            boolean porIp = limitador.registrarFallo(llaveIp);
            if (porUsuario || porIp)
                eventos.publishEvent(new EventoAuditoria(Tipo.INGRESO_BLOQUEADO,
                        "bloqueo iniciado usuario=" + nombre + " ip=" + llaveIp.substring(2)));
            throw new SinSesion(CLAVE_INCORRECTA);
        }
        limitador.registrarExito(llaveUsuario);
        Usuario u = cuenta.get();
        if (u.rol() == Rol.MYPE && !empresas.buscar(u.empresaId()).map(Empresa::activa).orElse(false)) {
            eventos.publishEvent(new EventoAuditoria(Tipo.INGRESO_FALLIDO, "usuario=" + u.usuario() + " empresa desactivada"));
            throw new SinSesion("La empresa de esta cuenta está desactivada. Habla con tu estudio contable.");
        }
        eventos.publishEvent(new EventoAuditoria(Tipo.INGRESO_EXITOSO, "usuario=" + u.usuario() + " rol=" + u.rol()));
        return new Sesion(u.usuario(), u.rol(), u.empresaId());
    }

    private void exigirNoBloqueado(String nombre, String llaveUsuario, String llaveIp) {
        long espera = Math.max(limitador.segundosDeBloqueo(llaveUsuario), limitador.segundosDeBloqueo(llaveIp));
        if (espera > 0) {
            eventos.publishEvent(new EventoAuditoria(Tipo.INGRESO_BLOQUEADO,
                    "usuario=" + nombre + " ip=" + llaveIp.substring(2) + " restan_s=" + espera));
            throw new DemasiadosIntentos(espera);
        }
    }

    /**
     * Cambio de clave del propio usuario: pide la actual (un fallo cuenta para el mismo bloqueo del ingreso, así este
     * endpoint no sirve para adivinar claves) y valida la nueva con las mismas reglas que al crear la cuenta.
     */
    public void cambiarClave(Sesion sesion, String actual, String nueva, String ip) {
        String nombre = sesion.usuario();
        String llaveUsuario = "u:" + Textos.acortar(nombre, LLAVE_MAXIMA);
        String llaveIp = "i:" + (ip == null || ip.isBlank() ? "desconocida" : Textos.acortar(ip, LLAVE_MAXIMA));
        exigirNoBloqueado(nombre, llaveUsuario, llaveIp);
        var cuenta = usuarios.buscar(nombre).orElseThrow(() -> new SinSesion("Inicia sesión para continuar."));
        if (!Claves.coincide(actual == null ? "" : actual, cuenta.claveCifrada())) {
            limitador.registrarFallo(llaveUsuario);
            limitador.registrarFallo(llaveIp);
            eventos.publishEvent(new EventoAuditoria(Tipo.CLAVE_CAMBIO_FALLIDO, "usuario=" + nombre));
            throw new ReglaIncumplida("La clave actual no es correcta.");
        }
        validarClave(nueva);
        if (nueva.equals(actual)) throw new ReglaIncumplida("La clave nueva debe ser distinta de la actual.");
        if (nueva.equalsIgnoreCase(nombre)) throw new ReglaIncumplida("La clave no puede ser igual al usuario.");
        usuarios.cambiarClave(nombre, Claves.cifrar(nueva));
        limitador.registrarExito(llaveUsuario);
        eventos.publishEvent(new EventoAuditoria(Tipo.CLAVE_CAMBIADA, "usuario=" + nombre));
    }

    private static void validarClave(String clave) {
        if (clave == null || clave.length() < CLAVE_MINIMA || clave.length() > CLAVE_MAXIMA)
            throw new ReglaIncumplida("La clave debe tener al menos " + CLAVE_MINIMA + " caracteres.");
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
        validarClave(clave);
        if ((rol == Rol.MYPE) != (empresaId != null)) throw new ReglaIncumplida("Solo una cuenta MYPE va unida a una empresa.");
        if (usuarios.existe(usuario)) throw new ReglaIncumplida("Ese usuario ya existe.");
        usuarios.crear(new Usuario(usuario, Claves.cifrar(clave), rol, empresaId));
    }

    public boolean existe(String usuario) {
        return usuarios.existe(usuario);
    }

    public List<String> accesosDe(long empresaId) {
        return usuarios.deEmpresa(empresaId);
    }
}
