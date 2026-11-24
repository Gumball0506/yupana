const ORIGENES = {
  compras: { nombre: "Compras de la empresa", desc: "Tu registro interno de compras" },
  rce: { nombre: "Propuesta RCE", desc: "Archivo del Registro de Compras Electrónico" },
};
// Archivos de cada botón de ejemplo (servidos desde /ejemplos)
const EJEMPLOS = {
  normal: { compras: "yupana_compras_demo.csv", rce: "yupana_rce_demo.csv" },
  faltantes: { compras: "yupana_compras_demo.csv", rce: "yupana_rce_demo_faltantes.csv" },
  cien: { compras: "yupana_compras_100.csv", rce: "yupana_rce_100.csv" },
};
const COLS = ["ruc_proveedor", "tipo_comprobante", "serie", "numero", "fecha_emision", "base_imponible", "igv", "total"];
const NUM = ["base_imponible", "igv", "total"];
const MAX_BYTES = 2 * 1024 * 1024;
const $ = id => document.getElementById(id);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c]));
const imp = n => Number(n || 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const soles = n => "S/ " + imp(n);
const kb = b => b < 1024 ? b + " B" : (b / 1024).toFixed(1) + " KB";
const ic = n => `<svg class="ic" aria-hidden="true"><use href="#e-${n}"></use></svg>`;
const MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic"];
const fechaTexto = iso => { const [f, h = ""] = iso.split("T"); const [a, m, d] = f.split("-");
  return `${Number(d)} ${MESES[m - 1]} ${a}${h ? ", " + h.slice(0, 5) : ""}`; };

// estado[o] = { fase: vacia|lista|subiendo|ok|error, archivo, errores, celdas, vista, advertencias, detalle }
const estado = { compras: { fase: "vacia" }, rce: { fase: "vacia" } };
let periodoCarga = null;
const conPeriodo = ruta => periodoCarga ? ruta + "?periodo=" + encodeURIComponent(periodoCarga) : ruta;
const ultimas = {};   // última importación guardada por origen

// ---------- Eventos anónimos (Marketing 5.0) ----------
let sesion;
try { sesion = sessionStorage.getItem("yupana_sesion"); } catch {}
if (!sesion) {
  sesion = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(16).slice(2).padEnd(12, "0");
  try { sessionStorage.setItem("yupana_sesion", sesion); } catch {}
}
function evento(tipo, detalle) {
  fetch("/api/eventos", { method: "POST", headers: { "Content-Type": "application/json" }, keepalive: true,
    body: JSON.stringify({ sesion, tipo, detalle }) }).catch(() => {});
}

// ---------- Tarjetas ----------
function renderTarjetas() {
  $("tarjetas").innerHTML = Object.entries(ORIGENES).map(([o, def]) => `
    <div class="e1-card" id="card-${o}">
      <div class="e1-card-head">
        <div><h3>${def.nombre}</h3><p class="desc">${def.desc} · CSV o XLSX · máx. 2 MB</p></div>
        <span class="e1-chip" id="chip-${o}"></span>
      </div>
      <label class="e1-drop">
        <input class="sr-only" type="file" id="f-${o}" accept=".csv,.xlsx">
        <strong>Arrastra tu archivo aquí</strong>
        <span>o haz clic para elegirlo</span>
      </label>
      <div id="archivo-${o}"></div>
      <div class="e1-res" id="res-${o}" aria-live="polite"></div>
    </div>`).join("");

  for (const o of Object.keys(ORIGENES)) {
    $("f-" + o).addEventListener("change", e => { if (e.target.files[0]) elegir(o, e.target.files[0]); e.target.value = ""; });
    const card = $("card-" + o);
    card.addEventListener("dragover", e => { e.preventDefault(); card.classList.add("drag"); });
    card.addEventListener("dragleave", e => { if (!card.contains(e.relatedTarget)) card.classList.remove("drag"); });
    card.addEventListener("drop", e => {
      e.preventDefault(); card.classList.remove("drag");
      if (e.dataTransfer.files[0]) elegir(o, e.dataTransfer.files[0]);
    });
  }
}
// Soltar fuera de una tarjeta no debe abrir el archivo en el navegador
addEventListener("dragover", e => e.preventDefault());
addEventListener("drop", e => e.preventDefault());

/** Validación previa en el navegador: respuesta inmediata sin subir nada. */
function elegir(o, archivo) {
  const ext = (archivo.name.match(/\.[^.]+$/) || [""])[0].toLowerCase();
  let error = null;
  if (![".csv", ".xlsx"].includes(ext)) error = `Formato '${ext || "sin extensión"}' no admitido. Usa CSV o XLSX.`;
  else if (archivo.size === 0) error = "El archivo está vacío.";
  else if (archivo.size > MAX_BYTES) error = "El archivo supera el límite de 2 MB.";
  estado[o] = error ? { fase: "error", archivo, errores: [error], celdas: [] } : { fase: "lista", archivo };
  pintar();
}

const CHIPS = {
  vacia: () => "Sin archivo",
  lista: () => ic("file") + "Listo para cargar",
  subiendo: () => ic("refresh") + "Verificando…",
  ok: e => ic("check") + `Importado · ${e.detalle?.total_filas ?? ""} comprobantes`,
  error: () => ic("x") + "No se importó",
};

function pintarTarjeta(o) {
  const e = estado[o];
  $("card-" + o).className = "e1-card " + (e.fase === "ok" ? "ok" : e.fase === "error" ? "error" : "");
  const chip = $("chip-" + o);
  chip.className = "e1-chip " + e.fase;
  chip.innerHTML = CHIPS[e.fase](e);

  // Un archivo por tarjeta: con archivo elegido, la zona de arrastre se oculta (arrastrar encima sigue reemplazándolo)
  $("card-" + o).querySelector(".e1-drop").hidden = e.fase !== "vacia";
  const nombre = e.fase === "ok" ? esc(e.detalle.nombre_archivo)
    : e.archivo ? `${esc(e.archivo.name)} · ${kb(e.archivo.size)}` : "";
  $("archivo-" + o).innerHTML = nombre
    ? `<div class="e1-file">${ic("file")}<span>${nombre}</span>
         ${e.fase === "subiendo" ? "" : `<span class="acts"><button type="button" class="e1-btn sec" data-cambiar="${o}">Cambiar archivo</button>
           ${e.fase === "ok" ? "" : `<button type="button" class="e1-btn quiet" data-quitar="${o}">Quitar</button>`}</span>`}</div>`
    : "";

  let html = "";
  if (e.fase === "ok" && e.detalle) {
    const r = e.detalle.resumen;
    html += `<p class="e1-resumen"><b>${r.comprobantes} comprobantes</b> · ${esc(fechaTexto(r.desde))} → ${esc(fechaTexto(r.hasta))}
      · Base ${soles(r.base_imponible)} · IGV ${soles(r.igv)} · <b>Total ${soles(r.total)}</b>
      <button type="button" class="e1-link" data-contenido="${o}">Ver contenido</button></p>`;
  }
  if (e.fase === "error") {
    html += `<div class="e1-msg err"><strong>No se importó el archivo. Nada se guardó.</strong>
      <ul>${e.errores.map(x => `<li>${esc(x)}</li>`).join("")}</ul>
      ${pistas(e.errores)}
      ${ultimas[o] ? `<div class="pista">Se mantiene la última carga válida: ${esc(ultimas[o].nombre_archivo)} (#${ultimas[o].id}).</div>` : ""}</div>`;
    html += tablaRechazo(e);
  }
  $("res-" + o).innerHTML = html;
}

/** Cómo corregir cada tipo de error (P09: «¿sabes cómo corregir el archivo?»). */
function pistas(errores) {
  const t = errores.join(" ");
  const p = [];
  if (/Falta la columna/.test(t)) p.push(`Descarga la <a href="/plantilla/plantilla_yupana.xlsx" download data-plantilla="xlsx">plantilla</a> y copia tus datos en esas columnas.`);
  if (/solo tiene encabezados|está vacío/.test(t)) p.push("Agrega al menos un comprobante debajo de los encabezados.");
  if (/no es un número/.test(t)) p.push("Escribe los importes con punto decimal, sin «S/» ni comas de miles (ej. 1416.00).");
  if (/negativo/.test(t)) p.push("Los importes deben ser 0 o mayores.");
  if (/AAAA-MM-DD/.test(t)) p.push("Escribe la fecha como AAAA-MM-DD (ej. 2026-10-03).");
  if (/11 dígitos/.test(t)) p.push("El RUC debe tener 11 dígitos, sin espacios ni guiones.");
  if (/duplicado/.test(t)) p.push("Elimina la fila repetida: cada comprobante debe aparecer una sola vez.");
  if (/está vacía/.test(t)) p.push("Completa tipo, serie y número en cada fila.");
  if (/UTF-8/.test(t)) p.push("En Excel: Archivo → Guardar como → «CSV UTF-8 (delimitado por comas)».");
  if (/no admitido/.test(t)) p.push("Sube un archivo .csv o .xlsx.");
  if (/2 MB/.test(t)) p.push("Divide el archivo o quita columnas que no se usan.");
  return p.length ? `<div class="pista"><strong>Cómo corregirlo:</strong> ${p.join(" ")}</div>` : "";
}

/** Filas con error del archivo rechazado, con la celda exacta en rojo. */
function tablaRechazo(e) {
  const v = e.vista;
  if (!v) return "";
  if (!v.filas.length) {
    const encontradas = v.encabezado.filter(Boolean);
    return `<p class="e1-info">Columnas encontradas en tu archivo: ${encontradas.length ? encontradas.map(esc).join(", ") : "ninguna"}.</p>`;
  }
  const mala = (n, i) => e.celdas.some(c => c.fila === n && (c.columna === null || c.columna === v.columnas[i]));
  const nombres = [...new Set(["serie", "numero", ...e.celdas.map(c => c.columna).filter(Boolean)])];
  const idx = nombres.map(c => v.columnas.indexOf(c)).filter(i => i >= 0);
  return `<p class="e1-info" style="margin-top:12px">Filas con error (celda marcada en rojo):</p>
    <div class="e1-scroll"><table class="e1-data">
      <thead><tr><th>Fila</th>${idx.map(i => `<th>${esc(v.encabezado[i])}</th>`).join("")}</tr></thead>
      <tbody>${v.filas.map(f => `<tr><td>${f.n}</td>${idx.map(i =>
        `<td class="${mala(f.n, i) ? "mal" : ""}">${esc(f.celdas[i] ?? "")}</td>`).join("")}</tr>`).join("")}</tbody>
    </table></div>`;
}

document.addEventListener("click", e => {
  const q = e.target.closest("[data-quitar]");
  if (q) {
    const o = q.dataset.quitar;
    estado[o] = ultimas[o] ? { fase: "ok", detalle: ultimas[o] } : { fase: "vacia" };
    pintar();
  }
  const cb = e.target.closest("[data-cambiar]");
  if (cb) $("f-" + cb.dataset.cambiar).click();
  const p = e.target.closest("[data-plantilla]");
  if (p) evento("plantilla_descargada", p.dataset.plantilla);
  const v = e.target.closest("[data-ver]");
  if (v) verImportacion(Number(v.dataset.ver));
  const vc = e.target.closest("[data-contenido]");
  if (vc) verContenido(ultimas[vc.dataset.contenido]);
  // Menús desplegables: se cierran al elegir una opción o al hacer clic fuera
  document.querySelectorAll(".e1-menu[open]").forEach(m => {
    if (!m.contains(e.target) || e.target.closest(".e1-menu-lista")) m.open = false;
  });
  const ir = e.target.closest("[data-ir]");
  if (ir) {
    if (!$("fila-" + ir.dataset.ir)) { filtro = "todos"; pintarPanel(); }
    const fila = $("fila-" + ir.dataset.ir);
    fila?.scrollIntoView({ behavior: "smooth", block: "center" });
    fila?.classList.add("e1-foco"); setTimeout(() => fila?.classList.remove("e1-foco"), 1500);
  }
  const f = e.target.closest("[data-filtro]");
  if (f) { filtro = f.dataset.filtro; pintarPanel(); }
  if (e.target.closest("[data-conciliar]")) conciliar();
  if (e.target.closest("[data-ver-casos]")) {
    $("dlg-caso").close();
    filtro = filtroInicial(conciliacion);
    pintarPanel();
    $("conciliacion").scrollIntoView({ behavior: "smooth" });
  }
  if (e.target.closest("[data-exportar]")) evento("resultado_exportado");
  const rv = e.target.closest("[data-revisar]");
  if (rv) abrirCaso(Number(rv.dataset.revisar));
  const ac = e.target.closest("[data-accion]");
  if (ac) guardarAccion(Number(ac.dataset.caso), ac.dataset.accion);
  if (e.target.closest("[data-cerrar]")) $("dlg-caso").close();
});

// ---------- Subida ----------
function motivo(errores) {
  const t = errores.join(" ");
  if (/Falta la columna/.test(t)) return "falta_columna";
  if (/vacío|encabezados/.test(t)) return "archivo_vacio";
  if (/no admitido/.test(t)) return "formato";
  if (/UTF-8/.test(t)) return "codificacion";
  if (/2 MB/.test(t)) return "tamano";
  if (/duplicado/.test(t)) return "duplicado";
  return "dato_invalido";
}

async function subir(o) {
  const e = estado[o];
  estado[o] = { ...e, fase: "subiendo" };
  pintar();
  evento("carga_iniciada", o);
  const fd = new FormData();
  fd.append("origen", o);
  fd.append("archivo", e.archivo);
  if (periodoCarga) fd.append("periodo", periodoCarga);
  try {
    const r = await fetch("/api/cargas", { method: "POST", body: fd });
    const d = await r.json().catch(() => ({}));
    if (r.ok) {
      ultimas[o] = d;
      estado[o] = { fase: "ok", detalle: d };
      evento("carga_exitosa", o);
    } else {
      const errores = d.errores || [r.status === 413 ? "El archivo supera el límite de 2 MB."
                                                      : "Error inesperado del servidor (" + r.status + ")."];
      estado[o] = { fase: "error", archivo: e.archivo, errores, celdas: d.celdas || [], vista: d.vista };
      evento("carga_fallida", motivo(errores));
    }
  } catch {
    estado[o] = { fase: "error", archivo: e.archivo, errores: ["No hay conexión con el servidor."], celdas: [] };
  }
}

let cargaEnCurso = false;
async function cargarListas() {
  if (cargaEnCurso) return;
  cargaEnCurso = true;
  $("periodo-carga").disabled = true;
  $("btn-cargar").disabled = true;
  const subidos = Object.keys(ORIGENES).filter(o => estado[o].fase === "lista");
  for (const o of subidos) await subir(o);
  // Aviso integrado: si todo se importó y hay ambos lotes, se concilia en el acto (nunca queda un resultado viejo)
  const fallo = subidos.some(o => estado[o].fase === "error");
  if (!fallo && subidos.length && ultimas.compras && ultimas.rce) await conciliar(false);
  pintar();
  refrescarHistorial();
  mostrarResultadoCarga(subidos);
  cargaEnCurso = false;
  $("periodo-carga").disabled = false;
  pintar();
}

/** Cuadro al frente: resultado de la conciliación + advertencias de formato + archivos rechazados, todo junto. */
function mostrarResultadoCarga(origenes) {
  const fallidos = origenes.filter(o => estado[o].fase === "error");
  // Si algo falló, solo se muestra el error: nada de resultados ni advertencias de cargas anteriores
  const conAdv = fallidos.length ? [] : Object.keys(ORIGENES).filter(o => estado[o].fase === "ok" && estado[o].detalle.advertencias?.length);
  const faltan = Object.keys(ORIGENES).filter(o => !ultimas[o]);
  const c = !fallidos.length && conciliacion?.id && !desactualizada() ? conciliacion : null;
  const cuenta = t => c ? c.resultados.filter(r => r.resultado === t).length : 0;
  const nAdv = conAdv.reduce((s, o) => s + estado[o].detalle.advertencias.length, 0);
  const dlg = $("dlg-caso");
  dlg.innerHTML = `<form method="dialog" class="e1-dlg-in">
    <h3 id="dlg-titulo">${fallidos.length ? "No se pudo conciliar" : c ? "Resultado de la conciliación" : "Archivo cargado"}</h3>
    ${fallidos.map(o => `<div class="e1-msg err"><strong>${esc(ORIGENES[o].nombre)}: no se importó. Nada se guardó.</strong>
      <ul>${estado[o].errores.map(x => `<li>${esc(x)}</li>`).join("")}</ul></div>`).join("")}
    ${fallidos.length ? `<p class="e1-info">Corrige el archivo (en su tarjeta verás cómo) y vuelve a cargarlo: no se concilia con un archivo anterior.</p>` : ""}
    ${!fallidos.length && !c && faltan.length ? `<p class="e1-info">Falta cargar ${faltan.map(o => `«${esc(ORIGENES[o].nombre)}»`).join(" y ")} para conciliar.</p>` : ""}
    ${c ? `<div class="e1-msg ${c.con_diferencia ? "warn" : "ok"}">
      <strong>${c.total} comprobantes: ${c.conciliados} cuadran${c.con_diferencia ? `, <b>${c.con_diferencia} no cuadran</b>` : ""}.</strong>
      ${c.con_diferencia ? `<ul>
        ${cuenta("DIFERENCIA") ? `<li>${cuenta("DIFERENCIA")} están en ambos archivos pero difieren en fecha o importes.</li>` : ""}
        ${cuenta("SOLO_COMPRAS") ? `<li>${cuenta("SOLO_COMPRAS")} están en tus compras y no en el RCE.</li>` : ""}
        ${cuenta("SOLO_RCE") ? `<li>${cuenta("SOLO_RCE")} están en el RCE y no en tus compras.</li>` : ""}</ul>
        <p>IGV bajo revisión (demo): <b>${soles(c.igv_revision)}</b></p>` : ""}
      ${c.total !== ultimas.compras?.total_filas ? `<p class="e1-info">Son ${c.total} y no ${ultimas.compras?.total_filas} porque se cuentan los comprobantes de ambos archivos sin repetir.</p>` : ""}</div>` : ""}
    ${conAdv.length ? `<div class="e1-msg warn"><strong>Además, ${nAdv} fila${nAdv === 1 ? "" : "s"} con el total mal sumado dentro del archivo</strong>
      ${conAdv.map(o => `<p>${esc(ORIGENES[o].nombre)} · ${esc(estado[o].detalle.nombre_archivo)}</p>
        <ul>${estado[o].detalle.advertencias.map(a => `<li>${esc(a)}</li>`).join("")}</ul>`).join("")}
      <p class="e1-info">Esto revisa cada archivo por separado (total = base + IGV). No impide conciliar.</p></div>` : ""}
    <div class="e1-dlg-acts">
      <button type="button" class="e1-btn quiet" data-cerrar>Cerrar</button>
      ${c ? `<button type="button" class="e1-btn t" data-ver-casos>${c.con_diferencia ? "Ver casos por revisar" : "Ver resultado"}${ic("arrow")}</button>` : ""}
    </div>
  </form>`;
  if (!dlg.open) dlg.showModal();
}
$("btn-cargar").addEventListener("click", cargarListas);

async function usarEjemplos(variante) {
  evento("ejemplos_usados", variante === "normal" ? undefined : variante);
  for (const o of Object.keys(ORIGENES)) {
    const nombre = EJEMPLOS[variante][o];
    const blob = await (await fetch("/ejemplos/" + nombre)).blob();
    estado[o] = { fase: "lista", archivo: new File([blob], nombre, { type: "text/csv" }) };
  }
  pintar();
  await cargarListas();
}
$("btn-ejemplos").addEventListener("click", () => usarEjemplos("normal"));
$("btn-faltantes").addEventListener("click", () => usarEjemplos("faltantes"));
$("btn-100").addEventListener("click", () => usarEjemplos("cien"));

$("btn-reiniciar").addEventListener("click", async () => {
  if (!confirm(`¿Limpiar «${yo?.empresa?.razon_social ?? "esta empresa"}»?\n\nSe borran sus archivos cargados, conciliaciones, casos revisados y chat. `
    + "La empresa y las demás empresas no se tocan.\n\nNo se puede deshacer.")) return;
  const r = await fetch("/api/cargas", { method: "DELETE" }).catch(() => null);
  if (!r?.ok) { alert("No se pudo limpiar la empresa: revisa que el servidor siga encendido."); return; }
  evento("demo_reiniciada");
  for (const o of Object.keys(ORIGENES)) { estado[o] = { fase: "vacia" }; delete ultimas[o]; }
  conciliacion = null;
  $("panel").innerHTML = "";
  chat.mensajes = [];
  pintarChat();
  pintar();
  refrescarHistorial();
});

// ---------- Contenido de un archivo (cuadro) ----------
function verContenido(d) {
  if (!d) return;
  const s = d.resumen, anterior = ultimas[d.origen] && d.id !== ultimas[d.origen].id;
  const dlg = $("dlg-datos");
  dlg.innerHTML = `<form method="dialog" class="e1-dlg-in">
    <div class="e1-item-top"><h3 id="datos-t">${esc(d.origen_nombre)}</h3>${anterior ? `<span class="e1-old">${ic("alert")}Importación anterior</span>` : ""}</div>
    <p class="e1-info">${esc(d.nombre_archivo)} · importación #${d.id} · ${esc(fechaTexto(d.fecha))} · mostrando ${d.filas.length} de ${d.total_filas}</p>
    <div class="e1-stats">
      <div class="e1-stat"><b>${s.comprobantes}</b><span>Comprobantes</span></div>
      <div class="e1-stat"><b class="chico">${esc(s.desde)} → ${esc(s.hasta)}</b><span>Rango de fechas</span></div>
      <div class="e1-stat"><b>${soles(s.base_imponible)}</b><span>Base imponible</span></div>
      <div class="e1-stat"><b>${soles(s.igv)}</b><span>IGV</span></div>
      <div class="e1-stat"><b>${soles(s.total)}</b><span>Total</span></div>
    </div>
    <div class="e1-scroll e1-scroll-alto"><table class="e1-data">
      <thead><tr>${COLS.map(c => `<th class="${NUM.includes(c) ? "n" : ""}">${c}</th>`).join("")}</tr></thead>
      <tbody>${d.filas.map(f => `<tr>${COLS.map(c =>
        NUM.includes(c) ? `<td class="n">${imp(f[c])}</td>` : `<td>${esc(f[c])}</td>`).join("")}</tr>`).join("")}</tbody>
    </table></div>
    <div class="e1-dlg-acts"><button class="e1-btn">Cerrar</button></div>
  </form>`;
  dlg.showModal();
}

// ---------- Historial ----------
async function refrescarHistorial() {
  if (!$("det-historial").open) return;
  let h;
  try {
    h = await api("/api/cargas/historial");
  } catch {
    $("historial").textContent = "No se pudo consultar el historial. Vuelve a abrirlo para intentar de nuevo.";
    return;
  }
  const grupos = new Map();
  for (const i of h) {
    const periodo = i.periodo ?? "";
    if (!grupos.has(periodo)) grupos.set(periodo, []);
    grupos.get(periodo).push(i);
  }
  $("historial").innerHTML = h.length ? [...grupos.keys()].sort().reverse().map(periodo =>
    `<section class="e1-historial-mes"><h3>${esc(periodo || "Cargas sin periodo asignado")}</h3>
      ${tablaHistorial(grupos.get(periodo))}</section>`).join("")
    : `<p class="e1-info">Aún no hay importaciones.</p>`;
}

function tablaHistorial(h) {
  return `<div class="e1-scroll"><table class="e1-data">
    <thead><tr><th>#</th><th>Origen</th><th>Archivo</th><th>Fecha</th><th class="n">Filas</th><th></th></tr></thead>
    <tbody>${h.map(i => `<tr><td>${i.id}</td><td>${esc(ORIGENES[i.origen].nombre)}</td><td>${esc(i.nombre_archivo)}</td>
      <td>${esc(i.fecha.replace("T", " "))}</td><td class="n">${i.total_filas}</td>
      <td><button class="e1-link" data-ver="${i.id}">Ver</button>
        ${i.archivo_disponible ? `<a href="/api/cargas/${i.id}/archivo">Descargar original</a>` : ""}</td></tr>`).join("")}</tbody></table></div>`;
}
$("det-historial").addEventListener("toggle", () => { if ($("det-historial").open) { evento("historial_visto"); refrescarHistorial(); } });

async function verImportacion(id) {
  verContenido(await fetch("/api/cargas/" + id).then(r => r.json()));
}

// ---------- Pintado general ----------
function pintar() {
  for (const o of Object.keys(ORIGENES)) pintarTarjeta(o);
  const fases = Object.values(estado).map(e => e.fase);
  $("btn-cargar").disabled = cargaEnCurso || !fases.includes("lista");
  const periodo = periodoCarga ?? conciliacion?.periodo ?? ultimas.compras?.resumen?.desde?.slice(0, 7);
  $("crumb").innerHTML = `<span>Conciliación</span>${periodo ? `<span aria-hidden="true">/</span><b>Periodo ${esc(periodo)}</b>` : ""}`;
  pintarPanel();
}

// ---------- H2 · Conciliación (2.1) y panel (2.2) · diseño E1 «Hoja de cuadre» ----------
const ESTADOS = {
  CONCILIADO: ["ok", "check", "Cuadra"],
  DIFERENCIA: ["x", "alert", "Con diferencia"],
  SOLO_COMPRAS: ["x", "alert", "No figura en el RCE"],
  SOLO_RCE: ["x", "alert", "No figura en tus compras"],
};
const CAMPOS = { fecha_emision: "Fecha", base_imponible: "Base imponible", igv: "IGV", total: "Total" };
const PALABRAS = ["cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez"];
const palabra = n => PALABRAS[n] ?? String(n);
let conciliacion = null;
let filtro = "todos";   // todos | dif | ok
// C01 (feedback E1-02): primero los casos a revisar; si todo cuadra, la lista completa
const filtroInicial = c => c?.con_diferencia ? "dif" : "todos";

const desactualizada = () => conciliacion?.id &&
  ((ultimas.compras && ultimas.compras.id !== conciliacion.id_compras) || (ultimas.rce && ultimas.rce.id !== conciliacion.id_rce));

async function conciliar(desplazar = true) {
  $("btn-conciliar").disabled = true;
  try {
    const r = await fetch(conPeriodo("/api/conciliar"), { method: "POST" });
    const d = await r.json().catch(() => ({}));
    if (r.ok) { conciliacion = d; filtro = filtroInicial(d); evento("panel_visto"); }
    else { conciliacion = null; $("panel").innerHTML = `<div class="e1-msg err">${esc((d.errores || ["Error al conciliar (" + r.status + ")."]).join(" "))}</div>`; }
  } catch {
    $("panel").innerHTML = `<div class="e1-msg err">No hay conexión con el servidor.</div>`;
  }
  pintar();
  if (conciliacion && desplazar) $("conciliacion").scrollIntoView({ behavior: "smooth" });
}
$("btn-conciliar").addEventListener("click", () => conciliar());

/** Diferencia RCE − compras con signo: «−50.00», o «+1 día» para fechas. */
function delta(campo, c, r) {
  if (c == null || r == null) return "";
  if (campo === "fecha_emision") {
    const dias = Math.round((Date.parse(r) - Date.parse(c)) / 864e5);
    return `${dias > 0 ? "+" : "−"}${Math.abs(dias)} día${Math.abs(dias) === 1 ? "" : "s"}`;
  }
  const d = Number(r) - Number(c);
  return (d > 0 ? "+" : d < 0 ? "−" : "") + imp(Math.abs(d));
}
const valor = (campo, v) => v == null ? "—" : campo === "fecha_emision" ? esc(v) : imp(v);
const difDe = (r, campo) => r.diferencias.some(d => d.campo === campo);
const docDe = r => `${esc(r.serie)}-${esc(r.numero)}`;

function celda(r, campo) {
  const c = r.compras?.[campo], v = r.rce?.[campo];
  const cls = campo === "fecha_emision" ? "mono" : "n";
  if (!r.compras && r.rce) return `<td class="${cls}"><div class="two${campo === "fecha_emision" ? " l" : ""}">
    <span class="rce"><abbr title="Propuesta RCE">RCE</abbr>${valor(campo, v)}</span></div></td>`;
  if (!difDe(r, campo)) return `<td class="${cls}">${valor(campo, c ?? v)}</td>`;
  return `<td class="${cls}"><div class="two${campo === "fecha_emision" ? " l" : ""}"><span>${valor(campo, c)}</span>
    <span class="rce"><abbr title="Propuesta RCE">RCE</abbr>${valor(campo, v)}</span><span class="dl">${delta(campo, c, v)}</span></div></td>`;
}

function filaTabla(r) {
  const [cls, icono, texto] = ESTADOS[r.resultado];
  const ok = r.resultado === "CONCILIADO";
  return `<tr id="fila-${r.id}" class="${ok ? "ok" : "dif"}">
    <td class="mono">${String(r.orden).padStart(2, "0")}</td>
    <td><span class="e1-st ${cls}">${ic(icono)}${texto}</span></td>
    <td>${ok ? `<span class="mono">${docDe(r)}</span>` : `<b class="mono">${docDe(r)}</b><small>${esc(r.tipo_comprobante)}</small>`}</td>
    <td class="mono">${esc(r.ruc_proveedor)}</td>
    ${Object.keys(CAMPOS).map(c => celda(r, c)).join("")}
    <td class="n">${ok ? "" : `${insignia(r)}<button class="e1-btn t chico" data-revisar="${r.id}">Revisar${ic("arrow")}</button>`}</td></tr>`;
}

function tarjetaMovil(r) {
  const [cls, icono, texto] = ESTADOS[r.resultado];
  const base = r.compras ?? r.rce;
  const campos = r.diferencias.length ? r.diferencias.map(d => d.campo) : Object.keys(CAMPOS);
  const fechaDistinta = difDe(r, "fecha_emision");
  return `<article class="e1-item">
    <div class="e1-item-top"><b>${docDe(r)}</b><span class="e1-st ${cls}">${ic(icono)}${texto}</span></div>
    <p class="meta">${esc(r.tipo_comprobante)} · RUC ${esc(r.ruc_proveedor)} · ${fechaDistinta ? "Total " + soles(base.total) : esc(base.fecha_emision)}</p>
    <div class="cmp"><span class="h">Campo</span><span class="h v">Compras</span><span class="h v">RCE</span><span class="h v">Dif.</span>
      ${campos.map(c => `<span>${CAMPOS[c] || esc(c)}</span><span class="v">${valor(c, r.compras?.[c])}</span><span class="v r">${valor(c, r.rce?.[c])}</span><span class="v">${delta(c, r.compras?.[c], r.rce?.[c]) || "—"}</span>`).join("")}</div>
    ${insignia(r)}
    <button class="e1-btn t" data-revisar="${r.id}">Revisar caso${ic("arrow")}</button>
  </article>`;
}

function pintarPanel() {
  const c = conciliacion;
  const btn = $("btn-conciliar"), vigente = c?.id && !desactualizada();
  btn.disabled = !(ultimas.compras && ultimas.rce);
  btn.hidden = !!vigente || btn.disabled; // ya se concilia al cargar; solo aparece si el resultado quedó desactualizado
  btn.className = "e1-btn " + (vigente ? "sec" : "t");
  btn.innerHTML = (vigente ? "Volver a conciliar" : "Conciliar") + ic("arrow");
  if (!c || !c.id) {
    if (!$("panel").querySelector(".e1-msg.err"))
      $("panel").innerHTML = ultimas.compras && ultimas.rce ? "" : `<p class="e1-info">Carga ambos archivos <a href="#tarjetas">arriba</a> para habilitar la conciliación.</p>`;
    return;
  }
  const rs = c.resultados;
  // ponytail: con muchos comprobantes el mapa empieza cerrado; se respeta lo que el usuario abrió/cerró
  const mapaAbierto = $("mapa")?.dataset.c == c.id ? $("mapa").open : rs.length <= 60;
  const dif = rs.filter(r => r.resultado !== "CONCILIADO"), ok = rs.filter(r => r.resultado === "CONCILIADO");
  const etiqueta = r => String(r.orden).padStart(2, "0");
  const verDif = filtro !== "ok", verOk = filtro !== "dif";
  const seg = (k, txt) => `<button data-filtro="${k}" aria-pressed="${filtro === k}">${txt}</button>`;

  const titular = dif.length
    ? `<em>${dif.length} de ${rs.length}</em> ${dif.length === 1 ? "comprobante no cuadra" : "comprobantes no cuadran"} con la propuesta del RCE.`
    : `Los ${rs.length} comprobantes cuadran con la propuesta del RCE.`;
  const sub = !dif.length ? "Ninguno requiere revisión: todos coinciden campo por campo con tu registro de compras."
    : `Revisa ${dif.length === 1 ? "este" : "estos " + palabra(dif.length)}.` + (ok.length
      ? ok.length === 1 ? " El otro coincide campo por campo con tu registro de compras."
                        : ` Los otros ${palabra(ok.length)} coinciden campo por campo con tu registro de compras.` : "");

  const revisados = dif.filter(r => r.revision), observados = revisados.filter(r => r.revision.accion === "OBSERVADO").length;
  const avance = dif.length ? ` <b>Revisados: ${revisados.length} de ${dif.length}</b> (${observados} observado${observados === 1 ? "" : "s"}, ${revisados.length - observados} confirmado${revisados.length - observados === 1 ? "" : "s"}).` : "";

  $("panel").innerHTML = `
    ${desactualizada() ? `<div class="e1-alert" role="status">
      <p>${ic("alert")}<span>Subiste un archivo después de esta conciliación. Concilia de nuevo para ver los datos al día.</span></p>
      <button class="e1-btn sec" data-conciliar>${ic("refresh")}Conciliar de nuevo</button></div>` : ""}

    <section class="e1-head">
      <div>
        <p class="e1-meta">Conciliación #${c.id} · ${esc(fechaTexto(c.fecha))} · Compras (import #${c.id_compras}) frente a propuesta RCE (import #${c.id_rce})</p>
        <h3 class="e1-h1">${titular}</h3>
        <p class="e1-sub">${sub}${avance}</p>
        ${dif.length ? `<button type="button" class="e1-btn t" data-resumen>${ic("chat")}Enviar resumen a la empresa</button>` : ""}
      </div>
      <aside class="e1-igv" aria-label="IGV bajo revisión">
        <p class="lab">IGV bajo revisión (demo)</p>
        <p class="amt">${soles(c.igv_revision)}</p>
        <p class="fine">IGV de ${dif.length === 1 ? "el comprobante observado" : `los ${dif.length} comprobantes observados`}. Cifra indicativa sujeta a revisión profesional: no es crédito fiscal perdido ni recuperado.</p>
        <dl><dt>Diferencia de IGV entre fuentes</dt><dd>${soles(c.diferencia_igv)}</dd></dl>
        ${dif.length ? `<p class="fine e1-compara"><b>${soles(c.igv_revision)}</b> es el IGV de los comprobantes por revisar; <b>${soles(c.diferencia_igv)}</b> es cuánto difiere ese IGV entre tu registro y el RCE. Son medidas distintas: no se suman.</p>` : ""}
      </aside>
    </section>

    <details class="e1-mapa" id="mapa" data-c="${c.id}"${mapaAbierto ? " open" : ""}>
      <summary>Mapa de comprobantes · ${dif.length} con diferencia, ${ok.length} cuadran <small>(clic en un cuadro para ir a su fila)</small></summary>
      <div class="e1-map" aria-label="Los ${rs.length} comprobantes: ${ok.length} cuadran y ${dif.length} tienen diferencia">
        ${rs.map(r => `<button type="button" data-ir="${r.id}"${r.resultado !== "CONCILIADO" ? ' class="x"' : ""} title="${docDe(r)}">${etiqueta(r)}</button>`).join("")}
      </div>
      <p class="e1-key">${dif.length ? `<span><i class="x"></i><b>Con diferencia:</b> ${dif.length <= 30 ? dif.map(etiqueta).join(", ") : dif.length}</span>` : ""}
        ${ok.length ? `<span><i></i><b>Cuadran:</b> ${ok.length}</span>` : ""}</p>
    </details>

    <section class="e1-sheet" aria-label="Hoja de cuadre">
      <div class="e1-tools">
        <div class="e1-seg" role="group" aria-label="Filtrar">
          ${seg("todos", `Todos (${rs.length})`)}${seg("dif", `Con diferencia (${dif.length})`)}${seg("ok", `Cuadran (${ok.length})`)}
        </div>
        <p class="e1-read">Arriba, tu registro de compras. Debajo, en <b>terracota con «RCE»</b>, lo que propone el RCE.</p>
        <a class="e1-btn sec" href="/api/conciliaciones/${c.id}/csv" download data-exportar>${ic("down")}Exportar CSV</a>
      </div>

      <table class="e1-tbl">
        <thead><tr><th>#</th><th>Estado</th><th>Comprobante</th><th>Proveedor (RUC)</th><th>Fecha de emisión</th><th class="n">Base imponible</th><th class="n">IGV</th><th class="n">Total</th><th class="n">Revisión</th></tr></thead>
        <tbody>
          ${filtro === "todos" ? [...rs].sort((a, b) => a.orden - b.orden).map(filaTabla).join("") : ""}
          ${filtro === "dif" && dif.length ? `<tr class="grp"><td colspan="9">Por revisar · ${dif.length} comprobante${dif.length === 1 ? "" : "s"}</td></tr>${dif.map(filaTabla).join("")}` : ""}
          ${filtro === "ok" && ok.length ? `<tr class="grp"><td colspan="9">Cuadran · ${ok.length} comprobante${ok.length === 1 ? "" : "s"}, sin acción pendiente</td></tr>${ok.map(filaTabla).join("")}` : ""}
          ${(verDif ? dif.length : 0) + (verOk ? ok.length : 0) ? "" : `<tr><td colspan="9" class="e1-empty">No hay comprobantes en este filtro.</td></tr>`}
        </tbody>
      </table>

      <div class="e1-list">
        ${verDif ? dif.map(tarjetaMovil).join("") : ""}
        ${verOk && ok.length ? `<details class="e1-more"${filtro === "ok" ? " open" : ""}>
          <summary><span>${ic("check")}${ok.length} comprobante${ok.length === 1 ? " cuadra" : "s cuadran"}</span><small>Ver lista</small></summary>
          <ul>${ok.map(r => `<li><span>${docDe(r)}</span><span>${imp(r.compras?.total)}</span></li>`).join("")}</ul>
        </details>` : ""}
        ${(verDif ? dif.length : 0) + (verOk ? ok.length : 0) ? "" : `<p class="e1-empty">No hay comprobantes en este filtro.</p>`}
      </div>
    </section>

    <p class="e1-foot">${ic("info")}<span>«Conciliar» solo cruza tus dos archivos dentro de esta demo. Revisar un caso no presenta nada a SUNAT. Sin clave SOL.</span></p>`;
}

// ---------- H3 · 3.1-B Revisar caso: Confirmar / Observar ----------
const ACCION = { CONFIRMADO: ["ok", "check", "Confirmado"], OBSERVADO: ["x", "alert", "Observado"] };
function insignia(r) {
  if (!r.revision) return `<span class="e1-rev">${ic("info")}Pendiente</span>`;
  const [cls, icono, texto] = ACCION[r.revision.accion];
  return `<span class="e1-rev ${cls}">${ic(icono)}${texto} · ${esc(fechaTexto(r.revision.fecha))}</span>`;
}

function abrirCaso(id) {
  const r = conciliacion?.resultados?.find(x => x.id === id);
  if (!r || r.resultado === "CONCILIADO") return;
  const [cls, icono, texto] = ESTADOS[r.resultado];
  const campos = r.diferencias.length ? r.diferencias.map(d => d.campo) : Object.keys(CAMPOS);
  const dlg = $("dlg-caso");
  dlg.innerHTML = `<form method="dialog" class="e1-dlg-in">
    <div class="e1-item-top"><h3 id="dlg-titulo" class="mono">${docDe(r)}</h3><span class="e1-st ${cls}">${ic(icono)}${texto}</span></div>
    <p class="e1-info">${esc(r.tipo_comprobante)} · RUC ${esc(r.ruc_proveedor)}</p>
    <p class="causa">${esc(r.causa || "")}</p>
    <div class="cmp"><span class="h">Campo</span><span class="h v">Compras</span><span class="h v">RCE</span><span class="h v">Dif.</span>
      ${campos.map(c => `<span>${CAMPOS[c]}</span><span class="v">${valor(c, r.compras?.[c])}</span><span class="v r">${valor(c, r.rce?.[c])}</span><span class="v">${delta(c, r.compras?.[c], r.rce?.[c]) || "—"}</span>`).join("")}</div>
    <p>Estado actual: ${insignia(r)}</p>
    ${r.revision?.nota ? `<p class="e1-info">Nota: ${esc(r.revision.nota)}</p>` : ""}
    <label class="e1-lbl" for="nota-caso">Nota (opcional, máx. 300 caracteres)</label>
    <textarea id="nota-caso" maxlength="300" rows="2" placeholder="Ej.: pedir nota de crédito al proveedor"></textarea>
    <p class="e1-info"><b>Confirmar</b>: ya lo revisaste y la diferencia está explicada. <b>Observar</b>: necesita seguimiento (p. ej. con el proveedor).</p>
    <div class="e1-dlg-acts">
      <button type="button" class="e1-btn quiet" data-cerrar>Cerrar</button>
      <button type="button" class="e1-btn sec" data-accion="confirmar" data-caso="${r.id}">${ic("check")}Confirmar</button>
      <button type="button" class="e1-btn t" data-accion="observar" data-caso="${r.id}">${ic("alert")}Observar</button>
    </div>
    <p class="e1-foot">${ic("info")}<span>Solo cambia el estado del caso dentro de esta demo: no presenta nada a SUNAT.</span></p>
  </form>`;
  if (!dlg.open) dlg.showModal();
}
$("dlg-caso").addEventListener("close", () => { if (/^#caso-/.test(location.hash)) history.replaceState(null, "", location.pathname + location.search); });

function abrirDesdeHash() {
  const m = location.hash.match(/^#caso-(\d+)$/);
  if (m) abrirCaso(Number(m[1]));
}

async function guardarAccion(id, accion) {
  const nota = $("nota-caso")?.value || null;
  const r = await fetch(`/api/casos/${id}/estado`, { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ accion, nota }) }).catch(() => null);
  const d = await r?.json().catch(() => ({}));
  if (!r?.ok) { alert((d?.errores || ["No hay conexión con el servidor."]).join(" ")); return; }
  conciliacion.resultados.find(x => x.id === id).revision = d;
  evento("accion_caso", accion);
  $("dlg-caso").close();
  pintar();
  avisarAccion(conciliacion.resultados.find(x => x.id === id), d);
}

// C04 (feedback E3-03): la acción se ve registrada y queda claro que no es una gestión ante SUNAT
let temporizadorAccion;
function avisarAccion(r, revision) {
  const caja = $("estado-accion");
  caja.textContent = `✓ ${r.serie}-${r.numero} quedó ${revision.accion === "OBSERVADO" ? "observado" : "confirmado"} y guardado `
    + `en Yupana.`;
  caja.hidden = false;
  clearTimeout(temporizadorAccion);
  temporizadorAccion = setTimeout(() => { caja.hidden = true; }, 8000);
}

// ---------- H5 · Sesión y API ----------
let yo = null;
async function api(url, op = {}) {
  const r = await fetch(url, { ...op, headers: { "Content-Type": "application/json" } }).catch(() => null);
  if (r?.status === 401) { location.href = "/login.html"; throw new Error("Inicia sesión."); }
  const d = r?.status === 204 ? {} : await r?.json().catch(() => ({}));
  if (!r?.ok) throw new Error((d?.errores || ["No hay conexión con el servidor."]).join(" "));
  return d;
}
$("salir").addEventListener("click", async () => { await fetch("/api/logout", { method: "POST" }); location.href = "/login.html"; });

// ---------- H5 · Chat simulado tipo WhatsApp ----------
let chat = { telefono: null, mensajes: [] };
let resumenPara = null;   // conciliación cuyo resumen está en el borrador
async function cargarChat() { chat = await api("/api/mensajes").catch(() => chat); pintarChat(); }

// Panel lateral: se abre desde la barra o desde «Enviar resumen»; el número cuenta mensajes no vistos
const claveVistos = () => "yupana_chat_vistos_" + (yo?.empresa?.id ?? "");
function abrirChat() {
  $("chat-panel").hidden = false;
  pintarChat();
  $("ch-texto")?.focus();
}
function cerrarChat() { $("chat-panel").hidden = true; }
$("btn-chat").addEventListener("click", () => $("chat-panel").hidden ? abrirChat() : cerrarChat());
addEventListener("keydown", e => { if (e.key === "Escape" && !$("chat-panel").hidden && !document.querySelector("dialog[open]")) cerrarChat(); });

/** Borrador con los casos pendientes de la última conciliación; el estudio lo revisa antes de enviarlo. */
function insertarResumen() {
  const c = conciliacion;
  if (!c?.id) return;
  const pend = c.resultados.filter(r => r.resultado !== "CONCILIADO" && r.revision?.accion !== "CONFIRMADO");
  // El chat admite 1000 caracteres: una línea corta por caso y se agregan mientras quepan
  const motivo = r => r.resultado === "SOLO_COMPRAS" ? "no figura en el RCE"
    : r.resultado === "SOLO_RCE" ? "no figura en tus compras"
    : "difiere " + r.diferencias.map(d => (CAMPOS[d.campo] || d.campo).replace(/^(?!IGV)\w/, l => l.toLowerCase())).join(", ").replace(/, ([^,]*)$/, " y $1");
  const cabeza = `*Casos por revisar · periodo ${c.periodo}*\n`
    + `De ${c.total} comprobantes, ${c.conciliados} cuadran y ${c.con_diferencia} no cuadran con la propuesta del RCE.\n\n`;
  const pie = `\n\nIGV bajo revisión (referencial): ${soles(c.igv_revision)}\nResponde a este chat si tienes el comprobante corregido.`;
  const lineas = [];
  for (const [k, r] of pend.entries()) {
    const linea = `· ${r.serie}-${r.numero}: ${motivo(r)}`, resto = pend.length - k - 1;
    const cola = resto ? `\n· …y ${resto} más` : "";
    if ((cabeza + [...lineas, linea].join("\n") + cola + pie).length > 1000) { lineas.push(`· …y ${pend.length - k} más`); break; }
    lineas.push(linea);
  }
  const texto = cabeza + (pend.length ? lineas.join("\n") : "Todos los casos ya fueron revisados.") + pie;
  abrirChat();
  $("ch-texto").value = texto;
  resumenPara = c.id;
  $("ch-texto").focus();
}

// *negrita* como en WhatsApp; el resto se escapa
const textoChat = t => esc(t).replace(/\*([^*\n]+)\*/g, "<b>$1</b>");

function pintarChat() {
  if (!yo) return;
  const estudio = yo.rol === "ESTUDIO";
  const ultimo = [...chat.mensajes].reverse().find(m => m.direccion === (estudio ? "SALIENTE" : "ENTRANTE"));
  const borrador = $("ch-texto")?.value || "";
  // Mensajes no vistos: con el panel abierto se marcan como vistos
  let vistosChat = 0;
  try { vistosChat = Number(localStorage.getItem(claveVistos()) || 0); } catch {}
  if (!$("chat-panel").hidden) { vistosChat = chat.mensajes.length; try { localStorage.setItem(claveVistos(), vistosChat); } catch {} }
  const nuevos = Math.max(0, chat.mensajes.length - vistosChat);
  $("chat-badge").hidden = !nuevos;
  $("chat-badge").textContent = nuevos;
  $("chat").innerHTML = `<div class="ch-caja">
    <div class="ch-cab"><div><b>${esc(estudio ? yo.empresa.razon_social : "Estudio contable")}</b>
      <span>${estudio ? (chat.telefono ? "+" + esc(chat.telefono) : "sin número de WhatsApp") : "tu estudio contable"} · chat interno</span></div>
      <button class="e1-btn quiet" type="button" data-chat-actualizar>Actualizar</button></div>
    <div class="ch-lista" id="ch-lista">${chat.mensajes.map(m => {
      const mio = (m.direccion === "SALIENTE") === estudio;
      return `<div class="ch-msg ${mio ? "mio" : ""}"><div class="ch-txt">${textoChat(m.texto)}</div>
        <span class="ch-hora">${esc(m.usuario || "")} · ${esc(fechaTexto(m.fecha))}</span></div>`; }).join("") || `<p class="e1-info">Aún no hay mensajes.</p>`}</div>
    <form class="ch-form" id="ch-form"><label class="sr-only" for="ch-texto">Mensaje</label>
      <textarea id="ch-texto" rows="2" maxlength="1000" placeholder="Escribe un mensaje">${esc(borrador)}</textarea>
      <button class="e1-btn t" type="submit">Enviar</button></form>
    <div class="ch-acts">
      ${estudio && conciliacion?.id ? `<button class="e1-btn sec" type="button" data-resumen>Insertar resumen de casos</button>` : ""}
      ${estudio && ultimo && chat.telefono ? `<a class="e1-btn sec" target="_blank" rel="noopener" data-wame
        href="https://wa.me/${chat.telefono}?text=${encodeURIComponent(ultimo.texto)}">Abrir en WhatsApp</a>` : ""}
      ${estudio && !chat.telefono ? `<span class="e1-info">Esta empresa no tiene número: el enlace wa.me necesita uno.</span>` : ""}
    </div></div>`;
  const l = $("ch-lista");
  l.scrollTop = l.scrollHeight;
}

document.addEventListener("submit", async e => {
  if (e.target.id !== "ch-form") return;
  e.preventDefault();
  const texto = $("ch-texto").value;
  if (!texto.trim()) return;
  try {
    chat.mensajes.push(await api("/api/mensajes", { method: "POST", body: JSON.stringify({ texto }) }));
    if (resumenPara) { resumenPara = null; evento("whatsapp_enviado"); }
    $("ch-texto").value = "";
    pintarChat();
  } catch (err) { alert(err.message); }
});

document.addEventListener("click", async e => {
  if (e.target.closest("[data-chat-actualizar]")) cargarChat();
  if (e.target.closest("[data-wame]")) evento("wa_me_abierto");
  if (e.target.closest("[data-resumen]")) insertarResumen();
  if (e.target.closest("[data-chat-cerrar]")) cerrarChat();
});

renderTarjetas();
pintar();
evento("demo_abierta");
fetch("/api/yo").then(r => { if (r.status === 401) location.href = "/login.html"; return r.ok ? r.json() : null; }).then(y => {
  if (!y) return;
  if (!y.empresa) { location.href = "/panel.html"; return; }
  yo = y;
  if (y.demo) {
    document.querySelectorAll("[data-solo-demo]").forEach(el => { el.hidden = false; });
  } else {
    const periodoUrl = new URLSearchParams(location.search).get("periodo");
    const hoy = new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Lima", year: "numeric", month: "2-digit" });
    periodoCarga = /^[1-9][0-9]{3}-(0[1-9]|1[0-2])$/.test(periodoUrl) ? periodoUrl : hoy.format(new Date());
    const selector = $("periodo-carga");
    selector.value = periodoCarga;
    selector.addEventListener("change", () => {
      if (!selector.validity.valid) { selector.value = periodoCarga; return; }
      const url = new URL(location.href);
      url.searchParams.set("periodo", selector.value);
      url.hash = "";
      location.assign(url.href);
    });
    $("selector-periodo").hidden = false;
  }
  document.body.classList.toggle("rol-mype", y.rol === "MYPE");
  if (y.rol === "MYPE") {
    document.querySelector(".e1-intro .e1-h1").textContent = "Tus casos por revisar";
    document.querySelector(".e1-intro .e1-sub").textContent = "Tu estudio contable cruzó tus compras con la propuesta del RCE. "
      + "Abre cada caso que no cuadra, confírmalo u obsérvalo, y escríbele por el chat si necesitas algo.";
  }
  $("usuario").textContent = y.usuario;
  $("empresa-nombre").textContent = `${y.empresa.razon_social} · RUC ${y.empresa.ruc}`;
  cargarChat();
// Persistencia: al recargar se consulta lo guardado en la BD
return Promise.all([
  api(conPeriodo("/api/cargas")),
  api(conPeriodo("/api/conciliaciones/ultima")),
]).then(([d, conc]) => {
  for (const [o, det] of Object.entries(d)) {
    if (ultimas[o]) continue; // el usuario ya cargó algo mientras llegaba esta respuesta: no pisarlo
    ultimas[o] = det;
    if (estado[o].fase === "vacia") estado[o] = { fase: "ok", detalle: det };
  }
  conciliacion = conc;
  filtro = filtroInicial(conc);
  pintar();
  abrirDesdeHash();
}).catch(err => {
  $("estado-periodo").textContent = err.message;
}).finally(() => { $("periodo-carga").disabled = false; });
}).catch(() => {});
addEventListener("hashchange", abrirDesdeHash);
