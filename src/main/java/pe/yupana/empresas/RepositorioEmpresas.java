package pe.yupana.empresas;

import java.util.List;
import java.util.Optional;

import pe.yupana.dominio.Ruc;

/** Puerto de salida de las empresas del estudio. */
public interface RepositorioEmpresas {

    long crear(Ruc ruc, String razonSocial, String telefono);

    Optional<Empresa> buscar(long id);

    boolean existeRuc(Ruc ruc);

    /** Ordenadas por razón social. */
    List<Empresa> todas();
}
