package pe.yupana.seguridad;

import java.io.Serializable;

import pe.yupana.dominio.Rol;

/**
 * Quién hace la petición y sobre qué empresa (ISO 27001 A.8.3 restricción de acceso). La empresa sale SIEMPRE de la
 * sesión del servidor, nunca de un parámetro del navegador: así una MYPE no puede pedir datos de otra.
 *
 * @param empresaId empresa activa; null si el estudio aún no eligió una
 */
public record Sesion(String usuario, Rol rol, Long empresaId) implements Serializable {

    public boolean esEstudio() {
        return rol == Rol.ESTUDIO;
    }

    public void exigirEstudio() {
        if (!esEstudio()) throw new AccesoDenegado("Solo el estudio contable puede hacer esto.");
    }

    public long empresa() {
        if (empresaId == null) throw new EmpresaNoElegida();
        return empresaId;
    }
}
