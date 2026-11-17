package pe.yupana.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.yupana.acceso.RepositorioUsuarios;
import pe.yupana.acceso.Usuario;
import pe.yupana.dominio.Fechas;
import pe.yupana.dominio.Rol;

@Repository
class RepositorioUsuariosJdbc implements RepositorioUsuarios {

    private final JdbcClient jdbc;

    RepositorioUsuariosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscar(String usuario) {
        return jdbc.sql("SELECT usuario, clave_hash, rol, empresa_id FROM usuarios WHERE usuario = ?").param(usuario)
                .query((rs, i) -> new Usuario(rs.getString("usuario"), rs.getString("clave_hash"), Rol.valueOf(rs.getString("rol")),
                        rs.getObject("empresa_id", Long.class)))
                .optional();
    }

    @Override
    public boolean existe(String usuario) {
        return jdbc.sql("SELECT COUNT(*) FROM usuarios WHERE usuario = ?").param(usuario).query(Long.class).single() > 0;
    }

    @Override
    public void crear(Usuario u) {
        jdbc.sql("INSERT INTO usuarios(usuario, clave_hash, rol, empresa_id, creado) VALUES (?,?,?,?,?)")
                .params(u.usuario(), u.claveCifrada(), u.rol().name(), u.empresaId(), Fechas.ahora()).update();
    }

    @Override
    public void cambiarClave(String usuario, String claveCifrada) {
        jdbc.sql("UPDATE usuarios SET clave_hash = ? WHERE usuario = ?").params(claveCifrada, usuario).update();
    }

    @Override
    public List<String> deEmpresa(long empresaId) {
        return jdbc.sql("SELECT usuario FROM usuarios WHERE empresa_id = ? ORDER BY usuario").param(empresaId).query(String.class).list();
    }
}
