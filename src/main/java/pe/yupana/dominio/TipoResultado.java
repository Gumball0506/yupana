package pe.yupana.dominio;

/** Resultado de comparar un comprobante entre las dos fuentes. */
public enum TipoResultado {
    CONCILIADO, DIFERENCIA, SOLO_COMPRAS, SOLO_RCE;

    public boolean estaObservado() {
        return this != CONCILIADO;
    }
}
