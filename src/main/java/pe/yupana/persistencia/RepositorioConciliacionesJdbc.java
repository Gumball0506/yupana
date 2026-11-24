package pe.yupana.persistencia;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import pe.yupana.carga.VistaComprobante;
import pe.yupana.conciliacion.NuevaConciliacion;
import pe.yupana.conciliacion.RepositorioConciliaciones;
import pe.yupana.conciliacion.VistaConciliacion;
import pe.yupana.conciliacion.VistaDiferencia;
import pe.yupana.conciliacion.VistaResultado;
import pe.yupana.conciliacion.VistaRevision;
import pe.yupana.dominio.Diferencia;
import pe.yupana.dominio.LlaveComprobante;
import pe.yupana.dominio.ResultadoComparacion;

/** Adaptador JDBC del puerto RepositorioConciliaciones. */
@Repository
class RepositorioConciliacionesJdbc implements RepositorioConciliaciones {

    private static final RowMapper<VistaComprobante> A_COMPROBANTE = (rs, i) -> new VistaComprobante(
            rs.getString("ruc_proveedor"), rs.getString("tipo_comprobante"), rs.getString("serie"), rs.getString("numero"),
            rs.getString("fecha_emision"), rs.getBigDecimal("base_imponible"), rs.getBigDecimal("igv"), rs.getBigDecimal("total"));

    private final JdbcClient jdbc;

    RepositorioConciliacionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- Escritura ----------

