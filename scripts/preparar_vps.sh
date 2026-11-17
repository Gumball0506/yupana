#!/bin/sh
# Prepara un VPS Ubuntu nuevo (1 GB de RAM) para correr Yupana: swap, Docker y firewall. Ejecutar como root:
#   sudo sh scripts/preparar_vps.sh
set -eu
[ "$(id -u)" = "0" ] || { echo "Ejecuta con sudo." >&2; exit 1; }

# 1) Swap de 2 GB: sin ella, 1 GB no alcanza para compilar la imagen ni para correr Java + PostgreSQL
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile > /dev/null
  swapon /swapfile
  echo "/swapfile none swap sw 0 0" >> /etc/fstab
  echo "vm.swappiness=20" > /etc/sysctl.d/99-yupana.conf
  sysctl -p /etc/sysctl.d/99-yupana.conf > /dev/null
  echo "Swap de 2 GB creada."
else
  echo "Ya hay swap; no se crea otra."
fi

# 2) Docker (script oficial) si no está
if ! command -v docker > /dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi

# 3) Firewall: solo SSH, HTTP y HTTPS (primero SSH, para no quedarte afuera)
if command -v ufw > /dev/null 2>&1; then
  ufw allow 22/tcp > /dev/null
  ufw allow 80/tcp > /dev/null
  ufw allow 443/tcp > /dev/null
  ufw --force enable > /dev/null
  echo "Firewall activo: 22, 80 y 443."
fi
free -h
echo "Listo. Sigue con docs/DESPLIEGUE_VPS_1GB.md (paso 4)."
