#!/bin/sh
# Restaura un respaldo de Yupana en una base de datos. Uso:
#   scripts/restaurar.sh /respaldos/diarios/yupana_20261117_020000.dump [base_destino]
#
# Si no se indica base_destino se usa PGDATABASE (¡sobrescribe lo que haya!). Para PROBAR un respaldo sin tocar la base real,
# restaura en otra base, por ejemplo yupana_restauracion (ver scripts/probar_restauracion.sh).
# Variables: PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
set -eu

archivo="${1:?Indica el archivo de respaldo (.dump)}"
base="${2:-${PGDATABASE:?Indica la base de destino o define PGDATABASE}}"
[ -s "$archivo" ] || { echo "No existe o está vacío: $archivo" >&2; exit 1; }

echo "Restaurando $archivo en la base «$base» ..."
# --clean --if-exists reemplaza las tablas existentes; --single-transaction: o se restaura todo o nada.
pg_restore --clean --if-exists --no-owner --no-privileges --single-transaction --dbname="$base" "$archivo"
echo "Restauración terminada."
