package pe.yupana.empresas;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Empresa cliente del estudio contable. El teléfono (solo dígitos con código de país) sirve para el enlace wa.me. */
public record Empresa(long id, String ruc, @JsonProperty("razon_social") String razonSocial, String telefono, boolean activa) {
}
