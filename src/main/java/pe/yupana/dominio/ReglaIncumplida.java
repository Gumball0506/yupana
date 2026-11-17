package pe.yupana.dominio;

/** Un dato no cumple una regla del negocio. El mensaje está pensado para mostrarse tal cual al usuario. */
public class ReglaIncumplida extends RuntimeException {

    public ReglaIncumplida(String mensaje) {
        super(mensaje);
    }
}
