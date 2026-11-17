package pe.yupana.dominio;

/**
 * Identidad de un comprobante: RUC + tipo + serie + número. Es un objeto de valor, por eso carga y conciliación
 * comparten una sola definición. El número es texto: «000001» no es «1».
 */
public record LlaveComprobante(String ruc, String tipo, String serie, String numero) {

    public static LlaveComprobante de(String ruc, String tipo, String serie, String numero) {
        return new LlaveComprobante(ruc.strip(), TipoComprobante.canonico(tipo), serie.strip().toUpperCase(), numero.strip());
    }

    public boolean estaCompleta() {
        return !ruc.isEmpty() && !tipo.isEmpty() && !serie.isEmpty() && !numero.isEmpty();
    }
}
