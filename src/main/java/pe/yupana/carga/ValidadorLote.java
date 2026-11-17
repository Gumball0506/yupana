package pe.yupana.carga;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import pe.yupana.dominio.Comprobante;
import pe.yupana.dominio.LlaveComprobante;
import pe.yupana.dominio.Textos;

/** Valida un archivo completo: encabezado, cada fila (cadena de reglas) y duplicados. Todo o nada. */
@Component
public class ValidadorLote {

    private static final int ERRORES_VISIBLES = 20;
    private static final int FILAS_VISTA_RECHAZO = 20;
    private static final int LARGO_MAXIMO_CELDA_EN_VISTA = 100;

    private final List<ReglaDeFila> reglas = ReglasDeFila.enOrden();

    public Lote validar(List<Fila> filasCrudas) {
        List<Fila> filas = filasCrudas.stream().filter(f -> !f.estaVacia()).toList();
        if (filas.isEmpty()) throw new ErrorCarga("El archivo está vacío.");

        List<String> original = filas.getFirst().celdas().stream().map(String::strip).toList();
        List<String> encabezado = original.stream().map(EsquemaArchivo::normalizarEncabezado).toList();
        exigirColumnas(original, encabezado);
        if (filas.size() == 1) throw new ErrorCarga("El archivo solo tiene encabezados: no hay comprobantes que importar.");

        Hallazgos hallazgos = new Hallazgos();
        columnasIgnoradas(original, encabezado).ifPresent(hallazgos::advertir);

        List<Comprobante> comprobantes = new ArrayList<>();
        Set<Integer> filasConError = new LinkedHashSet<>();
        Map<LlaveComprobante, Integer> llaves = new HashMap<>();
        for (Fila fila : filas.subList(1, filas.size())) {
            int erroresAntes = hallazgos.cantidadErrores();
            FilaEnRevision revision = new FilaEnRevision(fila, encabezado);
            reglas.forEach(regla -> regla.aplicar(revision, hallazgos));
            if (revision.tieneTodosLosImportes()) comprobantes.add(revision.comoComprobante());
            marcarDuplicado(revision, llaves, hallazgos);
            if (hallazgos.cantidadErrores() > erroresAntes) filasConError.add(fila.n());
        }
        if (hallazgos.hayErrores()) throw rechazo(hallazgos, filas, filasConError, original, encabezado);
        return new Lote(comprobantes, hallazgos.advertencias());
    }

    private void exigirColumnas(List<String> original, List<String> encabezado) {
        List<String> errores = new ArrayList<>();
        for (String columna : EsquemaArchivo.COLUMNAS)
            if (!encabezado.contains(columna)) errores.add("Falta la columna " + (columna.equals("igv") ? "IGV" : columna) + ".");
        if (errores.isEmpty()) return;
        errores.add("El MVP usa el formato normalizado del DICCIONARIO (8 columnas). "
                + "La exportación oficial de SUNAT aún no tiene adaptador.");
        throw new ErrorCarga(errores, List.of(), new VistaRechazo(acortar(original), acortar(encabezado), List.of()));
    }

    private Optional<String> columnasIgnoradas(List<String> original, List<String> encabezado) {
        List<String> ignoradas = new ArrayList<>();
        for (int i = 0; i < encabezado.size(); i++)
            if (!encabezado.get(i).isEmpty() && !EsquemaArchivo.COLUMNAS.contains(encabezado.get(i)))
                ignoradas.add(original.get(i));
        return ignoradas.isEmpty() ? Optional.empty()
                : Optional.of("Se ignoraron columnas que el MVP no usa: " + String.join(", ", ignoradas) + ".");
    }

    private void marcarDuplicado(FilaEnRevision fila, Map<LlaveComprobante, Integer> vistas, Hallazgos hallazgos) {
        LlaveComprobante llave = LlaveComprobante.de(fila.valor("ruc_proveedor"), fila.valor("tipo_comprobante"),
                fila.valor("serie"), fila.valor("numero"));
        Integer filaPrevia = vistas.putIfAbsent(llave, fila.n());
        if (llave.estaCompleta() && filaPrevia != null)
            hallazgos.errorDeFila(fila.n(), "Fila " + fila.n() + ": comprobante duplicado " + fila.valor("serie") + "-"
                    + fila.valor("numero") + " (ya está en la fila " + filaPrevia + ").");
    }

    private ErrorCarga rechazo(Hallazgos hallazgos, List<Fila> filas, Set<Integer> filasConError,
                               List<String> original, List<String> encabezado) {
        List<String> visibles = new ArrayList<>(hallazgos.errores().subList(0, Math.min(ERRORES_VISIBLES, hallazgos.errores().size())));
        if (hallazgos.errores().size() > ERRORES_VISIBLES)
            visibles.add("… y " + (hallazgos.errores().size() - ERRORES_VISIBLES) + " errores más.");
        List<VistaRechazo.FilaRechazada> vistaFilas = filas.stream().filter(f -> filasConError.contains(f.n()))
                .limit(FILAS_VISTA_RECHAZO).map(f -> new VistaRechazo.FilaRechazada(f.n(), acortar(f.celdas()))).toList();
        return new ErrorCarga(visibles, hallazgos.celdas(), new VistaRechazo(acortar(original), acortar(encabezado), vistaFilas));
    }

    /** Lo que el usuario envió se devuelve recortado: nunca se refleja un texto de tamaño arbitrario. */
    private static List<String> acortar(List<String> textos) {
        return textos.stream().map(t -> Textos.acortar(t, LARGO_MAXIMO_CELDA_EN_VISTA)).toList();
    }
}
