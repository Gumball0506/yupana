# Respaldo y restauración de la base (PostgreSQL)

## Qué se respalda y cada cuánto
- La base completa de Yupana (empresas, usuarios, cargas, conciliaciones, casos, auditoría). Formato `pg_dump -Fc` (ya comprimido).
- El servicio `respaldo` de `docker-compose.yml` corre `scripts/respaldo.sh` al arrancar y luego cada 24 horas.
- Se guardan en `./respaldos/`: **7 diarios** (`diarios/`) y **4 semanales** (`semanales/`, el de cada domingo). Los más viejos se borran solo después de un respaldo nuevo y válido.
- Un respaldo se acepta solo si no está vacío y `pg_restore --list` lo puede leer. Si falla, **no se borra ninguno de los anteriores**.

> El volumen `pgdata` y `./respaldos` viven en el mismo servidor. Eso protege de un borrado o de un error de la aplicación, no de perder el servidor.
> Copia `./respaldos` a otro lugar (otro servidor, almacenamiento en la nube) para tener un respaldo fuera de él.

## Respaldo a mano
```
docker compose exec respaldo sh /scripts/respaldo.sh
ls respaldos/diarios
```

## Restaurar
**Para probar un respaldo sin tocar la base real** (recomendado, repetir cada cierto tiempo):
```
docker compose exec respaldo sh /scripts/probar_restauracion.sh
```
Toma un respaldo nuevo, lo restaura en la base temporal `yupana_restauracion`, compara el número de filas de las 14 tablas con las de la base real y borra la temporal. Termina con `RESTAURACIÓN VERIFICADA` o con la lista de tablas que no coinciden.

**Para recuperar la base real tras un desastre:**
```
docker compose stop app                                   # nadie escribe mientras se restaura
docker compose exec respaldo sh /scripts/restaurar.sh /respaldos/diarios/yupana_AAAAMMDD_HHMMSS.dump
docker compose start app
```
La restauración usa `--clean --if-exists --single-transaction`: o se restaura todo o no cambia nada.

## Qué está verificado y qué no
| Pieza | Estado |
|---|---|
| Rotación (7 + 4), validación y rechazo de un respaldo vacío | **Verificado**: `sh scripts/probar_rotacion.sh` (usa un `pg_dump` falso y fechas falsas; corre en cualquier Linux o Git Bash y en la integración continua) |
| El esquema y la aplicación sobre PostgreSQL real | **Verificado**: `mvnw test -Ppostgres` |
| `pg_dump` y `pg_restore` reales, y `probar_restauracion.sh` | **No se pudo ejecutar** donde se desarrolló (sin Docker ni cliente PostgreSQL). Hay que correr una vez `probar_restauracion.sh` en el servidor y guardar su salida como evidencia |
