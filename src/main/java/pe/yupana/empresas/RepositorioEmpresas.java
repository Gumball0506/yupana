package pe.yupana.empresas;

import java.util.List;
import java.util.Optional;

import pe.yupana.dominio.Ruc;

/** Puerto de salida de las empresas del estudio. */
public interface RepositorioEmpresas {

    long crear(Ruc ruc, String razonSocial, String telefono);

    Optional<Empresa> buscar(long id);

    boolean existeRuc(Ruc ruc);

    /** Cambia razón social y teléfono; el RUC no se edita. */
    void actualizar(long id, String razonSocial, String telefono);

    /** Desactivar no borra nada: conserva cargas, conciliaciones y casos. */
    void cambiarEstado(long id, boolean activa);

    /** Ordenadas por razón social. */
    List<Empresa> todas();
}
