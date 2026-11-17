# Yupana · MVP publicable (H6 + Entregable 4)

![CI](https://github.com/OWNER/REPO/actions/workflows/ci.yml/badge.svg)
<!-- Cambia OWNER/REPO por el repositorio de GitHub cuando lo subas; el flujo está en .github/workflows/ci.yml -->

Conciliación tributaria diaria para MYPE peruanas: carga compras y la propuesta RCE, concilia, muestra el «IGV bajo revisión» y permite confirmar u observar cada caso. Multiempresa para estudios contables, simulador SIRE y chat simulado.

**Todo es ficticio:** sin SUNAT, sin clave SOL, sin WhatsApp real. Ningún botón presenta nada a SUNAT. La cifra se rotula «IGV bajo revisión (demo)… no es crédito fiscal perdido ni recuperado».

Java 21 · Spring Boot 4.1.2 · JDBC · H2 (demo y pruebas) · PostgreSQL 16 (producción) · Apache POI.

## Correr en local (H2, sin Docker)
```
mvnw.cmd test                  pruebas (87; con -Ppostgres, 3 más contra PostgreSQL real)
mvnw.cmd spring-boot:run       http://localhost:8770  (landing → «Ingresar a la demo»)
```
- Usuarios de demo (se crean con la base vacía): `estudio.demo` y `mype.a`. Clave por defecto **solo en local**: `Yupana2026*` (o `YUPANA_SEED_CLAVE`).
- El servidor escucha solo en `127.0.0.1`. La base es `./data/yupana` (no se sube a Git); borra `data/` para empezar de cero.
- Perfil `docente`: `-Dspring-boot.run.profiles=docente` enciende `/h2-console`.

## Correr con Docker (PostgreSQL)
```
cp .env.example .env                       # y cambia POSTGRES_PASSWORD y YUPANA_SEED_CLAVE (mín. 12 caracteres)
docker compose up --build -d               # db + app + respaldo, sin HTTPS: http://localhost:8770
curl http://localhost:8770/actuator/health # {"status":"UP"}
docker compose --profile https up --build -d   # además Caddy con HTTPS automático para YUPANA_DOMINIO
```
Prueba completa (ingreso → carga → conciliación → casos → reinicio → persistencia):
```
CLAVE=<tu YUPANA_SEED_CLAVE> REINICIAR="docker compose restart app" sh scripts/humo.sh
```

### Variables de entorno
| Variable | Para qué | Obligatoria |
|---|---|---|
| `YUPANA_SEED_CLAVE` | Clave de los usuarios iniciales. En `produccion` el arranque **falla** si falta, es la de demostración o tiene menos de 12 caracteres | Sí (producción) |
| `YUPANA_DB_URL` · `YUPANA_DB_USUARIO` · `YUPANA_DB_CLAVE` | Conexión a PostgreSQL (el compose las arma desde `POSTGRES_*`) | Sí (producción) |
| `POSTGRES_DB` · `POSTGRES_USER` · `POSTGRES_PASSWORD` | Base de datos del compose | `POSTGRES_PASSWORD` sí |
| `YUPANA_SERVER_ADDRESS` | Dirección donde escucha. `127.0.0.1` en local, `0.0.0.0` en el contenedor | No |
| `YUPANA_COOKIE_SECURE` | Cookie de sesión solo por HTTPS. `true` con Caddy; `false` solo para probar sin HTTPS | No (`true` por defecto en producción) |
| `YUPANA_HSTS` | Cabecera Strict-Transport-Security | No |
| `YUPANA_DOMINIO` | Dominio público para Caddy (HTTPS) | Solo con `--profile https` |
| `NVD_API_KEY` | Clave gratuita del NVD para OWASP Dependency-Check (solo CI o escaneo local) | No |

Parámetros del negocio y la seguridad en `application.properties` (prefijo `yupana.`): `tolerancia`, `max-filas`, `reinicio-habilitado`, `login.max-intentos`, `login.bloqueo-minutos`, `limite.login-por-minuto`, `limite.carga-por-minuto`.

### Perfiles
| Perfil | Qué hace |
|---|---|
| (ninguno) | Demo local con H2 |
| `docente` | Enciende la consola de la base para mostrar las tablas |
| `produccion` | Endurecido: exige clave propia, PostgreSQL por entorno, cookie `Secure`, HSTS, «Reiniciar demo» apagado, sin datos de ejemplo, IP real tras el proxy |

## Respaldo y restauración
Un servicio del compose hace `pg_dump` cada 24 horas: 7 diarios y 4 semanales en `./respaldos`, validados antes de aceptarlos. Detalle, restauración y prueba de restauración en [`docs/RESPALDO_Y_RESTAURACION.md`](docs/RESPALDO_Y_RESTAURACION.md).

## Estructura
| Paquete | Qué hay |
|---|---|
| `dominio` | Reglas puras, sin Spring: `Conciliador`, `LlaveComprobante`, `Ruc` (módulo 11), `SimuladorSire`… |
| `carga` · `conciliacion` · `casos` · `empresas` · `acceso` · `sire` · `mensajes` · `uso` | Un módulo de aplicación por área, con su puerto de salida |
| `panel` | Consulta de solo lectura del panel del estudio; nadie depende de él |
| `persistencia` | Un adaptador JDBC por puerto: único lugar con SQL |
| `seguridad` | Claves PBKDF2, sesión, auditoría, bloqueo y límites |
| `web` | Controladores, manejo de errores, cabeceras de seguridad, registro de sesiones |
| `config` | Propiedades, exigencias de producción y siembra |

`ArquitecturaTest` falla si el dominio importa Spring o JDBC, si algo distinto de `persistencia` usa SQL, si la web toca la persistencia o si alguien depende de `panel`. Diagramas de flujo, arquitectura, despliegue, secuencia y ER en [`docs/DIAGRAMAS.md`](docs/DIAGRAMAS.md).

## Seguridad
- **Entrada:** extensión, firma ZIP del `.xlsx`, 2 MB, 5 000 filas y duplicados; nunca se evalúan fórmulas. **Salida:** CSV protegido contra fórmulas; valores ajenos recortados.
- **Acceso:** claves PBKDF2 (210 000 iteraciones); cookie HttpOnly, SameSite=Strict y `Secure` en producción; sesión nueva al ingresar; **bloqueo de 15 min tras 5 fallos** (por usuario y por IP, sin revelar qué usuarios existen); límite de peticiones por IP en ingreso y carga; cambio de clave que cierra las otras sesiones; la empresa sale siempre de la sesión del servidor.
- **Errores:** sin trazas hacia el cliente; 503 claro si la base no responde.
- **Cabeceras:** CSP `script-src 'self'`, `X-Frame-Options`, `nosniff`, HSTS en producción.
- **Auditoría** (tabla `auditoria`): cargas, conciliaciones, ingresos (exitosos, fallidos y bloqueados), claves cambiadas, empresas creadas, editadas y desactivadas, casos revisados. Nunca guarda contenido de archivos.
- **Dependencias:** `python scripts/escaneo_osv.py` (sin clave) y OWASP Dependency-Check con `-Pseguridad` (necesita `NVD_API_KEY`).
- **Repositorio:** `.gitignore` excluye `data/`, `.env`, logs y respaldos. Nada de secretos en el código.

## Pruebas
87 pruebas con H2 y 3 contra PostgreSQL real (`mvnw test -Ppostgres`, sin Docker). Incluyen: dominio, carga, conciliación, casos, multiempresa, acceso, bloqueo, límites, cambio de clave, empresas, arquitectura, producción, salud, 503 y el **mes de 100 compras** (`datos_demo/mes_100_compras/`, comparado comprobante por comprobante con una hoja de respuestas generada aparte). Cobertura (JaCoCo): 89.8 % de instrucciones; dominio, carga, conciliación y casos sobre 95 % (`target/site/jacoco/index.html`).

## Lo que sigue pendiente
- **Docker no se pudo ejecutar donde se desarrolló.** El Dockerfile y el compose están escritos y el YAML validado, pero la verificación real la hace el trabajo `docker` del CI (o `docker compose up --build` en un equipo con Docker).
- `pg_dump`/`pg_restore` reales y `scripts/probar_restauracion.sh`: se ejecutan en el CI; falta guardar su salida como evidencia.
- OWASP Dependency-Check necesita una clave del NVD; el escaneo con OSV sí se ejecutó (73 dependencias, sin hallazgos).
- Las pantallas nuevas (cambiar clave, editar y desactivar empresas) se probaron por API y con revisión de sintaxis; falta recorrerlas en un navegador.
- Un estudio ve todas las empresas: no existe el concepto de «varios estudios, cada uno con las suyas». Si se necesita, hay que modelarlo.
- Las sesiones viven en memoria del servidor: se pierden al reiniciar y no se comparten entre instancias.
- Repositorio remoto de GitHub, dominio y certificado: los pone el equipo (el badge de CI apunta a `OWNER/REPO`).
- Conexión real con SUNAT/SIRE y WhatsApp: fuera del MVP.
