package pe.yupana.carga;

import java.util.List;

import pe.yupana.dominio.Comprobante;

/** Resultado de validar un archivo: comprobantes listos para guardar y avisos que no bloquean. */
public record Lote(List<Comprobante> comprobantes, List<String> advertencias) {
}
