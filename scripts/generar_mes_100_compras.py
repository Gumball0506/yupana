#!/usr/bin/env python3
"""
Genera, de forma reproducible (semilla fija), el mes de prueba de 100 compras de Yupana y su hoja de respuestas.

Todo es ficticio: 20 proveedores con RUC 2099991000N, octubre de 2026.
Salidas en datos_demo/mes_100_compras/:
  compras_100.csv, rce_100.csv       los dos archivos que se cargan en el sistema
  respuestas_esperadas.csv           por comprobante, qué debe decir la conciliación y por qué
  resumen_esperado.properties        totales esperados por archivo y por simulador SIRE

Uso:  python scripts/generar_mes_100_compras.py
"""
import csv
import random
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

SEMILLA = 20261115
SALIDA = Path(__file__).resolve().parent.parent / "datos_demo" / "mes_100_compras"
D2 = Decimal("0.01")
IGV = Decimal("0.18")


def q(x):
    return Decimal(x).quantize(D2, rounding=ROUND_HALF_UP)


def importes(base):
    base = q(base)
    igv = q(base * IGV)
    return base, igv, base + igv


def fila(ruc, tipo, serie, numero, fecha, base):
    b, i, t = importes(base)
    return {"ruc_proveedor": ruc, "tipo_comprobante": tipo, "serie": serie, "numero": numero,
            "fecha_emision": fecha.isoformat(), "base_imponible": b, "igv": i, "total": t}


def llave(f):
    """Misma llave que el sistema: RUC + tipo canónico + serie en mayúsculas + número."""
    tipos = {"01": "FACTURA", "03": "BOLETA", "07": "NOTA DE CREDITO", "08": "NOTA DE DEBITO",
             "NC": "NOTA DE CREDITO", "ND": "NOTA DE DEBITO"}
    t = f["tipo_comprobante"].strip().upper()
    return (f["ruc_proveedor"].strip(), tipos.get(t, t), f["serie"].strip().upper(), f["numero"].strip())


def java_hash(texto):
    """String.hashCode() de Java, para predecir el simulador SIRE de forma independiente."""
    h = 0
    for c in texto:
        h = (31 * h + ord(c)) & 0xFFFFFFFF
    return h - (1 << 32) if h >= (1 << 31) else h


