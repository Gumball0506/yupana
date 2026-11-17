package pe.yupana.dominio;

/** Un campo que difiere entre compras y propuesta RCE, con los dos valores ya formateados. */
public record Diferencia(CampoCotejable campo, String valorCompras, String valorRce) {

    String descripcion() {
        return campo.etiqueta() + " (compras " + valorCompras + " · RCE " + valorRce + ")";
    }
}
