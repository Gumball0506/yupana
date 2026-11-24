# Cómo publicar Yupana en internet

Qué necesitas en cualquier caso: un servidor o servicio que corra contenedores Docker, un dominio (para HTTPS) y el código en un repositorio de GitHub. **Todo es ficticio**: no subas datos reales de contribuyentes ni claves SOL.

## Opción recomendada: un VPS con Docker Compose (el compose ya está listo)
Sirve cualquier VPS Linux (Ubuntu 22.04 o 24.04) con **2 GB de RAM como mínimo** (la aplicación Java y PostgreSQL juntas). Revisa el precio vigente del proveedor que elijas antes de contratar.

1. **Servidor:** crea el VPS, entra por SSH con una llave y deja abiertos solo los puertos 22, 80 y 443 (`ufw allow 22,80,443/tcp && ufw enable`).
2. **Docker:** `curl -fsSL https://get.docker.com | sh`
3. **Código:** sube el proyecto a un repositorio privado de GitHub y haz `git clone` en el servidor.
4. **Dominio:** crea un registro DNS **A** (por ejemplo `yupana.tudominio.com`) que apunte a la IP del servidor y espera a que propague.
5. **Variables:** `cp .env.example .env` y edita:
   - `POSTGRES_PASSWORD` y `YUPANA_SEED_CLAVE` con valores propios y largos (la clave de la semilla, mínimo 12 caracteres),
   - `YUPANA_DOMINIO=yupana.tudominio.com`,
   - `YUPANA_COOKIE_SECURE=true` y `YUPANA_HSTS=true`.
6. **Arrancar con HTTPS:** `docker compose --profile https up --build -d` (Caddy pide el certificado solo).
7. **Verificar:**
   - `curl https://yupana.tudominio.com/actuator/health` → `{"status":"UP"}`
   - `CLAVE=<tu YUPANA_SEED_CLAVE> BASE_URL=https://yupana.tudominio.com REINICIAR="docker compose restart app" sh scripts/humo.sh`
   - `docker compose exec respaldo sh /scripts/probar_restauracion.sh` (guarda la salida como evidencia del respaldo).
8. **Actualizar:** `git pull && docker compose --profile https up --build -d`.
9. **Respaldos:** copia la carpeta `./respaldos` a otro lugar (otro servidor o almacenamiento en la nube): si pierdes el VPS, pierdes también esos archivos.

Si no tienes dominio todavía: `YUPANA_COOKIE_SECURE=false`, `YUPANA_HSTS=false`, `docker compose up --build -d` y entra por `http://IP:8770` desde el mismo servidor (el compose solo abre ese puerto en 127.0.0.1; para verlo desde fuera necesitas HTTPS con dominio). Sin HTTPS no es apto para más que una prueba.

## Si ya tienen un VPS con Coolify
Coolify despliega desde GitHub y pone su propio proxy con HTTPS, así que **no se usa el servicio `caddy`**:
1. Recurso nuevo desde el repositorio, tipo *Docker Compose*, archivo `docker-compose.yml`.
2. Asigna el dominio al servicio `app` (puerto interno **8770**).
3. Define las variables de entorno en Coolify (las mismas del paso 5; `YUPANA_COOKIE_SECURE=true`, `YUPANA_HSTS=true`). Marca `POSTGRES_PASSWORD` y `YUPANA_SEED_CLAVE` como secretas.
4. Despliega y revisa `https://tu-dominio/actuator/health`.
Los nombres exactos de los menús pueden variar según la versión de Coolify; confírmalo en su pantalla.

## Alternativa sin administrar un servidor (Railway, Render, Fly.io)
Sirven para publicar rápido: despliegan el `Dockerfile` y ofrecen PostgreSQL administrado. Hay que adaptar dos cosas: definir `YUPANA_DB_URL` (formato `jdbc:postgresql://host:5432/base`), `YUPANA_DB_USUARIO` y `YUPANA_DB_CLAVE` con los datos de su base, y **no usar** los servicios `db`, `respaldo` ni `caddy` del compose (los respaldos los da el servicio). Los planes gratuitos suelen apagar la aplicación por inactividad, lo que no conviene en una exposición: revisa los límites y precios vigentes.

## Operación con datos reales en Render

- Usa una base PostgreSQL con retención adecuada y respaldos. La base gratuita de Render vence a los 30 días. Respalda la base activa antes de cambiar de instancia y conserva también la tabla `archivos_importacion`.
- En una base nueva, ingresa como `estudio` con la clave inicial `YUPANA_SEED_CLAVE`. En una instalación con empresas existentes, usa la cuenta de estudio que ya tienes y da de alta la empresa real con su RUC.
- Elige el periodo tributario antes de subir compras y RCE. En el historial puedes ver las cargas válidas por mes y descargar el CSV o Excel original de cada carga nueva. Las cargas previas al cambio aparecen sin periodo y conservan sus filas procesadas.
- Usa las plantillas de Yupana para preparar el RCE si parte de una exportación con columnas diferentes. La carga manual funciona sin credenciales de SUNAT. Ejecuta `scripts/humo.sh` sobre una base de pruebas, ya que escribe archivos de ejemplo y revisiones.

## Antes de la exposición
- Despliega con días de anticipación: el DNS y el certificado pueden tardar.
- Ten un plan B local (`mvnw spring-boot:run` o `docker compose up`) y capturas.
- Cambia las claves de demostración y no dejes `Yupana2026*` en ningún servidor.
