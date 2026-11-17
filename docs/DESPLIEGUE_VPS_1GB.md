# Yupana en un VPS gratuito de 1 GB

Vale para la máquina gratuita de Google Cloud (e2-micro) y para la «micro» de Oracle Cloud. Los límites y condiciones de cada oferta cambian: confírmalos en su página oficial antes de crear la cuenta (ver `docs/DESPLIEGUE.md`).
Vercel no sirve para esto: corre páginas y funciones, no un servidor Java con base de datos que se queda encendido.

## 1. Crear la máquina
**Google Cloud (e2-micro):** región de EE. UU. con la oferta gratuita (Oregón, Iowa o Carolina del Sur), Ubuntu 24.04, disco estándar de 30 GB como máximo. Marca «Permitir tráfico HTTP» y «HTTPS». Pide tarjeta.
**Oracle Cloud (VM.Standard.E2.1.Micro o la ARM, si hay capacidad):** Ubuntu 24.04. En la red de la máquina (lista de seguridad) abre los puertos 80 y 443 de entrada. Las imágenes Ubuntu de Oracle traen reglas de `iptables` que bloquean todo menos SSH: si desde fuera no responde, permite 80 y 443 también ahí (`sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT`, igual con 443, y guárdalo con `sudo netfilter-persistent save`).

## 2. Un dominio gratis para HTTPS
Caddy necesita un nombre que apunte a la IP del servidor. Si no tienes dominio, un servicio de subdominios gratuitos (por ejemplo DuckDNS) sirve: crea `algo.duckdns.org` con la IP pública de la máquina. Confirma en su página que sigue siendo gratuito.

## 3. Entrar y bajar el código
```
ssh usuario@IP_DEL_SERVIDOR
git clone https://github.com/TU_USUARIO/TU_REPOSITORIO.git yupana && cd yupana
```

## 4. Preparar el servidor (swap, Docker, firewall)
```
sudo sh scripts/preparar_vps.sh
```
Cierra y vuelve a abrir la sesión SSH para que tu usuario use Docker sin `sudo` (o antepón `sudo` a los comandos de abajo).

## 5. Variables
```
cp .env.example .env
nano .env
```
- `POSTGRES_PASSWORD` y `YUPANA_SEED_CLAVE`: valores propios y largos (la clave, mínimo 12 caracteres).
- `YUPANA_DOMINIO=algo.duckdns.org`
- `YUPANA_COOKIE_SECURE=true` y `YUPANA_HSTS=true`

## 6. Arrancar (versión ligera + HTTPS)
```
docker compose -f docker-compose.yml -f docker-compose.ligero.yml --profile https up --build -d
```
La primera construcción tarda varios minutos en una máquina de 1 GB (es normal; usa la swap). Sigue el avance con `docker compose logs -f app`.

## 7. Comprobar
```
curl https://algo.duckdns.org/actuator/health        # {"status":"UP"}
CLAVE='<tu YUPANA_SEED_CLAVE>' BASE_URL=https://algo.duckdns.org sh scripts/humo.sh
```

## Si algo falla
| Síntoma | Qué mirar |
|---|---|
| `app` se reinicia solo | `docker compose logs app`: si dice `OutOfMemoryError`, sube `mem_limit` y `-Xmx` de `app` en `docker-compose.ligero.yml` |
| La construcción termina en «Killed» | Falta memoria: confirma que la swap existe (`free -h`) |
| No abre desde fuera | Reglas de red del proveedor y `sudo ufw status` |
| Caddy no saca el certificado | El nombre debe apuntar a la IP **antes** de arrancar, y los puertos 80 y 443 deben estar abiertos |
| No inicia sesión | Con HTTPS real, `YUPANA_COOKIE_SECURE=true`. Con la clave equivocada, recuerda que 5 fallos bloquean 15 minutos |

Esto no se ha probado en un servidor real desde donde se desarrolló; la primera vez revisa cada paso.
