# Yupana · H6 — H5 completo sobre arquitectura por capas

Hace lo mismo que **H5**:
- panel multiempresa del estudio;
- login por rol;
- lección del RUC;
- simulador SIRE;
- chat simulado tipo WhatsApp;
- Confirmar / Observar, aviso del día y feedback.

La diferencia está en el código. Se construyó sobre el **refactor de H2 de nuestro compañero** (Clean Code, capas, patrones y controles ISO 27001) y se le sumaron los módulos de H3–H5 respetando esas mismas reglas.

Java 21 o 25 · Spring Boot 4 · H2 · Apache POI. Usa **datos ficticios**: sin clave SOL, sin SUNAT real y sin WhatsApp real.

## Ejecutar
```
mvnw.cmd test                        pruebas (52)
mvnw.cmd spring-boot:run             http://localhost:8770  (landing → «Ingresar a la demo»)
```
- Con JDK 21: agrega `-Djava.version=21`.
- Usuarios de demo (se crean con la base vacía): `estudio.demo` y `mype.a`. La clave sale de la variable `YUPANA_SEED_CLAVE`; si no está, se usa el valor por defecto de `application.properties`.
- Perfiles, iguales a los de su versión: `docente` enciende `/h2-console` y `produccion` deshabilita «Reiniciar demo».
- Por defecto el servidor solo escucha en `127.0.0.1`. Para un servidor real hay que cambiar `server.address` y ponerlo detrás de HTTPS.

## Estructura (las dependencias apuntan hacia el dominio)
| Paquete | Qué hay |
|---|---|
| `dominio` | Reglas puras, sin Spring: `Conciliador`, `LlaveComprobante`, `Ruc` (módulo 11), `AccionCaso`, `Rol`, `SimuladorSire`, `Csv`, `Fechas` |
| `carga` | Lectores CSV/XLSX, validación por cadena de reglas, `ServicioCargas` (por empresa) y el evento `DemoReiniciada` |
| `conciliacion` | `ServicioConciliacion`, exportador CSV (con revisión) y el evento `ConciliacionEjecutada` |
| `casos` | Confirmar / Observar, aviso del día (`RedactorAviso`) y herencia de revisiones al volver a conciliar |
| `empresas` · `acceso` | Alta de empresas; usuarios, ingreso y accesos de la MYPE |
| `sire` · `mensajes` | Simulador SIRE con tickets; chat simulado |
| `uso` | Eventos anónimos y feedback (Marketing 5.0) |
| `panel` | Consulta de solo lectura del panel del estudio. Junta varios módulos y nadie depende de él |
| `persistencia` | Un adaptador JDBC por puerto. Es la única capa con SQL, y todas sus consultas filtran por empresa |
| `seguridad` | `Claves` (PBKDF2), `Sesion` (rol + empresa activa), auditoría (`EventoAuditoria`, `RegistroAuditoria`) |
| `web` | Un controlador por área, `ConfiguracionWeb` (interceptor + `Sesion` inyectada), `ManejadorErrores`, cabeceras de seguridad |
| `config` | `PropiedadesYupana` y `Siembra` |

`ArquitecturaTest` hace fallar la compilación de pruebas si:
- el dominio importa Spring, JDBC u otra capa;
- algún módulo de aplicación usa SQL o la web;
- la web toca la persistencia;
- alguien depende del panel.

## Patrones de diseño
| Patrón | Dónde |
|---|---|
| Strategy | `ComparadorDeValores`, `LectorArchivo` |
| Factory Method | `FabricaLectores` |
| Cadena de reglas | `ReglaDeFila` |
| Value Object | `LlaveComprobante`, `Ruc` |
| Repository con puertos y adaptadores, Service Layer | un puerto por módulo y su adaptador en `persistencia` |
| Observer | `EventoAuditoria` → `RegistroAuditoria`; `ConciliacionEjecutada` → los casos heredan revisiones; `DemoReiniciada` → el chat y el SIRE se limpian |
| Read model (consulta separada) | `PanelEstudio` |

## Seguridad (lo de la versión E2 más lo de H5)
- **Entrada:** extensión, firma ZIP del `.xlsx`, 2 MB, 5 000 filas y duplicados; nunca se evalúan fórmulas. **Salida:** CSV protegido contra fórmulas y valores ajenos recortados.
- **Errores:** sin trazas hacia el cliente, con código de soporte. **CSP:** `script-src 'self'`, sin scripts dentro del HTML (cada pantalla tiene su `.js`).
- **Acceso:**
  - claves con PBKDF2 (210 000 iteraciones, sal aleatoria);
  - cookie HttpOnly y SameSite=Strict, con un identificador de sesión nuevo al ingresar;
  - mismo mensaje y mismo tiempo de respuesta para un usuario inexistente que para una clave mala;
  - la empresa se toma de la sesión del servidor, nunca del navegador.
- **Auditoría** (tabla `auditoria`): cargas, conciliaciones, ingresos exitosos y fallidos, accesos denegados, empresas y accesos creados, casos revisados e importaciones del SIRE. Nunca guarda contenido de archivos.

## Pruebas (52)
| Origen | Pruebas |
|---|---|
| Del compañero, adaptadas a multiempresa | 39: dominio, carga, conciliación, CSV, API segura y arquitectura (+1 regla nueva) |
| Nuevas | `ReglasNuevasTest` (RUC, acción, SIRE, CSV), `CasosTest` (T13/T14), `MultiempresaTest` (aislamiento, panel, SIRE, chat), `AccesoTest` (401/403/409, auditoría de ingreso) |

## Diferencias con H5
| | H5 | H6 |
|---|---|---|
| Funciones y pantallas | Todas | Las mismas |
| Código | 6 clases grandes | 13 paquetes con responsabilidades separadas |
| Pruebas | 19 | 52 |
| Auditoría ISO | No | Sí |
| Política de seguridad (CSP) | No | Sí |

## Pendiente antes de publicar en internet
- Límite de intentos de login.
- HTTPS.
- PostgreSQL con respaldo.
- Escaneo de dependencias.
- Editar o borrar empresas y cambiar claves.