def generar():
    rng = random.Random(SEMILLA)
    proveedores = [f"2099991{n:04d}" for n in range(1, 21)]
    inicio = date(2026, 10, 1)
    compras = []
    contador = {"F001": 0, "B001": 0, "FC01": 0}
    for _ in range(100):
        r = rng.random()
        if r < 0.82:
            tipo, serie = "Factura", "F001"
        elif r < 0.94:
            tipo, serie = "Boleta", "B001"
        else:
            tipo, serie = "Nota de Credito", "FC01"
        contador[serie] += 1
        base = Decimal(rng.randint(40, 4000)) + Decimal(rng.choice(["0.00", "0.50", "0.90", "0.99", "0.25"]))
        compras.append(fila(rng.choice(proveedores), tipo, serie, f"{contador[serie]:06d}",
                            inicio + timedelta(days=rng.randint(0, 30)), base))

    indices = list(range(100))
    rng.shuffle(indices)

    def por_tipo(t):
        return [i for i in indices if compras[i]["tipo_comprobante"] == t]

    facturas, boletas, notas = por_tipo("Factura"), por_tipo("Boleta"), por_tipo("Nota de Credito")

    # 7 que deben cuadrar aunque se vean distintas
    disfraces = [(facturas.pop(0), "base_con_un_centimo_de_mas"), (facturas.pop(0), "igv_con_un_centimo_de_menos"),
                 (facturas.pop(0), "tipo_01_en_vez_de_Factura"), (facturas.pop(0), "serie_en_minuscula"),
                 (boletas.pop(0), "tipo_03_en_vez_de_Boleta"), (notas.pop(0), "tipo_NC_en_vez_de_Nota_de_Credito"),
                 (facturas.pop(0), "numero_con_espacios")]
    usados = {i for i, _ in disfraces}
    restantes = [i for i in indices if i not in usados]
    con_diferencia = restantes[:15]
    solo_compras = restantes[15:19]
    plano = restantes[19:]

    rce, respuestas = [], []

    def anotar(f, resultado, motivo, campos=""):
        respuestas.append({"ruc_proveedor": f["ruc_proveedor"], "tipo_comprobante": f["tipo_comprobante"],
                           "serie": f["serie"], "numero": f["numero"], "resultado_esperado": resultado,
                           "motivo": motivo, "campos_distintos": campos})

    for i in plano:
        rce.append(dict(compras[i]))
        anotar(compras[i], "CONCILIADO", "igual")
    for i, motivo in disfraces:
        f = dict(compras[i])
        if motivo == "base_con_un_centimo_de_mas":
            f["base_imponible"] = f["base_imponible"] + D2
        elif motivo == "igv_con_un_centimo_de_menos":
            f["igv"] = f["igv"] - D2
        elif motivo == "tipo_01_en_vez_de_Factura":
            f["tipo_comprobante"] = "01"
        elif motivo == "serie_en_minuscula":
            f["serie"] = f["serie"].lower()
        elif motivo == "tipo_03_en_vez_de_Boleta":
            f["tipo_comprobante"] = "03"
        elif motivo == "tipo_NC_en_vez_de_Nota_de_Credito":
            f["tipo_comprobante"] = "NC"
        elif motivo == "numero_con_espacios":
            f["numero"] = " " + f["numero"] + " "
        rce.append(f)
        anotar(compras[i], "CONCILIADO", motivo)

    # 15 con diferencia: 5 de monto, 3 solo IGV, 3 solo total, 3 solo fecha, 1 fecha y base
    tipos_dif = ["monto"] * 5 + ["igv"] * 3 + ["total"] * 3 + ["fecha"] * 3 + ["fecha_y_base"]
    for i, kind in zip(con_diferencia, tipos_dif):
        c = compras[i]
        f = dict(c)
        if kind == "monto":
            b, ig, t = importes(c["base_imponible"] * Decimal("0.90"))
            f.update(base_imponible=b, igv=ig, total=t)
            campos = "base_imponible;igv;total"
        elif kind == "igv":
            f["igv"] = c["igv"] + Decimal(rng.choice(["1.00", "2.50", "5.00"]))
            f["total"] = f["base_imponible"] + f["igv"]
            campos = "igv;total"
        elif kind == "total":
            f["total"] = c["total"] + Decimal(rng.choice(["0.50", "1.00", "3.00"]))
            campos = "total"
        elif kind == "fecha":
            f["fecha_emision"] = (date.fromisoformat(c["fecha_emision"]) + timedelta(days=rng.randint(1, 3))).isoformat()
            campos = "fecha_emision"
        else:
            b, ig, t = importes(c["base_imponible"] + Decimal("100"))
            f["fecha_emision"] = (date.fromisoformat(c["fecha_emision"]) + timedelta(days=1)).isoformat()
            f.update(base_imponible=b, igv=ig, total=t)
            campos = "fecha_emision;base_imponible;igv;total"
        rce.append(f)
        anotar(c, "DIFERENCIA", "diferencia_" + kind, campos)
    for i in solo_compras:
        anotar(compras[i], "SOLO_COMPRAS", "no_figura_en_el_rce")
    for n in range(3):
        extra = fila(rng.choice(proveedores), "Factura", "F001", f"{9001 + n:06d}",
                     inicio + timedelta(days=rng.randint(0, 30)), Decimal(rng.randint(100, 900)))
        rce.append(extra)
        anotar(extra, "SOLO_RCE", "no_figura_en_tus_compras")
    rce.sort(key=lambda f: (f["fecha_emision"], f["ruc_proveedor"], f["numero"]))
    compras.sort(key=lambda f: (f["fecha_emision"], f["ruc_proveedor"], f["numero"]))
    return compras, rce, respuestas


def signo(f):
    return -1 if llave(f)[1] == "NOTA DE CREDITO" else 1


