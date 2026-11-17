const ORIGENES = {
  compras: { nombre: "Compras de la empresa", desc: "Tu registro interno de compras", ejemplo: "yupana_compras_demo.csv" },
  rce: { nombre: "Propuesta RCE (simulada)", desc: "RCE = Registro de Compras Electrónico que propone SUNAT; aquí, un archivo ficticio", ejemplo: "yupana_rce_demo.csv",
         faltantes: "yupana_rce_demo_faltantes.csv" },
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
const ultimas = {};   // última importación guardada por origen
const vistos = {};    // importación mostrada en la vista previa (última o una del historial)

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
  else if (archivo.size > MAX_BYTES) error = "El archivo supera 2 MB, límite de la demo.";
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

  $("archivo-" + o).innerHTML = e.archivo && e.fase !== "ok"
    ? `<div class="e1-file"><span>${esc(e.archivo.name)} · ${kb(e.archivo.size)}</span>
         ${e.fase === "subiendo" ? "" : `<button type="button" class="e1-btn sec" data-quitar="${o}">Quitar</button>`}</div>`
    : "";

  let html = "";
  if (e.fase === "ok" && e.detalle) {
    html += `<div class="e1-msg ok">${esc(e.detalle.nombre_archivo)} guardado como importación #${e.detalle.id}.</div>`;
    if (e.detalle.advertencias?.length)
      html += `<div class="e1-msg warn"><strong>Revisa:</strong><ul>${e.detalle.advertencias.map(a => `<li>${esc(a)}</li>`).join("")}</ul></div>`;
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
  const p = e.target.closest("[data-plantilla]");
  if (p) evento("plantilla_descargada", p.dataset.plantilla);
  const v = e.target.closest("[data-ver]");
  if (v) verImportacion(Number(v.dataset.ver));
  const u = e.target.closest("[data-ultima]");
  if (u) { vistos[u.dataset.ultima] = ultimas[u.dataset.ultima]; pintarPrevias(); }
  const f = e.target.closest("[data-filtro]");
  if (f) { filtro = f.dataset.filtro; pintarPanel(); }
  if (e.target.closest("[data-conciliar]")) conciliar();
  if (e.target.closest("[data-exportar]")) evento("resultado_exportado");
  const rv = e.target.closest("[data-revisar]");
  if (rv) abrirCaso(Number(rv.dataset.revisar));
  const ac = e.target.closest("[data-accion]");
  if (ac) guardarAccion(Number(ac.dataset.caso), ac.dataset.accion);
  if (e.target.closest("[data-cerrar]")) $("dlg-caso").close();
  if (e.target.closest("[data-aviso]")) simularAviso();
  const fb = e.target.closest("[data-fb]");
  if (fb) { respuestaFb = fb.dataset.fb; pintarFeedback(); }
  if (e.target.closest("[data-enviar-fb]")) enviarFeedback();
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
  try {
    const r = await fetch("/api/cargas", { method: "POST", body: fd });
    const d = await r.json().catch(() => ({}));
    if (r.ok) {
      ultimas[o] = vistos[o] = d;
      estado[o] = { fase: "ok", detalle: d };
      evento("carga_exitosa", o);
    } else {
      const errores = d.errores || [r.status === 413 ? "El archivo supera 2 MB, límite de la demo."
                                                      : "Error inesperado del servidor (" + r.status + ")."];
      estado[o] = { fase: "error", archivo: e.archivo, errores, celdas: d.celdas || [], vista: d.vista };
      evento("carga_fallida", motivo(errores));
    }
  } catch {
    estado[o] = { fase: "error", archivo: e.archivo, errores: ["No hay conexión con el servidor."], celdas: [] };
  }
}

async function cargarListas() {
  $("btn-cargar").disabled = true;
  for (const o of Object.keys(ORIGENES)) if (estado[o].fase === "lista") await subir(o);
  pintar();
  refrescarHistorial();
  pintarSire();
}
$("btn-cargar").addEventListener("click", cargarListas);

async function usarEjemplos(faltantes) {
  evento("ejemplos_usados", faltantes ? "faltantes" : undefined);
  for (const [o, def] of Object.entries(ORIGENES)) {
    const nombre = faltantes && def.faltantes || def.ejemplo;
    const blob = await (await fetch("/ejemplos/" + nombre)).blob();
    estado[o] = { fase: "lista", archivo: new File([blob], nombre, { type: "text/csv" }) };
  }
  pintar();
  await cargarListas();
  // Siguiente paso a la vista: en la demo en vivo no hay que buscar el botón «Conciliar»
  $("btn-conciliar")?.focus({ preventScroll: true });
  $("conciliacion")?.scrollIntoView({ behavior: "smooth" });
}
$("btn-ejemplos").addEventListener("click", () => usarEjemplos(false));
$("btn-faltantes").addEventListener("click", () => usarEjemplos(true));

$("btn-reiniciar").addEventListener("click", async () => {
  if (!confirm("¿Borrar todas las cargas de la demo? Esta acción no se puede deshacer.")) return;
  const r = await fetch("/api/cargas", { method: "DELETE" }).catch(() => null);
  if (!r?.ok) { alert("No se pudo reiniciar la demo: revisa que el servidor siga encendido."); return; }
  evento("demo_reiniciada");
  marcarLimpio();
  for (const o of Object.keys(ORIGENES)) { estado[o] = { fase: "vacia" }; delete ultimas[o]; delete vistos[o]; }
  conciliacion = null;
  $("panel").innerHTML = "";
  avisos = [];
  chat.mensajes = [];
  pintarChat();
  pintarSire();
  pintar();
  refrescarHistorial();
});

// ---------- Vista previa ----------
function pintarPrevias() {
  const html = Object.keys(ORIGENES).filter(o => vistos[o]).map(o => {
    const d = vistos[o], s = d.resumen;
    const esAnterior = ultimas[o] && d.id !== ultimas[o].id;
    return `<section class="e1-prev" id="prev-${o}">
      <div class="e1-card-head" style="margin-bottom:0">
        <h3>${esc(d.origen_nombre)}</h3>
        ${esAnterior ? `<span class="e1-old">${ic("alert")}Viendo una importación anterior · <button class="e1-link" data-ultima="${o}">volver a la última</button></span>` : ""}
      </div>
      <p class="e1-info">Importación #${d.id} · ${esc(d.nombre_archivo)} · ${esc(fechaTexto(d.fecha))} · mostrando ${d.filas.length} de ${d.total_filas}</p>
      <div class="e1-stats">
        <div class="e1-stat"><b>${s.comprobantes}</b><span>Comprobantes</span></div>
        <div class="e1-stat"><b class="chico">${esc(s.desde)} → ${esc(s.hasta)}</b><span>Rango de fechas</span></div>
        <div class="e1-stat"><b>${soles(s.base_imponible)}</b><span>Base imponible</span></div>
        <div class="e1-stat"><b>${soles(s.igv)}</b><span>IGV</span></div>
        <div class="e1-stat"><b>${soles(s.total)}</b><span>Total</span></div>
      </div>
      <div class="e1-scroll"><table class="e1-data">
        <thead><tr>${COLS.map(c => `<th class="${NUM.includes(c) ? "n" : ""}">${c}</th>`).join("")}</tr></thead>
        <tbody>${d.filas.map(f => `<tr>${COLS.map(c =>
          NUM.includes(c) ? `<td class="n">${imp(f[c])}</td>` : `<td>${esc(f[c])}</td>`).join("")}</tr>`).join("")}</tbody>
      </table></div></section>`;
  }).join("");
  $("previas").innerHTML = html || `<p class="e1-info">Aún no hay datos cargados.</p>`;
}

// ---------- Historial ----------
async function refrescarHistorial() {
  if (!$("det-historial").open) return;
  const h = await fetch("/api/cargas/historial").then(r => r.json()).catch(() => []);
  $("historial").innerHTML = h.length ? `<div class="e1-scroll"><table class="e1-data">
    <thead><tr><th>#</th><th>Origen</th><th>Archivo</th><th>Fecha</th><th class="n">Filas</th><th></th></tr></thead>
    <tbody>${h.map(i => `<tr><td>${i.id}</td><td>${esc(ORIGENES[i.origen].nombre)}</td><td>${esc(i.nombre_archivo)}</td>
      <td>${esc(i.fecha.replace("T", " "))}</td><td class="n">${i.total_filas}</td>
      <td><button class="e1-link" data-ver="${i.id}">Ver</button></td></tr>`).join("")}</tbody></table></div>`
    : `<p class="e1-info">Aún no hay importaciones.</p>`;
}
$("det-historial").addEventListener("toggle", () => { if ($("det-historial").open) { evento("historial_visto"); refrescarHistorial(); } });

async function verImportacion(id) {
  const d = await fetch("/api/cargas/" + id).then(r => r.json());
  vistos[d.origen] = d;
  pintarPrevias();
  $("prev-" + d.origen).scrollIntoView({ behavior: "smooth" });
}

// ---------- Métricas Marketing 5.0 ----------
const NOMBRES_EVENTO = {
  demo_abierta: "Demo abierta", carga_iniciada: "Cargas iniciadas", carga_exitosa: "Cargas exitosas",
  carga_fallida: "Cargas fallidas", ejemplos_usados: "Usó archivos de ejemplo", plantilla_descargada: "Descargó plantilla",
  historial_visto: "Abrió historial", demo_reiniciada: "Reinició demo", panel_visto: "Vio el panel de conciliación", resultado_exportado: "Exportó el resultado (CSV)",
  accion_caso: "Confirmó u observó un caso", aviso_simulado: "Simuló el aviso del día", feedback_recibido: "Dejó feedback",
  ensayo_registrado: "Ensayos registrados", login: "Ingresos",
  empresa_creada: "Empresas creadas", sire_propuesta: "Propuestas SIRE pedidas (simulador)", sire_importada: "Propuestas SIRE importadas",
  whatsapp_enviado: "Avisos enviados al chat", wa_me_abierto: "Abrió WhatsApp (wa.me)", cta_probar_demo: "Clic en «Probar demo» (landing)",
};
$("det-metricas").addEventListener("toggle", async () => {
  if (!$("det-metricas").open) return;
  const m = await fetch("/api/eventos/resumen").then(r => r.json()).catch(() => null);
  if (!m) return;
  const ev = m.eventos, ini = ev.carga_iniciada || 0, ok = ev.carga_exitosa || 0;
  const motivos = Object.entries(m.motivos_fallo);
  $("metricas").innerHTML = `
    <div class="e1-stats">
      <div class="e1-stat"><b>${m.sesiones}</b><span>Sesiones anónimas</span></div>
      <div class="e1-stat"><b>${ini ? Math.round(ok * 100 / ini) + " %" : "—"}</b><span>Éxito de carga (${ok}/${ini})</span></div>
      ${Object.entries(NOMBRES_EVENTO).map(([k, n]) => `<div class="e1-stat"><b>${ev[k] || 0}</b><span>${n}</span></div>`).join("")}
    </div>
    ${motivos.length ? `<p class="e1-info"><strong>Motivos de fallo:</strong> ${motivos.map(([k, n]) => `${esc(k)} (${n})`).join(" · ")}</p>` : ""}
    ${await pintarFeedbackEquipo()}
    <p class="e1-info">Eventos anónimos: una sesión aleatoria por pestaña; nunca nombres ni contenido de archivos.
      Sirven para decidir mejoras (p. ej. si muchos fallan por «falta_columna», destacar la plantilla).</p>`;
});

// ---------- Pintado general ----------
function pintarPasos(p1, p2, p3, p4) {
  const paso = (fase, n, txt, lg) => `<li class="${fase}"${fase === "now" ? ' aria-current="step"' : ""}>
    <span class="n">${fase === "done" ? ic("check") : n}</span><span>${txt}<span class="lg">${lg}</span></span></li>`;
  $("pasos").innerHTML = paso(p1, 1, "Cargar", " archivos") + paso(p2, 2, "Verificar", " y guardar") + paso(p3, 3, "Conciliar", "") + paso(p4, 4, "Revisar", " casos");
}

function pintar() {
  for (const o of Object.keys(ORIGENES)) pintarTarjeta(o);
  const fases = Object.values(estado).map(e => e.fase);
  $("btn-cargar").disabled = !fases.includes("lista");
  const ambosOk = fases.every(f => f === "ok");
  const ambosElegidos = fases.every(f => ["lista", "subiendo", "ok"].includes(f));
  const vigente = conciliacion?.id && !desactualizada();
  const pendientes = vigente ? conciliacion.resultados.filter(r => r.resultado !== "CONCILIADO" && !r.revision).length : 1;
  pintarPasos(ambosElegidos ? "done" : "now", ambosOk ? "done" : ambosElegidos ? "now" : "", vigente ? "done" : ambosOk ? "now" : "",
    vigente ? (pendientes ? "now" : "done") : "");
  const periodo = conciliacion?.periodo ?? ultimas.compras?.resumen?.desde?.slice(0, 7);
  $("crumb").innerHTML = `<span>Conciliación</span>${periodo ? `<span aria-hidden="true">/</span><b>Periodo ${esc(periodo)}</b>` : ""}`;
  pintarPrevias();
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

async function conciliar() {
  $("btn-conciliar").disabled = true;
  try {
    const r = await fetch("/api/conciliar", { method: "POST" });
    const d = await r.json().catch(() => ({}));
    if (r.ok) { conciliacion = d; filtro = filtroInicial(d); evento("panel_visto"); }
    else { conciliacion = null; $("panel").innerHTML = `<div class="e1-msg err">${esc((d.errores || ["Error al conciliar (" + r.status + ")."]).join(" "))}</div>`; }
  } catch {
    $("panel").innerHTML = `<div class="e1-msg err">No hay conexión con el servidor.</div>`;
  }
  pintar();
  if (conciliacion) $("conciliacion").scrollIntoView({ behavior: "smooth" });
}
$("btn-conciliar").addEventListener("click", conciliar);

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
  return `<tr class="${ok ? "ok" : "dif"}">
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
  pintarAvisos();
  pintarFeedback();
  const c = conciliacion;
  const btn = $("btn-conciliar"), vigente = c?.id && !desactualizada();
  btn.disabled = !(ultimas.compras && ultimas.rce);
  btn.className = "e1-btn " + (vigente ? "sec" : "t");
  btn.innerHTML = (vigente ? "Volver a conciliar" : "Conciliar") + ic("arrow");
  if (!c || !c.id) {
    if (!$("panel").querySelector(".e1-msg.err"))
      $("panel").innerHTML = ultimas.compras && ultimas.rce ? "" : `<p class="e1-info">Carga ambos archivos <a href="#tarjetas">arriba</a> para habilitar la conciliación.</p>`;
    return;
  }
  const rs = c.resultados;
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
      </div>
      <aside class="e1-igv" aria-label="IGV bajo revisión">
        <p class="lab">IGV bajo revisión (demo)</p>
        <p class="amt">${soles(c.igv_revision)}</p>
        <p class="fine">IGV de ${dif.length === 1 ? "el comprobante observado" : `los ${dif.length} comprobantes observados`}. Cifra indicativa sujeta a revisión profesional: no es crédito fiscal perdido ni recuperado.</p>
        <dl><dt>Diferencia de IGV entre fuentes</dt><dd>${soles(c.diferencia_igv)}</dd></dl>
        ${dif.length ? `<p class="fine e1-compara"><b>${soles(c.igv_revision)}</b> es el IGV de los comprobantes por revisar; <b>${soles(c.diferencia_igv)}</b> es cuánto difiere ese IGV entre tu registro y el RCE. Son medidas distintas: no se suman.</p>` : ""}
      </aside>
    </section>

    <div class="e1-map" role="img" aria-label="Los ${rs.length} comprobantes: ${ok.length} cuadran y ${dif.length} tienen diferencia${dif.length ? " (" + dif.map(etiqueta).join(", ") + ")" : ""}">
      ${rs.map(r => `<span${r.resultado !== "CONCILIADO" ? ' class="x"' : ""}>${etiqueta(r)}</span>`).join("")}
    </div>
    <p class="e1-key">${dif.length ? `<span><i class="x"></i><b>Con diferencia:</b> ${dif.map(etiqueta).join(", ")}</span>` : ""}
      ${ok.length ? `<span><i></i><b>Cuadran:</b> ${ok.map(etiqueta).join(", ")}</span>` : ""}</p>

    <section class="e1-sheet" aria-label="Hoja de cuadre">
      <div class="e1-tools">
        <div class="e1-seg" role="group" aria-label="Filtrar">
          ${seg("todos", `Todos (${rs.length})`)}${seg("dif", `Con diferencia (${dif.length})`)}${seg("ok", `Cuadran (${ok.length})`)}
        </div>
        <p class="e1-read">Arriba, tu registro de compras. Debajo, en <b>terracota con «RCE»</b>, lo que propone el RCE.</p>
        <a class="e1-btn sec" href="/api/conciliaciones/${c.id}/csv" download data-exportar>${ic("down")}Exportar CSV</a>
      </div>

      <table class="e1-tbl">
        <thead><tr><th>Estado</th><th>Comprobante</th><th>Proveedor (RUC)</th><th>Fecha de emisión</th><th class="n">Base imponible</th><th class="n">IGV</th><th class="n">Total</th><th class="n">Revisión</th></tr></thead>
        <tbody>
          ${verDif && dif.length ? `<tr class="grp"><td colspan="8">Por revisar · ${dif.length} comprobante${dif.length === 1 ? "" : "s"}</td></tr>${dif.map(filaTabla).join("")}` : ""}
          ${verOk && ok.length ? `<tr class="grp"><td colspan="8">Cuadran · ${ok.length} comprobante${ok.length === 1 ? "" : "s"}, sin acción pendiente</td></tr>${ok.map(filaTabla).join("")}` : ""}
          ${(verDif ? dif.length : 0) + (verOk ? ok.length : 0) ? "" : `<tr><td colspan="8" class="e1-empty">No hay comprobantes en este filtro.</td></tr>`}
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
$("dlg-caso").addEventListener("close", () => { if (/^#caso-/.test(location.hash)) history.replaceState(null, "", location.pathname); });

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
    + `(se mantiene al recargar). Es un estado interno de la demo: no presenta nada a SUNAT.`;
  caja.hidden = false;
  clearTimeout(temporizadorAccion);
  temporizadorAccion = setTimeout(() => { caja.hidden = true; }, 8000);
}

// ---------- H3 · 3.1-A Aviso de demostración (in-app, simulado) ----------
let avisos = [];
async function simularAviso() {
  const r = await fetch("/api/avisos", { method: "POST" }).catch(() => null);
  const d = await r?.json().catch(() => ({}));
  if (!r?.ok) { alert((d?.errores || ["No hay conexión con el servidor."]).join(" ")); return; }
  avisos.unshift(d);
  evento("aviso_simulado");
  pintarAvisos();
  $("avisos").scrollIntoView({ behavior: "smooth" });
}

const correo = a => `<article class="e1-mail">
    <dl><dt>De</dt><dd>avisos@yupana.demo (simulado)</dd><dt>Para</dt><dd>Contador de la empresa demo</dd>
      <dt>Asunto</dt><dd><b>${esc(a.asunto)}</b></dd><dt>Fecha</dt><dd>${esc(fechaTexto(a.fecha))}${conciliacion?.id && a.id_conciliacion !== conciliacion.id ? ` · conciliación #${a.id_conciliacion}` : ""}</dd></dl>
    <div class="cuerpo">${esc(a.cuerpo).replace(/ \/#caso-(\d+)/g, ' <a href="#caso-$1">Ver caso</a>')}</div>
  </article>`;

function pintarAvisos() {
  pintarGuion();
  if (!conciliacion?.id) { $("avisos").innerHTML = ""; return; }
  const [ultimo, ...antes] = avisos;
  $("avisos").innerHTML = `<div class="e1-sec"><h2>Aviso del día</h2>
      <button class="e1-btn sec" data-aviso>${ic("play")}Simular aviso del día</button></div>
    <p class="e1-info">Vista previa del resumen que recibiría el contador. Es una demostración manual: no se envía por correo ni por WhatsApp, ni se programa a diario.</p>
    ${ultimo ? correo(ultimo) : `<p class="e1-info">Aún no hay avisos.</p>`}
    ${antes.length ? `<details class="e1-fold"><summary>Avisos anteriores (${antes.length})</summary><div class="cuerpo">${antes.map(correo).join("")}</div></details>` : ""}`;
}

// ---------- H3 · 3.3 Feedback anónimo ----------
const RESP_FB = { si: "Sí", mas_o_menos: "Más o menos", no: "No" };
let respuestaFb = null, fbEnviado = false;
function pintarFeedback() {
  if (!conciliacion?.id) { $("feedback").innerHTML = ""; return; }
  $("feedback").innerHTML = fbEnviado
    ? `<div class="e1-fb"><p class="e1-msg ok">${ic("check")} Gracias. Tu respuesta es anónima y sirve para mejorar la demo.</p></div>`
    : `<div class="e1-fb"><h3>¿Te quedó claro qué casos revisar y por qué?</h3>
      <div class="e1-seg" role="group" aria-label="Tu respuesta">${Object.entries(RESP_FB).map(([k, t]) =>
        `<button type="button" data-fb="${k}" aria-pressed="${respuestaFb === k}">${t}</button>`).join("")}</div>
      <label class="e1-lbl" for="fb-comentario">Comentario (opcional). No escribas RUC, montos ni datos tributarios reales.</label>
      <textarea id="fb-comentario" maxlength="500" rows="2">${esc($("fb-comentario")?.value || "")}</textarea>
      <button type="button" class="e1-btn" data-enviar-fb ${respuestaFb ? "" : "disabled"}>Enviar</button></div>`;
}

async function enviarFeedback() {
  const r = await fetch("/api/feedback", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sesion, respuesta: respuestaFb, comentario: $("fb-comentario").value || null }) }).catch(() => null);
  if (!r?.ok) { alert("No se pudo enviar el feedback."); return; }
  evento("feedback_recibido", respuestaFb);
  fbEnviado = true;
  pintarFeedback();
}

async function pintarFeedbackEquipo() {
  const f = await fetch("/api/feedback/resumen").then(r => r.json()).catch(() => null);
  if (!f) return "";
  return `<p class="e1-info"><strong>Feedback:</strong> ${Object.entries(f.respuestas).map(([k, n]) => `${RESP_FB[k]} (${n})`).join(" · ")}
      · <a href="/api/feedback/csv" download>Descargar feedback (CSV)</a></p>
    ${f.comentarios.length ? `<ul class="e1-coment">${f.comentarios.map(c => `<li><span>${esc(fechaTexto(c.fecha))} · ${RESP_FB[c.respuesta]}</span>${esc(c.comentario)}</li>`).join("")}</ul>` : ""}`;
}

// ---------- H4 · 4.1-A Guion de ensayo: dos recorridos seguidos sin fallos desde sesión limpia ----------
let limpio = false, persistencia = false, ensayos = 0;
try { limpio = sessionStorage.getItem("yupana_limpio") === "1"; } catch {}
function marcarLimpio() { limpio = true; try { sessionStorage.setItem("yupana_limpio", "1"); } catch {} }

function pasosGuion() {
  const c = conciliacion?.id && !desactualizada() ? conciliacion : null;
  const d3 = c?.resultados.find(r => r.numero === "000003");
  return [
    ["Empezar desde cero («Reiniciar demo» o base vacía)", limpio],
    ["Cargar Compras y Propuesta RCE", !!(ultimas.compras && ultimas.rce)],
    ["Conciliar y ver las excepciones", !!c],
    ["Revisar el caso 000003 y marcar «Observar»", d3?.revision?.accion === "OBSERVADO"],
    ["Simular el aviso del día", !!c && avisos[0]?.id_conciliacion === c.id],
    ["Recargar la página (F5): el caso 000003 sigue observado", persistencia],
  ];
}

function pintarGuion() {
  const pasos = pasosGuion(), hechos = pasos.filter(([, ok]) => ok).length;
  $("guion").innerHTML = `<ol class="e1-guion">${pasos.map(([t, ok], i) =>
      `<li class="${ok ? "ok" : ""}">${ok ? ic("check") : `<span class="n">${i + 1}</span>`}<span>${t}</span></li>`).join("")}</ol>
    <p class="e1-info">${hechos} de ${pasos.length} pasos comprobados en esta pestaña.${ensayos ? ` Ensayos registrados en esta pestaña: ${ensayos}.` : ""}</p>
    <button class="e1-btn sec" data-ensayo>Registrar ensayo (${hechos === pasos.length ? "completo" : "incompleto"})</button>`;
}

document.addEventListener("click", e => {
  if (!e.target.closest("[data-ensayo]")) return;
  const completo = pasosGuion().every(([, ok]) => ok);
  evento("ensayo_registrado", completo ? "completo" : "incompleto");
  ensayos++;
  pintarGuion();
});

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

// ---------- H5 · Simulador SIRE ----------
let sireTicket = null, sireLog = [];
const registrar = (que, d) => { sireLog.unshift(`${new Date().toLocaleTimeString()}  ${que}\n${JSON.stringify(d, null, 2)}`); };

async function pintarSire() {
  if (yo?.rol !== "ESTUDIO") return;
  const periodos = await api("/api/sire/periodos").catch(() => []);
  const tickets = await api("/api/sire/tickets").catch(() => []);
  const t = sireTicket;
  const listo = t && t.estado !== "EN_PROCESO";
  $("sire-cuerpo").innerHTML = `<ol class="sr-pasos">
      <li class="${t ? "ok" : ""}"><span class="n">1</span><div><b>Elige el periodo</b>
        <select id="sire-periodo" class="e1-in">${periodos.map(p => `<option${t?.periodo === p ? " selected" : ""}>${p}</option>`).join("")}</select></div></li>
      <li class="${t ? "ok" : ""}"><span class="n">2</span><div><b>Solicita la propuesta</b><span>El SIRE no responde con el archivo: responde con un ticket y lo prepara en segundo plano.</span>
        <button class="e1-btn sec" data-sire-pedir>Solicitar propuesta</button></div></li>
      <li class="${listo ? "ok" : ""}"><span class="n">3</span><div><b>Consulta el ticket</b>
        <span>${t ? `Ticket <span class="mono">${esc(t.numero_ticket)}</span> · <b>${t.estado === "EN_PROCESO" ? "En proceso…" : "Listo"}</b> · ${t.filas} comprobantes` : "Aún no hay ticket."}</span></div></li>
      <li class="${t?.estado === "IMPORTADO" || t?.estado === "ACEPTADO_SIMULADO" ? "ok" : ""}"><span class="n">4</span><div><b>Descarga e importa</b>
        <span>Se importa como «Propuesta RCE», con las mismas validaciones que un archivo subido a mano.</span>
        ${listo ? `<div class="sr-acts"><a class="e1-btn sec" href="/api/sire/tickets/${t.id}/archivo" download>Descargar archivo</a>
          <button class="e1-btn t" data-sire-importar>Importar como Propuesta RCE</button>
          <button class="e1-btn quiet" data-sire-aceptar>Aceptar propuesta (simulado)</button></div>` : ""}</div></li>
    </ol>
    ${t?.estado === "ACEPTADO_SIMULADO" ? `<p class="e1-msg ok">Propuesta marcada como aceptada <b>solo dentro de la demo</b>. En el SIRE real, aceptarla genera tu Registro de Compras; aquí no se presentó nada a SUNAT.</p>` : ""}
    ${t?.variaciones?.length ? `<div class="e1-msg warn"><strong>Por qué esta propuesta no es igual a tus compras (simulado):</strong>
      <ul>${t.variaciones.map(v => `<li><span class="mono">${esc(v.documento)}</span>: ${esc(v.cambio)}</li>`).join("")}</ul></div>` : ""}
    ${sireLog.length ? `<details class="e1-fold"><summary>Respuestas del simulador (JSON)</summary><pre class="sr-log">${esc(sireLog.join("\n\n"))}</pre></details>` : ""}
    ${tickets.length ? `<details class="e1-fold"><summary>Tickets anteriores (${tickets.length})</summary><div class="e1-scroll"><table class="e1-data">
      <thead><tr><th>Ticket</th><th>Periodo</th><th class="n">Comprobantes</th><th>Estado</th></tr></thead>
      <tbody>${tickets.map(x => `<tr><td>${esc(x.numero_ticket)}</td><td>${esc(x.periodo)}</td><td class="n">${x.filas}</td><td>${esc(x.estado)}</td></tr>`).join("")}</tbody></table></div></details>` : ""}`;
}

async function sirePedir() {
  try {
    const t = await api("/api/sire/propuesta", { method: "POST", body: JSON.stringify({ periodo: $("sire-periodo").value }) });
    registrar("POST /api/sire/propuesta", t);
    sireTicket = t;
    evento("sire_propuesta");
    pintarSire();
    for (let i = 0; i < 15 && sireTicket.estado === "EN_PROCESO"; i++) {
      await new Promise(ok => setTimeout(ok, 1000));
      const n = await api(`/api/sire/tickets/${t.id}`);
      registrar(`GET /api/sire/tickets/${t.id}`, n);
      sireTicket = { ...n, variaciones: t.variaciones };
      pintarSire();
    }
  } catch (err) { alert(err.message); }
}

async function sireImportar() {
  try {
    const d = await api(`/api/sire/tickets/${sireTicket.id}/importar`, { method: "POST" });
    registrar(`POST /api/sire/tickets/${sireTicket.id}/importar`, { id: d.id, nombre_archivo: d.nombre_archivo, total_filas: d.total_filas });
    ultimas.rce = vistos.rce = d;
    estado.rce = { fase: "ok", detalle: d };
    sireTicket.estado = "IMPORTADO";
    evento("sire_importada");
    pintar();
    pintarSire();
    $("btn-conciliar").focus({ preventScroll: true });
    $("conciliacion").scrollIntoView({ behavior: "smooth" });
  } catch (err) { alert(err.message); }
}

async function sireAceptar() {
  try {
    const d = await api(`/api/sire/tickets/${sireTicket.id}/aceptar`, { method: "POST" });
    registrar(`POST /api/sire/tickets/${sireTicket.id}/aceptar`, d);
    sireTicket = { ...d, variaciones: sireTicket.variaciones };
    pintarSire();
  } catch (err) { alert(err.message); }
}

// ---------- H5 · Chat simulado tipo WhatsApp ----------
let chat = { telefono: null, mensajes: [] };
async function cargarChat() { chat = await api("/api/mensajes").catch(() => chat); pintarChat(); }

// *negrita* como en WhatsApp; el resto se escapa
const textoChat = t => esc(t).replace(/\*([^*\n]+)\*/g, "<b>$1</b>");

function pintarChat() {
  if (!yo) return;
  const estudio = yo.rol === "ESTUDIO";
  const ultimo = [...chat.mensajes].reverse().find(m => m.direccion === (estudio ? "SALIENTE" : "ENTRANTE"));
  const borrador = $("ch-texto")?.value || "";
  $("chat").innerHTML = `<div class="ch-caja">
    <div class="ch-cab"><div><b>${esc(estudio ? yo.empresa.razon_social : "Estudio contable")}</b>
      <span>${estudio ? (chat.telefono ? "+" + esc(chat.telefono) : "sin número de WhatsApp") : "tu estudio contable"} · simulado</span></div>
      <button class="e1-btn quiet" data-chat-actualizar>Actualizar</button></div>
    <div class="ch-lista" id="ch-lista">${chat.mensajes.map(m => {
      const mio = (m.direccion === "SALIENTE") === estudio;
      return `<div class="ch-msg ${mio ? "mio" : ""}"><div class="ch-txt">${textoChat(m.texto)}</div>
        <span class="ch-hora">${esc(m.usuario || "")} · ${esc(fechaTexto(m.fecha))}</span></div>`; }).join("") || `<p class="e1-info">Aún no hay mensajes.</p>`}</div>
    <form class="ch-form" id="ch-form"><label class="sr-only" for="ch-texto">Mensaje</label>
      <textarea id="ch-texto" rows="2" maxlength="1000" placeholder="Escribe un mensaje">${esc(borrador)}</textarea>
      <button class="e1-btn t" type="submit">Enviar</button></form>
    <div class="ch-acts">
      ${estudio ? `<button class="e1-btn sec" data-wsp-aviso>Enviar aviso del día al chat</button>` : ""}
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
    $("ch-texto").value = "";
    pintarChat();
  } catch (err) { alert(err.message); }
});

document.addEventListener("click", async e => {
  if (e.target.closest("[data-sire-pedir]")) sirePedir();
  if (e.target.closest("[data-sire-importar]")) sireImportar();
  if (e.target.closest("[data-sire-aceptar]")) sireAceptar();
  if (e.target.closest("[data-chat-actualizar]")) cargarChat();
  if (e.target.closest("[data-wame]")) evento("wa_me_abierto");
  if (e.target.closest("[data-wsp-aviso]")) {
    try {
      chat.mensajes.push(await api("/api/mensajes/aviso", { method: "POST" }));
      evento("whatsapp_enviado");
      pintarChat();
    } catch (err) { alert(err.message); }
  }
});

renderTarjetas();
pintar();
evento("demo_abierta");
fetch("/api/yo").then(r => { if (r.status === 401) location.href = "/login.html"; return r.ok ? r.json() : null; }).then(y => {
  if (!y) return;
  if (!y.empresa) { location.href = "/panel.html"; return; }
  yo = y;
  document.body.classList.toggle("rol-mype", y.rol === "MYPE");
  if (y.rol === "MYPE") {
    document.querySelector(".e1-intro .e1-h1").textContent = "Tus casos por revisar";
    document.querySelector(".e1-intro .e1-sub").textContent = "Tu estudio contable cruzó tus compras con la propuesta del RCE. "
      + "Abre cada caso que no cuadra, confírmalo u obsérvalo, y escríbele por el chat si necesitas algo.";
  }
  $("usuario").textContent = y.usuario;
  $("empresa-nombre").textContent = `${y.empresa.razon_social} · RUC ${y.empresa.ruc}`;
  cargarChat();
  pintarSire();
// Persistencia: al recargar se consulta lo guardado en la BD
return Promise.all([
  fetch("/api/cargas").then(r => r.json()),
  fetch("/api/conciliaciones/ultima").then(r => r.json()),
]).then(([d, conc]) => {
  for (const [o, det] of Object.entries(d)) {
    if (ultimas[o]) continue; // el usuario ya cargó algo mientras llegaba esta respuesta: no pisarlo
    ultimas[o] = vistos[o] = det;
    if (estado[o].fase === "vacia") estado[o] = { fase: "ok", detalle: det };
  }
  conciliacion = conc;
  filtro = filtroInicial(conc);
  if (!Object.keys(d).length) marcarLimpio();
  persistencia = conc?.resultados?.some(r => r.numero === "000003" && r.revision?.accion === "OBSERVADO") ?? false;
  pintar();
  abrirDesdeHash();
  return fetch("/api/avisos").then(r => r.json()).then(a => { avisos = a; pintarAvisos(); });
}).catch(() => {});
}).catch(() => {});
addEventListener("hashchange", abrirDesdeHash);
