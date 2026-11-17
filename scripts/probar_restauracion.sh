#!/bin/sh
# Prueba de restauración: demuestra que el último respaldo SIRVE. No toca la base real.
#   1) toma un respaldo nuevo, 2) lo restaura en la base temporal «yupana_restauracion»,
#   3) compara el número de filas de cada tabla con el de la base real, 4) borra la temporal.
# Termina con código 0 solo si todas las tablas coinciden.
# Variables: PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE DESTINO
set -eu

PGDATABASE="${PGDATABASE:?Define PGDATABASE}"
TEMPORAL="yupana_restauracion"
aqui="$(dirname "$0")"

sh "$aqui/respaldo.sh"
ultimo="$(ls -1 "${DESTINO:-/respaldos}"/diarios/yupana_*.dump | sort -r | head -n 1)"

psql -v ON_ERROR_STOP=1 -d postgres -c "DROP DATABASE IF EXISTS $TEMPORAL" -c "CREATE DATABASE $TEMPORAL"
sh "$aqui/restaurar.sh" "$ultimo" "$TEMPORAL"

tablas="empresas usuarios importaciones comprobantes conciliaciones resultados diferencias acciones_caso avisos mensajes sire_tickets feedback eventos auditoria"
fallos=0
for t in $tablas; do
  real=$(psql -At -d "$PGDATABASE" -c "SELECT COUNT(*) FROM $t")
  copia=$(psql -At -d "$TEMPORAL" -c "SELECT COUNT(*) FROM $t")
  if [ "$real" = "$copia" ]; then
    echo "OK    $t: $real filas"
  else
    echo "FALLA $t: real=$real restaurada=$copia"
    fallos=$((fallos + 1))
  fi
done
psql -v ON_ERROR_STOP=1 -d postgres -c "DROP DATABASE $TEMPORAL"
[ "$fallos" -eq 0 ] && echo "RESTAURACIÓN VERIFICADA: $ultimo" || { echo "$fallos tabla(s) no coinciden" >&2; exit 1; }