    @Override
    public long guardar(NuevaConciliacion nueva) {
        var clave = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO conciliaciones(empresa_id, id_compras, id_rce, fecha, total, conciliados, con_diferencia, igv_revision, diferencia_igv)
                VALUES (?,?,?,?,?,?,?,?,?)""")
                .params(nueva.empresaId(), nueva.idCompras(), nueva.idRce(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                        nueva.total(), nueva.conciliados(), nueva.conDiferencia(), nueva.igvBajoRevision(), nueva.diferenciaIgv())
                .update(clave, "id");
        long id = clave.getKey().longValue();
        List<ResultadoComparacion> resultados = nueva.resultados();
        for (int i = 0; i < resultados.size(); i++) guardarResultado(id, i + 1, resultados.get(i));
        return id;
    }

    private void guardarResultado(long idConciliacion, int orden, ResultadoComparacion r) {
        var clave = new GeneratedKeyHolder();
        var doc = r.comprobante();
        jdbc.sql("""
                INSERT INTO resultados(id_conciliacion, orden, ruc_proveedor, tipo_comprobante, serie, numero, resultado, causa, igv_compras, igv_rce)
                VALUES (?,?,?,?,?,?,?,?,?,?)""")
                .params(idConciliacion, orden, doc.rucProveedor(), doc.tipoComprobante(), doc.serie(), doc.numero(),
                        r.tipo().name(), r.causa(), r.igvCompras(), r.igvRce())
                .update(clave, "id");
        long idResultado = clave.getKey().longValue();
        for (Diferencia d : r.diferencias())
            jdbc.sql("INSERT INTO diferencias(id_resultado, campo, valor_compras, valor_rce) VALUES (?,?,?,?)")
                    .params(idResultado, d.campo().columna(), d.valorCompras(), d.valorRce()).update();
    }

    // ---------- Lectura ----------

    @Override
    public Optional<VistaConciliacion> ultima(long empresaId) {
        return jdbc.sql("SELECT id FROM conciliaciones WHERE empresa_id = ? ORDER BY id DESC LIMIT 1").param(empresaId)
                .query(Long.class).optional().flatMap(id -> detalle(empresaId, id));
    }

    @Override
    public Optional<VistaConciliacion> ultima(long empresaId, String periodo) {
        return jdbc.sql("""
                SELECT c.id FROM conciliaciones c
                JOIN importaciones ic ON ic.id = c.id_compras
                WHERE c.empresa_id = ? AND ic.periodo = ? ORDER BY c.id DESC LIMIT 1
                """).params(empresaId, periodo).query(Long.class).optional().flatMap(id -> detalle(empresaId, id));
    }

    private record Cabecera(long id, long idCompras, long idRce, String fecha, int total, int conciliados, int conDiferencia,
                            BigDecimal igvRevision, BigDecimal diferenciaIgv, String archivoCompras, String archivoRce, String periodo) {}

    @Override
    public Optional<VistaConciliacion> detalle(long empresaId, long id) {
        return jdbc.sql("""
                SELECT c.*, ic.periodo, ic.nombre_archivo AS archivo_compras, ir.nombre_archivo AS archivo_rce
                FROM conciliaciones c JOIN importaciones ic ON ic.id = c.id_compras JOIN importaciones ir ON ir.id = c.id_rce
                WHERE c.id = ? AND c.empresa_id = ?""").params(id, empresaId)
                .query((rs, i) -> new Cabecera(rs.getLong("id"), rs.getLong("id_compras"), rs.getLong("id_rce"),
                        rs.getString("fecha"), rs.getInt("total"), rs.getInt("conciliados"), rs.getInt("con_diferencia"),
                        rs.getBigDecimal("igv_revision"), rs.getBigDecimal("diferencia_igv"),
                        rs.getString("archivo_compras"), rs.getString("archivo_rce"), rs.getString("periodo")))
                .optional()
                .map(this::armar);
    }

    private VistaConciliacion armar(Cabecera c) {
        Map<LlaveComprobante, VistaComprobante> compras = comprobantesPorLlave(c.idCompras());
        Map<LlaveComprobante, VistaComprobante> rce = comprobantesPorLlave(c.idRce());
        List<VistaResultado> resultados = jdbc.sql("SELECT * FROM resultados WHERE id_conciliacion = ? ORDER BY orden")
                .param(c.id()).query((rs, i) -> new FilaResultado(rs.getLong("id"), rs.getLong("id_conciliacion"), rs.getInt("orden"),
                        rs.getString("ruc_proveedor"), rs.getString("tipo_comprobante"), rs.getString("serie"),
                        rs.getString("numero"), rs.getString("resultado"), rs.getString("causa"),
                        rs.getBigDecimal("igv_compras"), rs.getBigDecimal("igv_rce")))
                .list().stream().map(f -> f.aVista(compras, rce, diferenciasDe(f.id()), revisionDe(f.id()))).toList();
        return new VistaConciliacion(c.id(), c.idCompras(), c.idRce(), c.fecha(), c.total(), c.conciliados(), c.conDiferencia(),
                c.igvRevision(), c.diferenciaIgv(), c.archivoCompras(), c.archivoRce(), resultados, c.periodo() == null ? periodoDe(compras) : c.periodo());
    }

    private record FilaResultado(long id, long idConciliacion, int orden, String ruc, String tipo, String serie, String numero,
                                 String resultado, String causa, BigDecimal igvCompras, BigDecimal igvRce) {

        VistaResultado aVista(Map<LlaveComprobante, VistaComprobante> compras, Map<LlaveComprobante, VistaComprobante> rce,
                              List<VistaDiferencia> diferencias, VistaRevision revision) {
            LlaveComprobante llave = LlaveComprobante.de(ruc, tipo, serie, numero);
            return new VistaResultado(id, idConciliacion, orden, ruc, tipo, serie, numero, resultado, causa, igvCompras, igvRce,
                    compras.get(llave), rce.get(llave), diferencias, revision);
        }
    }

    private List<VistaDiferencia> diferenciasDe(long idResultado) {
        return jdbc.sql("SELECT campo, valor_compras, valor_rce FROM diferencias WHERE id_resultado = ? ORDER BY id")
                .param(idResultado)
                .query((rs, i) -> new VistaDiferencia(rs.getString("campo"), rs.getString("valor_compras"), rs.getString("valor_rce")))
                .list();
    }

    /** Última acción sobre el caso (la tabla acciones_caso guarda el historial completo). */
    private VistaRevision revisionDe(long idResultado) {
        return jdbc.sql("SELECT accion, nota, fecha, usuario FROM acciones_caso WHERE id_resultado = ? ORDER BY id DESC LIMIT 1")
                .param(idResultado)
                .query((rs, i) -> new VistaRevision(rs.getString("accion"), rs.getString("nota"), rs.getString("fecha"), rs.getString("usuario")))
                .optional().orElse(null);
    }

    private Map<LlaveComprobante, VistaComprobante> comprobantesPorLlave(long idImportacion) {
        Map<LlaveComprobante, VistaComprobante> porLlave = new HashMap<>();
        jdbc.sql("""
                SELECT ruc_proveedor, tipo_comprobante, serie, numero, fecha_emision, base_imponible, igv, total
                FROM comprobantes WHERE id_importacion = ?""").param(idImportacion).query(A_COMPROBANTE).list()
                .forEach(v -> porLlave.put(LlaveComprobante.de(v.rucProveedor(), v.tipoComprobante(), v.serie(), v.numero()), v));
        return porLlave;
    }

    /** Periodo = mes de la primera fecha de compras (para las migas del panel). */
    private static String periodoDe(Map<LlaveComprobante, VistaComprobante> compras) {
        return compras.values().stream().map(VistaComprobante::fechaEmision).min(Comparator.naturalOrder())
                .map(fecha -> fecha.substring(0, 7)).orElse(null);
    }
}
