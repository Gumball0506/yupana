package pe.yupana.uso;

/** Puerto de salida de los eventos anónimos de uso. */
public interface RepositorioEventos {

    void registrar(EventoDeUso evento);

    ResumenEventos resumen();
}
