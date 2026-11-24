package pe.yupana.persistencia;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import pe.yupana.carga.ArchivoImportado;
import pe.yupana.carga.EsquemaArchivo;
import pe.yupana.carga.Lote;
import pe.yupana.carga.RepositorioCargas;
import pe.yupana.carga.ResumenImportacion;
import pe.yupana.carga.ResumenLote;
import pe.yupana.carga.VistaComprobante;
import pe.yupana.carga.VistaImportacion;
import pe.yupana.dominio.Comprobante;

/** Adaptador JDBC del puerto RepositorioCargas. Consultas parametrizadas y siempre filtradas por empresa. */
@Repository
class RepositorioCargasJdbc implements RepositorioCargas {

    private static final String COLUMNAS_SQL = String.join(",", EsquemaArchivo.COLUMNAS);
    private static final int VISTA_PREVIA = 10;

    private static final RowMapper<VistaComprobante> A_VISTA = (rs, i) -> new VistaComprobante(
            rs.getString("ruc_proveedor"), rs.getString("tipo_comprobante"), rs.getString("serie"), rs.getString("numero"),
            rs.getString("fecha_emision"), rs.getBigDecimal("base_imponible"), rs.getBigDecimal("igv"), rs.getBigDecimal("total"));

    private static final RowMapper<Comprobante> A_DOMINIO = (rs, i) -> new Comprobante(
            rs.getString("ruc_proveedor"), rs.getString("tipo_comprobante"), rs.getString("serie"), rs.getString("numero"),
            rs.getString("fecha_emision"), rs.getBigDecimal("base_imponible"), rs.getBigDecimal("igv"), rs.getBigDecimal("total"));

    private final JdbcClient jdbc;

    RepositorioCargasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long guardar(long empresaId, String origen, String nombreArchivo, Lote lote, byte[] contenido, String periodo) {
        var clave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO importaciones(empresa_id, origen, nombre_archivo, fecha, total_filas, advertencias, periodo) VALUES (?,?,?,?,?,?,?)")
                .params(empresaId, origen, nombreArchivo, ahora(), lote.comprobantes().size(), String.join("\n", lote.advertencias()), periodo)
                .update(clave, "id");
        long id = clave.getKey().longValue();
        jdbc.sql("INSERT INTO archivos_importacion(importacion_id, contenido) VALUES (?,?)")
                .params(id, contenido).update();
        for (Comprobante c : lote.comprobantes())
            jdbc.sql("INSERT INTO comprobantes(id_importacion," + COLUMNAS_SQL + ") VALUES (?,?,?,?,?,?,?,?,?)")
                    .params(id, c.rucProveedor(), c.tipoComprobante(), c.serie(), c.numero(), c.fechaEmision(),
                            c.baseImponible(), c.igv(), c.total())
                    .update();
        return id;
    }

    @Override
    public Optional<ArchivoImportado> archivo(long empresaId, long id) {
        return jdbc.sql("""
                SELECT i.nombre_archivo, a.contenido
                FROM importaciones i JOIN archivos_importacion a ON a.importacion_id = i.id
                WHERE i.empresa_id = ? AND i.id = ?
                """).params(empresaId, id)
                .query((rs, fila) -> new ArchivoImportado(rs.getString("nombre_archivo"), rs.getBytes("contenido")))
                .optional();
    }

    private record Cabecera(long id, String origen, String nombreArchivo, String fecha, int totalFilas, String advertencias) {}

    @Override
    public Optional<VistaImportacion> detalle(long empresaId, long id, int limiteFilas) {
        return jdbc.sql("SELECT id, origen, nombre_archivo, fecha, total_filas, advertencias FROM importaciones WHERE id = ? AND empresa_id = ?")
                .params(id, empresaId)
                .query((rs, i) -> new Cabecera(rs.getLong("id"), rs.getString("origen"), rs.getString("nombre_archivo"),
                        rs.getString("fecha"), rs.getInt("total_filas"), rs.getString("advertencias")))
                .optional()
                .map(c -> new VistaImportacion(c.id(), c.origen(), c.nombreArchivo(), c.fecha(), c.totalFilas(),
                        partirAdvertencias(c.advertencias()), EsquemaArchivo.ORIGENES.get(c.origen()),
                        resumen(id), filas(id, limiteFilas)));
    }