def totales_archivo(compras, rce, respuestas):
    por_llave_c = {llave(f): f for f in compras}
    por_llave_r = {llave(f): f for f in rce}
    revision, diferencia, conciliados = Decimal("0"), Decimal("0"), 0
    for r in respuestas:
        k = llave({"ruc_proveedor": r["ruc_proveedor"], "tipo_comprobante": r["tipo_comprobante"],
                   "serie": r["serie"], "numero": r["numero"]})
        c, e = por_llave_c.get(k), por_llave_r.get(k)
        if r["resultado_esperado"] == "CONCILIADO":
            conciliados += 1
            continue
        usado = c if c is not None else e
        revision += signo(usado) * usado["igv"]
        diferencia += abs((c["igv"] if c else Decimal(0)) - (e["igv"] if e else Decimal(0)))
    return len(respuestas), conciliados, revision, diferencia


def totales_sire(compras):
    """Predice el simulador SIRE: 0 no informado, 1 base -10 %, 2 fecha +1 día, 3-9 igual, más una factura extra."""
    revision, diferencia, conciliados, total, solo, dif = Decimal(0), Decimal(0), 0, 0, 0, 0
    for c in compras:
        total += 1
        caso = java_hash("|".join(llave(c))) % 10
        if caso == 0:
            revision += signo(c) * c["igv"]
            diferencia += c["igv"]
            solo += 1
        elif caso == 1:
            _, ig, _ = importes(c["base_imponible"] * Decimal("0.90"))
            revision += signo(c) * c["igv"]
            diferencia += abs(c["igv"] - ig)
            dif += 1
        elif caso == 2:
            revision += signo(c) * c["igv"]
            dif += 1
        else:
            conciliados += 1
    total += 1  # factura extra que no está en las compras
    revision += Decimal("18.00")
    diferencia += Decimal("18.00")
    return total, conciliados, revision, diferencia, solo, dif


def escribir(compras, rce, respuestas):
    SALIDA.mkdir(parents=True, exist_ok=True)
    campos = ["ruc_proveedor", "tipo_comprobante", "serie", "numero", "fecha_emision", "base_imponible", "igv", "total"]
    for nombre, filas in (("compras_100.csv", compras), ("rce_100.csv", rce)):
        with open(SALIDA / nombre, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=campos, lineterminator="\n")
            w.writeheader()
            for x in filas:
                w.writerow({k: (f"{x[k]:.2f}" if isinstance(x[k], Decimal) else x[k]) for k in campos})
    with open(SALIDA / "respuestas_esperadas.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(respuestas[0].keys()), lineterminator="\n")
        w.writeheader()
        w.writerows(respuestas)
    t, c, rev, dif = totales_archivo(compras, rce, respuestas)
    ts, cs, revs, difs, solo, dsire = totales_sire(compras)
    with open(SALIDA / "resumen_esperado.properties", "w", encoding="utf-8") as f:
        f.write("# Generado por scripts/generar_mes_100_compras.py (semilla %d). No editar a mano.\n" % SEMILLA)
        f.write(f"archivo.total={t}\narchivo.conciliados={c}\narchivo.no_cuadran={t - c}\n")
        f.write(f"archivo.igv_bajo_revision={rev}\narchivo.diferencia_igv={dif}\n")
        f.write(f"sire.total={ts}\nsire.conciliados={cs}\nsire.no_cuadran={ts - cs}\n")
        f.write(f"sire.solo_compras={solo}\nsire.con_diferencia={dsire}\nsire.solo_rce=1\n")
        f.write(f"sire.igv_bajo_revision={revs}\nsire.diferencia_igv={difs}\n")
    print(f"archivo: {t} comprobantes, {c} cuadran, {t - c} no cuadran, IGV bajo revision {rev}, diferencia {dif}")
    print(f"sire:    {ts} comprobantes, {cs} cuadran, {ts - cs} no cuadran, IGV bajo revision {revs}, diferencia {difs}")


if __name__ == "__main__":
    escribir(*generar())
