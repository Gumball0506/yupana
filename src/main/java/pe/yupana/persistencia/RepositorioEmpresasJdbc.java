package pe.yupana.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import pe.yupana.dominio.Fechas;
import pe.yupana.dominio.Ruc;
import pe.yupana.empresas.Empresa;
import pe.yupana.empresas.RepositorioEmpresas;

@Repository
class RepositorioEmpresasJdbc implements RepositorioEmpresas {

    private static final RowMapper<Empresa> A_EMPRESA = (rs, i) ->
            new Empresa(rs.getLong("id"), rs.getString("ruc"), rs.getString("razon_social"), rs.getString("telefono"));

    private final JdbcClient jdbc;

    RepositorioEmpresasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long crear(Ruc ruc, String razonSocial, String telefono) {
        var clave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO empresas(ruc, razon_social, telefono, creada) VALUES (?,?,?,?)")
                .params(ruc.valor(), razonSocial, telefono, Fechas.ahora()).update(clave, "id");
        return clave.getKey().longValue();
    }

    @Override
    public Optional<Empresa> buscar(long id) {
        return jdbc.sql("SELECT id, ruc, razon_social, telefono FROM empresas WHERE id = ?").param(id).query(A_EMPRESA).optional();
    }

    @Override
    public boolean existeRuc(Ruc ruc) {
        return jdbc.sql("SELECT COUNT(*) FROM empresas WHERE ruc = ?").param(ruc.valor()).query(Long.class).single() > 0;
    }

    @Override
    public List<Empresa> todas() {
        return jdbc.sql("SELECT id, ruc, razon_social, telefono FROM empresas ORDER BY razon_social").query(A_EMPRESA).list();
    }
}
