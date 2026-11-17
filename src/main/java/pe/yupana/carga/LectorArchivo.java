package pe.yupana.carga;

import java.util.List;

/** Estrategia de lectura: cada formato de archivo sabe convertir sus bytes en filas de texto. */
public interface LectorArchivo {

    List<Fila> leer(byte[] contenido);
}
