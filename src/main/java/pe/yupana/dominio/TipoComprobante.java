package pe.yupana.dominio;

import java.util.Set;

/** Tipos de comprobante que reconoce el MVP: «01» y «Factura» son el mismo tipo. */
public enum TipoComprobante {
    FACTURA("FACTURA", "01"),
    BOLETA("BOLETA", "03"),
    NOTA_DE_CREDITO("NOTA DE CREDITO", "07", "NC"),
    NOTA_DE_DEBITO("NOTA DE DEBITO", "08", "ND");

    private final String nombre;
    private final Set<String> alias;

    TipoComprobante(String nombre, String... codigos) {
        this.nombre = nombre;
        this.alias = Set.of(codigos);
    }

    public String nombre() {
        return nombre;
    }

    /** Nombre canónico; si el texto no se reconoce se devuelve en mayúsculas y sin tildes. */
    public static String canonico(String crudo) {
        String texto = Textos.sinTildes(crudo).toUpperCase();
        for (TipoComprobante tipo : values())
            if (tipo.alias.contains(texto)) return tipo.nombre;
        return texto;
    }
}
