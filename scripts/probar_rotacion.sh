#!/bin/sh
# Prueba de la lógica de respaldo.sh SIN PostgreSQL: usa un «pg_dump» falso y fechas falsas.
# Comprueba: nombres, validación, copia semanal los domingos y rotación (7 diarios + 4 semanales).
# Corre en cualquier Linux o Git Bash:  sh scripts/probar_rotacion.sh
set -eu

aqui="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Volcado falso: escribe algo en el archivo que se le pide
cat > "$tmp/dump_falso.sh" <<'FALSO'
#!/bin/sh
for a in "$@"; do case "$a" in --file=*) archivo="${a#--file=}";; esac; done
echo "contenido del respaldo" > "$archivo"
FALSO
# «pg_restore --list» falso: acepta cualquier archivo que exista y no esté vacío
cat > "$tmp/lista_falsa.sh" <<'FALSO'
#!/bin/sh
[ -s "$1" ]
FALSO
chmod +x "$tmp/dump_falso.sh" "$tmp/lista_falsa.sh"

export DESTINO="$tmp/respaldos" COMANDO_DUMP="$tmp/dump_falso.sh" COMANDO_LISTA="$tmp/lista_falsa.sh"
falla() { echo "FALLA: $1" >&2; exit 1; }

# 12 respaldos diarios (días 1 a 12); los días 7 y 14... caen en domingo: simulamos domingos en los días 7 y 12
for dia in 01 02 03 04 05 06 07 08 09 10 11 12; do
  semana_dom="3"; if [ "$dia" = "07" ] || [ "$dia" = "12" ]; then semana_dom="7"; fi
  AHORA="202611${dia}_020000" DIA_SEMANA="$semana_dom" SEMANA="2026-W$dia" sh "$aqui/respaldo.sh" > /dev/null
done

diarios=$(ls -1 "$DESTINO/diarios" | wc -l | tr -d ' ')
[ "$diarios" = "7" ] || falla "debían quedar 7 diarios y hay $diarios"
mas_viejo=$(ls -1 "$DESTINO/diarios" | sort | head -n 1)
[ "$mas_viejo" = "yupana_20261106_020000.dump" ] || falla "el diario más viejo debía ser el del día 06 y es $mas_viejo"
semanales=$(ls -1 "$DESTINO/semanales" | wc -l | tr -d ' ')
[ "$semanales" = "2" ] || falla "debían existir 2 semanales (domingos 07 y 12) y hay $semanales"

# 6 domingos más: solo deben quedar los 4 semanales más recientes
for n in 13 14 15 16 17 18; do
  AHORA="202611${n}_020000" DIA_SEMANA="7" SEMANA="2026-W$n" sh "$aqui/respaldo.sh" > /dev/null
done
semanales=$(ls -1 "$DESTINO/semanales" | wc -l | tr -d ' ')
[ "$semanales" = "4" ] || falla "debían quedar 4 semanales y hay $semanales"
[ -f "$DESTINO/semanales/yupana_semana_2026-W18.dump" ] || falla "falta el semanal más reciente"
[ ! -f "$DESTINO/semanales/yupana_semana_2026-W07.dump" ] || falla "el semanal más viejo debía rotarse"

# Un volcado que sale vacío NO debe rotar nada ni dejar archivo
antes=$(ls -1 "$DESTINO/diarios" | sort | tr '\n' ' ')
cat > "$tmp/dump_vacio.sh" <<'FALSO'
#!/bin/sh
for a in "$@"; do case "$a" in --file=*) : > "${a#--file=}";; esac; done
FALSO
chmod +x "$tmp/dump_vacio.sh"
if AHORA="20261119_020000" COMANDO_DUMP="$tmp/dump_vacio.sh" sh "$aqui/respaldo.sh" > /dev/null 2>&1; then falla "un respaldo vacío debía fallar"; fi
despues=$(ls -1 "$DESTINO/diarios" | sort | tr '\n' ' ')
[ "$antes" = "$despues" ] || falla "un respaldo fallido no debe cambiar los diarios existentes"
ls "$DESTINO/diarios" | grep -q parcial && falla "quedó un archivo parcial"

echo "ROTACION OK: 7 diarios, 4 semanales, validacion y respaldo vacio rechazado"
