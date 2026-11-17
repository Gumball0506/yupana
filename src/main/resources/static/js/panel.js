const $ = id => document.getElementById(id);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c]));
const soles = n => "S/ " + Number(n || 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const ic = n => `<svg class="ic" aria-hidden="true"><use href="#e-${n}"></use></svg>`;
const MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic"];
const fechaTexto = iso => { const [f, h = ""] = iso.split("T"); const [a, m, d] = f.split("-");
  return `${Number(d)} ${MESES[m - 1]} ${a}${h ? ", " + h.slice(0, 5) : ""}`; };
const PESOS = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
const PREFIJO = "209999100";

async function api(url, opciones = {}) {
  const r = await fetch(url, { ...opciones, headers: { "Content-Type": "application/json" } }).catch(() => null);
  if (r?.status === 401) { location.href = "/login.html"; throw new Error("sin sesión"); }
  const d = r?.status === 204 ? {} : await r?.json().catch(() => ({}));
  if (!r?.ok) throw new Error((d?.errores || ["No hay conexión con el servidor."]).join(" "));
  return d;
}
const mensaje = (id, texto, tipo = "err") => { $(id).innerHTML = `<p class="e1-msg ${tipo}"></p>`; $(id).firstChild.textContent = texto; };

let empresas = [];

async function cargar() {
  empresas = await api("/api/empresas");
  const pend = empresas.reduce((s, e) => s + (e.conciliacion?.pendientes || 0), 0);
  const igv = empresas.reduce((s, e) => s + Number(e.conciliacion?.igv_revision || 0), 0);
  $("totales").innerHTML = `
    <div class="e1-stat"><b>${empresas.length}</b><span>Empresas</span></div>
    <div class="e1-stat"><b>${empresas.filter(e => e.conciliacion).length}</b><span>Con conciliación</span></div>
    <div class="e1-stat"><b>${pend}</b><span>Casos pendientes de revisión</span></div>
    <div class="e1-stat"><b>${soles(igv)}</b><span>IGV bajo revisión (demo), suma</span></div>`;
  $("empresas").innerHTML = `<div class="e1-scroll"><table class="e1-data pn-tabla">
    <thead><tr><th>Empresa</th><th>RUC</th><th>Periodo</th><th class="n">Comprobantes</th><th class="n">No cuadran</th><th class="n">Pendientes</th>
      <th class="n">IGV bajo revisión</th><th>Último mensaje</th><th>Acceso MYPE</th><th></th></tr></thead>
    <tbody>${empresas.map(e => { const c = e.conciliacion; return `<tr class="${e.activa ? "" : "pn-inactiva"}">
      <td class="pn-nombre" data-th="Empresa">${esc(e.razon_social)}${e.activa ? "" : ` <span class="e1-rev">Desactivada</span>`}</td><td data-th="RUC">${esc(e.ruc)}</td>
      <td data-th="Periodo">${c ? esc(c.periodo) : `<span class="e1-rev">${e.cargas ? "Sin conciliar" : "Sin cargas"}</span>`}</td>
      <td class="n" data-th="Comprobantes">${c ? c.total : "—"}</td><td class="n" data-th="No cuadran">${c ? c.con_diferencia : "—"}</td>
      <td class="n" data-th="Pendientes">${c ? (c.pendientes ? `<span class="e1-st x">${ic("alert")}${c.pendientes}</span>` : `<span class="e1-st ok">${ic("check")}0</span>`) : "—"}</td>
      <td class="n" data-th="IGV bajo revisión">${c ? soles(c.igv_revision) : "—"}</td>
      <td data-th="Último mensaje">${e.ultimo_mensaje ? esc(fechaTexto(e.ultimo_mensaje)) : "—"}</td>
      <td data-th="Acceso MYPE">${e.accesos.length ? esc(e.accesos.join(", ")) : `<button class="e1-link" data-acceso="${e.id}">Dar acceso</button>`}</td>
      <td class="pn-acciones">${e.activa ? `<button class="e1-btn t chico" data-abrir="${e.id}">Abrir${ic("arrow")}</button>` : ""}
        <button class="e1-link" data-editar="${e.id}">Editar</button>
        <button class="e1-link" data-estado="${e.id}">${e.activa ? "Desactivar" : "Reactivar"}</button></td></tr>`; }).join("")}</tbody></table></div>
    <p class="e1-info">«IGV bajo revisión» es una cifra indicativa sujeta a revisión profesional: no es crédito fiscal perdido ni recuperado.</p>`;
  leccion();
}

// ---------- Lección: dígito verificador del RUC (módulo 11) ----------
function digito(diez) {
  const suma = [...diez].reduce((s, c, i) => s + Number(c) * PESOS[i], 0);
  const r = 11 - (suma % 11);
  return { suma, resto: suma % 11, r, dv: r === 10 ? 0 : r === 11 ? 1 : r };
}

function leccion() {
  const ruc = $("ruc").value.replace(/\D/g, "");
  const base = (ruc + "__________").slice(0, 10);
  const completo = /^\d{10}/.test(ruc);
  const d = completo ? digito(ruc.slice(0, 10)) : null;
  const filas = [...base].map((c, i) => `<tr><td>${i + 1}</td><td>${c === "_" ? "·" : c}</td><td>× ${PESOS[i]}</td><td class="n">${c === "_" ? "" : Number(c) * PESOS[i]}</td></tr>`).join("");
  let veredicto = `<p class="e1-info">Escribe los 11 dígitos para ver el cálculo completo.</p>`;
  if (d) {
    const dado = ruc.length === 11 ? Number(ruc[10]) : null;
    const ficticio = ruc.startsWith(PREFIJO);
    veredicto = `<ol class="pn-pasos">
      <li>Suma de productos = <b>${d.suma}</b></li>
      <li>${d.suma} mód 11 = <b>${d.resto}</b></li>
      <li>11 − ${d.resto} = <b>${d.r}</b>${d.r >= 10 ? ` → ${d.r === 10 ? "10 se vuelve 0" : "11 se vuelve 1"}` : ""}</li>
      <li>Dígito verificador esperado: <b class="mono">${d.dv}</b></li></ol>
      ${dado === null ? "" : dado === d.dv
        ? `<p class="e1-msg ok">${ic("check")} El 11.º dígito (${dado}) coincide: el RUC es válido${ficticio ? " y ficticio." : ", pero <b>no es ficticio</b>: la demo no lo acepta."}</p>`
        : `<p class="e1-msg err">${ic("x")} El 11.º dígito es ${dado}, debería ser ${d.dv}: RUC inválido.</p>`}
      <p class="e1-info">Los 2 primeros dígitos indican el tipo: 10 = persona natural, 20 = persona jurídica.</p>`;
  }
  $("leccion").innerHTML = `<p class="e1-info">Cada uno de los 10 primeros dígitos se multiplica por un peso fijo:</p>
    <table class="e1-data pn-calc"><thead><tr><th>#</th><th>Dígito</th><th>Peso</th><th class="n">Producto</th></tr></thead><tbody>${filas}</tbody></table>${veredicto}`;
}
$("ruc").addEventListener("input", leccion);

$("ruc-libre").addEventListener("click", () => {
  const usados = new Set(empresas.map(e => e.ruc));
  for (let i = 0; i < 10; i++) {
    const diez = PREFIJO + i, ruc = diez + digito(diez).dv;
    if (!usados.has(ruc)) { $("ruc").value = ruc; leccion(); return; }
  }
  mensaje("msg-empresa", "Ya se usaron los 10 RUC ficticios válidos con el prefijo 209999100.");
});

$("form-empresa").addEventListener("submit", async e => {
  e.preventDefault();
  try {
    await api("/api/empresas", { method: "POST", body: JSON.stringify({ ruc: $("ruc").value.trim(), razon_social: $("razon").value, telefono: $("telefono").value }) });
    evento("empresa_creada");
    $("form-empresa").reset();
    mensaje("msg-empresa", "Empresa creada. Ábrela para cargar sus compras o traer la propuesta del SIRE (simulador).", "ok");
    await cargar();
  } catch (err) { mensaje("msg-empresa", err.message); }
});

// ---------- Abrir empresa y dar acceso ----------
let accesoPara = null;
let editando = null;
document.addEventListener("click", async e => {
  const a = e.target.closest("[data-abrir]");
  if (a) {
    try { await api(`/api/empresas/${a.dataset.abrir}/activar`, { method: "POST" }); location.href = "/demo.html"; }
    catch (err) { alert(err.message); }
  }
  const ac = e.target.closest("[data-acceso]");
  if (ac) {
    accesoPara = Number(ac.dataset.acceso);
    $("acc-empresa").textContent = empresas.find(x => x.id === accesoPara).razon_social;
    $("msg-acceso").innerHTML = "";
    $("form-acceso").reset();
    $("dlg-acceso").showModal();
  }
  if (e.target.closest("[data-cerrar]")) $("dlg-acceso").close();
  const ed = e.target.closest("[data-editar]");
  if (ed) {
    editando = empresas.find(x => x.id === Number(ed.dataset.editar));
    $("edit-ruc").textContent = "RUC " + editando.ruc;
    $("edit-razon").value = editando.razon_social;
    $("edit-telefono").value = editando.telefono || "";
    $("msg-editar").innerHTML = "";
    $("dlg-editar").showModal();
  }
  if (e.target.closest("[data-cerrar-editar]")) $("dlg-editar").close();
  const es = e.target.closest("[data-estado]");
  if (es) {
    const emp = empresas.find(x => x.id === Number(es.dataset.estado));
    const accion = emp.activa ? "desactivar" : "reactivar";
    const aviso = emp.activa ? `¿Desactivar «${emp.razon_social}»? No se borra nada; su MYPE no podrá ingresar hasta que la reactives.` : `¿Reactivar «${emp.razon_social}»?`;
    if (confirm(aviso)) {
      try { await api(`/api/empresas/${emp.id}/${accion}`, { method: "POST" }); await cargar(); }
      catch (err) { alert(err.message); }
    }
  }
});
$("form-editar").addEventListener("submit", async e => {
  e.preventDefault();
  try {
    await api(`/api/empresas/${editando.id}`, { method: "PUT", body: JSON.stringify({ razon_social: $("edit-razon").value.trim(), telefono: $("edit-telefono").value.trim() || null }) });
    $("dlg-editar").close();
    await cargar();
  } catch (err) { mensaje("msg-editar", err.message); }
});
$("form-acceso").addEventListener("submit", async e => {
  e.preventDefault();
  try {
    await api(`/api/empresas/${accesoPara}/accesos`, { method: "POST", body: JSON.stringify({ usuario: $("acc-usuario").value.trim(), clave: $("acc-clave").value }) });
    $("dlg-acceso").close();
    await cargar();
  } catch (err) { mensaje("msg-acceso", err.message); }
});

$("salir").addEventListener("click", async () => { await fetch("/api/logout", { method: "POST" }); location.href = "/login.html"; });

// Eventos anónimos (Marketing 5.0)
let sesion;
try { sesion = sessionStorage.getItem("yupana_sesion"); } catch {}
if (!sesion) { sesion = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(16).slice(2).padEnd(12, "0"); try { sessionStorage.setItem("yupana_sesion", sesion); } catch {} }
function evento(tipo, detalle) {
  fetch("/api/eventos", { method: "POST", headers: { "Content-Type": "application/json" }, keepalive: true, body: JSON.stringify({ sesion, tipo, detalle }) }).catch(() => {});
}

api("/api/yo").then(yo => {
  if (yo.rol !== "ESTUDIO") { location.href = "/demo.html"; return; }
  $("usuario").textContent = yo.usuario;
  cargar();
}).catch(() => {});
leccion();