    private static List<String> partirAdvertencias(String guardadas) {
        return guardadas == null || guardadas.isEmpty() ? List.of() : List.of(guardadas.split("\n"));
    }

    private ResumenLote resumen(long idImportacion) {
        return jdbc.sql("""
                SELECT COUNT(*) AS comprobantes, MIN(fecha_emision) AS desde, MAX(fecha_emision) AS hasta,
                       SUM(base_imponible) AS base_imponible, SUM(igv) AS igv, SUM(total) AS total
                FROM comprobantes WHERE id_importacion = ?""").param(idImportacion)
                .query((rs, i) -> new ResumenLote(rs.getLong("comprobantes"), rs.getString("desde"), rs.getString("hasta"),
                        rs.getBigDecimal("base_imponible"), rs.getBigDecimal("igv"), rs.getBigDecimal("total")))
                .single();
    }

    private List<VistaComprobante> filas(long idImportacion, int limite) {
        return jdbc.sql("SELECT " + COLUMNAS_SQL + " FROM comprobantes WHERE id_importacion = ? ORDER BY id LIMIT ?")
                .params(idImportacion, limite).query(A_VISTA).list();
    }

    @Override
    public Map<String, VistaImportacion> ultimas(long empresaId, String periodo) {
        Map<String, VistaImportacion> ultimas = new LinkedHashMap<>();
        for (String origen : EsquemaArchivo.ORIGENES.keySet())
            ultimaDe(empresaId, origen, periodo).flatMap(id -> detalle(empresaId, id, VISTA_PREVIA)).ifPresent(v -> ultimas.put(origen, v));
        return ultimas;
    }

    @Override
    public List<ResumenImportacion> historial(long empresaId) {
        return jdbc.sql("""
                SELECT i.id, i.origen, i.nombre_archivo, i.fecha, i.periodo, i.total_filas,
                       a.importacion_id IS NOT NULL AS archivo_disponible
                FROM importaciones i LEFT JOIN archivos_importacion a ON a.importacion_id = i.id
                WHERE i.empresa_id = ? ORDER BY i.id DESC
                """)
                .param(empresaId)
                .query((rs, i) -> new ResumenImportacion(rs.getLong("id"), rs.getString("origen"),
                        rs.getString("nombre_archivo"), rs.getString("fecha"), rs.getString("periodo"), rs.getInt("total_filas"),
                        rs.getBoolean("archivo_disponible")))
                .list();
    }

    @Override
    public List<Comprobante> comprobantes(long idImportacion) {
        return jdbc.sql("SELECT " + COLUMNAS_SQL + " FROM comprobantes WHERE id_importacion = ? ORDER BY id")
                .param(idImportacion).query(A_DOMINIO).list();
    }

    @Override
    public Optional<Long> ultimaDe(long empresaId, String origen) {
        return jdbc.sql("SELECT id FROM importaciones WHERE empresa_id = ? AND origen = ? ORDER BY id DESC LIMIT 1")
                .params(empresaId, origen)
                .query(Long.class).optional();
    }

    @Override
    public Optional<Long> ultimaDe(long empresaId, String origen, String periodo) {
        if (periodo == null) {
            return jdbc.sql("""
                    SELECT id FROM importaciones
                    WHERE empresa_id = ? AND origen = ? AND periodo IS NULL ORDER BY id DESC LIMIT 1
                    """).params(empresaId, origen).query(Long.class).optional();
        }
        return jdbc.sql("""
                SELECT id FROM importaciones
                WHERE empresa_id = ? AND origen = ? AND periodo = ? ORDER BY id DESC LIMIT 1
                """).params(empresaId, origen, periodo).query(Long.class).optional();
    }

    @Override
    public long cantidad(long empresaId) {
        return jdbc.sql("SELECT COUNT(*) FROM importaciones WHERE empresa_id = ?").param(empresaId).query(Long.class).single();
    }

    @Override
    public int borrarDeEmpresa(long empresaId) {
        return jdbc.sql("DELETE FROM importaciones WHERE empresa_id = ?").param(empresaId).update();
    }

    private static String ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
