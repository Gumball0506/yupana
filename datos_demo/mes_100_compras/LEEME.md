# Mes de prueba: 100 compras

Datos **ficticios** y reproducibles (semilla fija) para probar el sistema con un mes completo.
Se generan con `python scripts/generar_mes_100_compras.py`; no se editan a mano.

| Archivo | Para qué |
|---|---|
| `compras_100.csv` | 100 compras de 20 proveedores (RUC `2099991000N`), octubre de 2026: facturas, boletas y notas de crédito |
| `rce_100.csv` | La propuesta RCE por archivo, con las diferencias plantadas |
| `respuestas_esperadas.csv` | Por comprobante: resultado esperado y el motivo |
| `resumen_esperado.properties` | Totales esperados para el camino «archivo» y para el camino «simulador SIRE» |

## Lo que se plantó (camino «propuesta por archivo»)
| Qué | Cuántos | Resultado esperado |
|---|---|---|
| Con diferencia (monto, solo IGV, solo total, solo fecha, fecha y base) | 15 | DIFERENCIA |
| Solo en las compras | 4 | SOLO_COMPRAS |
| Solo en la propuesta RCE | 3 | SOLO_RCE |
| Parecen distintos pero deben cuadrar (1 céntimo de base, 1 céntimo de IGV, «01» por Factura, «03» por Boleta, «NC» por Nota de Crédito, serie en minúscula, número con espacios) | 7 | CONCILIADO |
| Iguales | 74 | CONCILIADO |

Total: 103 comprobantes, **81 cuadran y 22 no** (15 + 4 + 3).

## Camino «simulador SIRE»
El simulador toma las 100 compras y arma su propia propuesta con una regla fija por comprobante. Resultado esperado:
**101 comprobantes (los 100 más una factura que no está en las compras), 74 cuadran y 27 no**.
Las cifras de IGV de los dos caminos están en `resumen_esperado.properties`.

## Cómo se verifica
`Mes100ComprasTest` carga los archivos, concilia y compara comprobante por comprobante con `respuestas_esperadas.csv`.
La hoja de respuestas sale de cómo se plantaron los datos, no de ejecutar el sistema; el resultado del simulador SIRE se
predice con una reimplementación independiente de su regla (`String.hashCode` de Java) dentro del mismo script.
