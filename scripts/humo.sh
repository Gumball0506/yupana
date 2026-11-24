#!/bin/sh
# Prueba de humo de punta a punta contra una instancia en marcha (local o Docker):
#   ingresar → abrir empresa de prueba → cargar compras + RCE → conciliar → observar y confirmar un caso
#   → (opcional) reiniciar la aplicación → volver a ingresar y comprobar que el estado de los casos sigue guardado.
#
# Variables:
#   BASE_URL    dirección de la aplicación          (por defecto http://localhost:8770)
#   CLAVE       clave del usuario de prueba        (la de YUPANA_SEED_CLAVE en base nueva)
#   USUARIO     cuenta de estudio                  (por defecto estudio en producción)
#   PERIODO     periodo de las cargas              (por defecto 2026-10)
#   EMPRESA_ID  empresa de prueba existente        (si falta, se crea una en la base de prueba)
#   REINICIAR   comando que reinicia la aplicación  (ej. "docker compose restart app"); si falta, se omite esa parte
#   DATOS       carpeta con los CSV de ejemplo      (por defecto datos_demo)
# Necesita curl y python (python3 o python) (solo para leer JSON).
set -eu

BASE_URL="${BASE_URL:-http://localhost:8770}"
CLAVE="${CLAVE:?Define CLAVE para el usuario de prueba}"
USUARIO="${USUARIO:-estudio}"
PERIODO="${PERIODO:-2026-10}"
EMPRESA_ID="${EMPRESA_ID:-}"
DATOS="${DATOS:-datos_demo}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
jar="$tmp/cookies.txt"

falla() { echo "FALLA: $1" >&2; exit 1; }
PY=""
for candidato in python3 python; do
  if "$candidato" -c "" > /dev/null 2>&1; then PY="$candidato"; break; fi
done
[ -n "$PY" ] || falla "hace falta python3 (solo para leer JSON)"
json() { "$PY" -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

esperar_salud() {
  intentos=0
  until curl -fs "$BASE_URL/actuator/health" | grep -q '"status":"UP"'; do
    intentos=$((intentos + 1))
    [ "$intentos" -lt 60 ] || falla "la aplicación no llegó a UP en 120 s"
    sleep 2
  done
  echo "OK  /actuator/health = UP"
}

ingresar() {
  rm -f "$jar"
  credenciales=$("$PY" -c 'import json,sys; print(json.dumps(dict(usuario=sys.argv[1], clave=sys.argv[2])))' "$USUARIO" "$CLAVE")
  codigo=$(curl -s -o "$tmp/login.json" -w '%{http_code}' -c "$jar" -H 'Content-Type: application/json' \
    --data "$credenciales" "$BASE_URL/api/login")
  [ "$codigo" = "200" ] || falla "ingreso respondió $codigo (¿la clave es la de YUPANA_SEED_CLAVE y la cookie Secure está desactivada en local?)"
  echo "OK  ingreso como $USUARIO"
}

api() { curl -s -b "$jar" -c "$jar" "$@"; }

esperar_salud
ingresar

if [ -z "$EMPRESA_ID" ]; then
  api -f -H 'Content-Type: application/json' \
    -d '{"ruc":"20999910018","razon_social":"Empresa de prueba de humo"}' "$BASE_URL/api/empresas" > "$tmp/empresa.json" \
    || falla "no se pudo crear la empresa de prueba; para una base de pruebas ya preparada, usa EMPRESA_ID"
  EMPRESA_ID=$(json "d['id']" < "$tmp/empresa.json")
fi
activar() { api -f -X POST "$BASE_URL/api/empresas/$EMPRESA_ID/activar" > /dev/null; }
activar || falla "no se pudo abrir la empresa de prueba"
echo "OK  empresa de prueba abierta"

api -f -F origen=compras -F "periodo=$PERIODO" -F "archivo=@$DATOS/yupana_compras_demo.csv" "$BASE_URL/api/cargas" > "$tmp/compras.json" || falla "carga de compras"
api -f -F origen=rce -F "periodo=$PERIODO" -F "archivo=@$DATOS/yupana_rce_demo.csv" "$BASE_URL/api/cargas" > /dev/null || falla "carga de la propuesta RCE"
echo "OK  compras y propuesta RCE cargadas"

api -f -X POST "$BASE_URL/api/conciliar?periodo=$PERIODO" > "$tmp/conciliacion.json" || falla "conciliación"
resumen=$(json "'%d/%d/%d' % (d['total'], d['conciliados'], d['con_diferencia'])" < "$tmp/conciliacion.json")
[ "$resumen" = "10/7/3" ] || falla "se esperaba 10/7/3 (total/cuadran/no cuadran) y llegó $resumen"
echo "OK  conciliación 10/7/3"

caso() { json "[r['id'] for r in d['resultados'] if r['numero']=='$1'][0]" < "$tmp/conciliacion.json"; }
c3=$(caso 000003); c9=$(caso 000009)
api -f -X POST -H 'Content-Type: application/json' -d '{"accion":"observar","nota":"prueba de humo"}' "$BASE_URL/api/casos/$c3/estado" > /dev/null || falla "observar caso"
api -f -X POST -H 'Content-Type: application/json' -d '{"accion":"confirmar"}' "$BASE_URL/api/casos/$c9/estado" > /dev/null || falla "confirmar caso"
echo "OK  caso 000003 observado y caso 000009 confirmado"

estado() { api -f "$BASE_URL/api/conciliaciones/ultima?periodo=$PERIODO" | json "[ (r['revision'] or {}).get('accion') for r in d['resultados'] if r['numero']=='$1'][0]"; }
[ "$(estado 000003)" = "OBSERVADO" ] || falla "000003 no quedó OBSERVADO"
[ "$(estado 000009)" = "CONFIRMADO" ] || falla "000009 no quedó CONFIRMADO"

if [ -n "${REINICIAR:-}" ]; then
  echo "..  reiniciando la aplicación: $REINICIAR"
  sh -c "$REINICIAR" > /dev/null
  sleep 3
  esperar_salud
  ingresar
  activar || falla "no se pudo abrir la empresa tras reiniciar"
  compra_id=$(json "d['id']" < "$tmp/compras.json")
  api -f "$BASE_URL/api/cargas/$compra_id/archivo" > "$tmp/compras-original.csv" || falla "descargar original tras reiniciar"
  cmp -s "$DATOS/yupana_compras_demo.csv" "$tmp/compras-original.csv" || falla "el original descargado tiene bytes distintos"
  [ "$(estado 000003)" = "OBSERVADO" ] || falla "tras reiniciar, 000003 perdió su estado"
  [ "$(estado 000009)" = "CONFIRMADO" ] || falla "tras reiniciar, 000009 perdió su estado"
  echo "OK  tras reiniciar, el estado de los casos sigue guardado"
fi
echo "PRUEBA DE HUMO COMPLETA"
