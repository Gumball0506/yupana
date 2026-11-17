#!/usr/bin/env python3
"""
Escaneo de dependencias contra la base de vulnerabilidades OSV (osv.dev). No necesita clave.

Uso:
  mvnw -q dependency:list -DincludeScope=runtime -DoutputFile=target/dependencias.txt
  python scripts/escaneo_osv.py target/dependencias.txt [--fallar-en HIGH]

Lee las dependencias que REALMENTE se empaquetan (con sus versiones resueltas), pregunta a OSV por cada una y lista los
hallazgos con su severidad y la versión que lo corrige. Termina con código 1 si hay alguno de severidad HIGH o CRITICAL
que no esté en scripts/osv-excepciones.txt (cada excepción lleva su motivo).
Solo se envían a OSV nombres y versiones de librerías públicas; nada del proyecto.
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

API = "https://api.osv.dev/v1"
ORDEN = {"LOW": 1, "MODERATE": 2, "MEDIUM": 2, "HIGH": 3, "CRITICAL": 4}


def post(ruta, cuerpo):
    peticion = urllib.request.Request(API + ruta, data=json.dumps(cuerpo).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(peticion, timeout=60) as r:
        return json.load(r)


def get(ruta):
    with urllib.request.urlopen(API + ruta, timeout=60) as r:
        return json.load(r)


def dependencias(archivo):
    deps = set()
    for linea in Path(archivo).read_text(encoding="utf-8").splitlines():
        # grupo:artefacto:tipo:version:ambito   (o con clasificador: grupo:artefacto:tipo:clasificador:version:ambito)
        m = re.match(r"^\s+([\w.\-]+):([\w.\-]+):[\w\-]+:(?:[\w.\-]+:)?([\w.\-]+):(compile|runtime|provided)", linea)
        if m:
            deps.add((f"{m.group(1)}:{m.group(2)}", m.group(3)))
    return sorted(deps)


def severidad(vuln):
    nivel = (vuln.get("database_specific") or {}).get("severity")
    if nivel:
        return nivel.upper()
    return "DESCONOCIDA"


def corregida_en(vuln, nombre):
    arreglos = set()
    for afectado in vuln.get("affected", []):
        if afectado.get("package", {}).get("name") != nombre:
            continue
        for rango in afectado.get("ranges", []):
            for evento in rango.get("events", []):
                if "fixed" in evento:
                    arreglos.add(evento["fixed"])
    return ", ".join(sorted(arreglos)) or "sin parche publicado"


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    archivo = sys.argv[1]
    umbral = ORDEN[sys.argv[sys.argv.index("--fallar-en") + 1].upper()] if "--fallar-en" in sys.argv else ORDEN["HIGH"]
    excepciones = {}
    ruta_exc = Path(__file__).with_name("osv-excepciones.txt")
    if ruta_exc.exists():
        for linea in ruta_exc.read_text(encoding="utf-8").splitlines():
            if linea.strip() and not linea.startswith("#"):
                ident, _, motivo = linea.partition("|")
                excepciones[ident.strip()] = motivo.strip()

    deps = dependencias(archivo)
    print(f"Dependencias analizadas: {len(deps)}")
    respuesta = post("/querybatch", {"queries": [{"package": {"name": n, "ecosystem": "Maven"}, "version": v} for n, v in deps]})
    hallazgos = []
    for (nombre, version), resultado in zip(deps, respuesta["results"]):
        for v in resultado.get("vulns", []):
            detalle = get(f"/vulns/{v['id']}")
            hallazgos.append((nombre, version, detalle["id"], severidad(detalle), corregida_en(detalle, nombre),
                              detalle.get("summary", "")[:90]))
    if not hallazgos:
        print("Sin vulnerabilidades conocidas en las dependencias de ejecución.")
        return 0
    hallazgos.sort(key=lambda h: -ORDEN.get(h[3], 0))
    bloquean = 0
    for nombre, version, ident, sev, arreglo, resumen in hallazgos:
        excepcion = excepciones.get(ident)
        marca = "EXCEPCIÓN" if excepcion else ("BLOQUEA" if ORDEN.get(sev, 0) >= umbral else "aviso")
        if marca == "BLOQUEA":
            bloquean += 1
        print(f"[{marca:9}] {sev:9} {nombre}:{version}  {ident}  corrige en: {arreglo}\n            {resumen}"
              + (f"\n            motivo: {excepcion}" if excepcion else ""))
    print(f"\nHallazgos: {len(hallazgos)}; bloquean: {bloquean}")
    return 1 if bloquean else 0


if __name__ == "__main__":
    sys.exit(main())
