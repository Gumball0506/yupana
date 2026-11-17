#!/bin/sh
# Respaldo de la base PostgreSQL de Yupana: pg_dump comprimido, validado y con rotación.
#   7 respaldos diarios  +  4 semanales (el de cada domingo)
# POSIX sh: corre en la imagen postgres:16-alpine y en cualquier Linux.
#
# Variables (las mismas del cliente PostgreSQL):
#   PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE   conexión
#   DESTINO         carpeta de respaldos           (por defecto /respaldos)
#   MAX_DIARIOS     cuántos diarios conservar      (por defecto 7)
#   MAX_SEMANALES   cuántos semanales conservar    (por defecto 4)
#   COMANDO_DUMP    comando de volcado             (por defecto pg_dump; solo se cambia para probar)
#   COMANDO_LISTA   comando que valida el archivo  (por defecto pg_restore --list)
set -eu

DESTINO="${DESTINO:-/respaldos}"
MAX_DIARIOS="${MAX_DIARIOS:-7}"
MAX_SEMANALES="${MAX_SEMANALES:-4}"
COMANDO_DUMP="${COMANDO_DUMP:-pg_dump}"
COMANDO_LISTA="${COMANDO_LISTA:-pg_restore --list}"

# AHORA, DIA_SEMANA y SEMANA solo se definen en las pruebas (fechas falsas); en uso real salen de «date».
ahora="${AHORA:-$(date +%Y%m%d_%H%M%S)}"
dia_semana="${DIA_SEMANA:-$(date +%u)}"
semana="${SEMANA:-$(date +%G-W%V)}"
diarios="$DESTINO/diarios"
semanales="$DESTINO/semanales"
mkdir -p "$diarios" "$semanales"

archivo="$diarios/yupana_$ahora.dump"
parcial="$archivo.parcial"
trap 'rm -f "$parcial"' EXIT

# Formato «custom» (-Fc) ya viene comprimido (-Z 9) y permite restaurar tablas sueltas con pg_restore.
$COMANDO_DUMP --format=custom --compress=9 --no-owner --no-privileges --file="$parcial"

# Un respaldo que no se puede leer no es un respaldo: se valida antes de aceptarlo.
if [ ! -s "$parcial" ]; then
  echo "ERROR: el respaldo salió vacío; no se rotan los anteriores." >&2
  exit 1
fi
$COMANDO_LISTA "$parcial" > /dev/null || { echo "ERROR: el respaldo no se puede leer; no se rotan los anteriores." >&2; exit 1; }
mv "$parcial" "$archivo"
echo "Respaldo diario: $archivo ($(wc -c < "$archivo") bytes)"

# El domingo (7) se guarda además una copia semanal.
if [ "$dia_semana" = "7" ]; then
  semanal="$semanales/yupana_semana_$semana.dump"
  cp "$archivo" "$semanal"
  echo "Respaldo semanal: $semanal"
fi

# Rotación: se conservan los N más recientes de cada tipo (solo después de un respaldo bueno).
conservar() {
  carpeta="$1"; maximo="$2"
  # Los nombres llevan la fecha: ordenar por nombre (de más nuevo a más viejo) no depende de la hora del archivo.
  ls -1 "$carpeta"/yupana_*.dump 2>/dev/null | sort -r | tail -n +"$((maximo + 1))" | while read -r viejo; do
    rm -f "$viejo"
    echo "Rotado: $viejo"
  done
}
conservar "$diarios" "$MAX_DIARIOS"
conservar "$semanales" "$MAX_SEMANALES"
