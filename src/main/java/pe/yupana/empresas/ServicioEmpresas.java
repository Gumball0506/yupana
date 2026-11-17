package pe.yupana.empresas;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.dominio.Ruc;
import pe.yupana.seguridad.EventoAuditoria;
import pe.yupana.seguridad.EventoAuditoria.Tipo;

/** Alta y consulta de las empresas que atiende el estudio contable. */
@Service
public class ServicioEmpresas {

    private static final int RAZON_SOCIAL_MAXIMA = 100;

    private final RepositorioEmpresas repositorio;
    private final ApplicationEventPublisher eventos;

    public ServicioEmpresas(RepositorioEmpresas repositorio, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.eventos = eventos;
    }

    public Empresa crear(String ruc, String razonSocial, String telefono, String usuario) {
        Ruc valido = new Ruc(ruc);
        String razon = razonValida(razonSocial);
        if (repositorio.existeRuc(valido)) throw new ReglaIncumplida("Ya existe una empresa con el RUC " + ruc + ".");
        long id = repositorio.crear(valido, razon, telefonoNormalizado(telefono));
        eventos.publishEvent(new EventoAuditoria(Tipo.EMPRESA_CREADA, "empresa=" + id + " ruc=" + ruc + " por=" + usuario));
        return repositorio.buscar(id).orElseThrow();
    }

    private static String razonValida(String razonSocial) {
        String razon = razonSocial == null ? "" : razonSocial.strip();
        if (razon.isEmpty() || razon.length() > RAZON_SOCIAL_MAXIMA)
            throw new ReglaIncumplida("La razón social es obligatoria (máx. " + RAZON_SOCIAL_MAXIMA + " caracteres).");
        return razon;
    }

    /** Edita razón social y teléfono. El RUC es la identidad de la empresa y no se cambia. */
    public Empresa editar(long id, String razonSocial, String telefono, String usuario) {
        existente(id);
        repositorio.actualizar(id, razonValida(razonSocial), telefonoNormalizado(telefono));
        eventos.publishEvent(new EventoAuditoria(Tipo.EMPRESA_EDITADA, "empresa=" + id + " por=" + usuario));
        return existente(id);
    }

    /** Desactiva en vez de borrar: los datos se conservan y se pueden reactivar. */
    public Empresa desactivar(long id, String usuario) {
        return cambiarEstado(id, false, Tipo.EMPRESA_DESACTIVADA, usuario);
    }

    public Empresa reactivar(long id, String usuario) {
        return cambiarEstado(id, true, Tipo.EMPRESA_REACTIVADA, usuario);
    }

    private Empresa cambiarEstado(long id, boolean activa, Tipo tipo, String usuario) {
        existente(id);
        repositorio.cambiarEstado(id, activa);
        eventos.publishEvent(new EventoAuditoria(tipo, "empresa=" + id + " por=" + usuario));
        return existente(id);
    }

    private Empresa existente(long id) {
        return repositorio.buscar(id).orElseThrow(() -> new ReglaIncumplida("Empresa no encontrada."));
    }

    /** «+51 999-999-999» → «51999999999»; vacío → null. */
    private static String telefonoNormalizado(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String digitos = telefono.replaceAll("[\\s+-]", "");
        if (!digitos.matches("\\d{8,15}"))
            throw new ReglaIncumplida("El teléfono va solo con dígitos y código de país (ej. 51999999999).");
        return digitos;
    }

    public Optional<Empresa> buscar(long id) {
        return repositorio.buscar(id);
    }

    public List<Empresa> todas() {
        return repositorio.todas();
    }
}
