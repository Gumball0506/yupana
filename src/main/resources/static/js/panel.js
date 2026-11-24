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
    <div class="e1-stat"><b>${soles(igv)}</b><span>IGV bajo revisión</span></div>`;
  pintarEmpresas();
}

const normalizar = s => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
function pintarEmpresas() {
  const consulta = normalizar($("buscar-empresa").value.trim());
  const visibles = empresas.filter(e => normalizar(e.razon_social).includes(consulta) || e.ruc.includes(consulta));
  $("empresa-estado").textContent = visibles.length
    ? `${visibles.length} ${visibles.length === 1 ? "empresa" : "empresas"}`
    : consulta ? "No encontramos empresas con esa búsqueda." : "Agrega una empresa para empezar.";
  $("empresas").innerHTML = visibles.map(e => {
    const c = e.conciliacion;
    return `<article class="yp-company">
      <header class="yp-company-head">
        <div><h2>${esc(e.razon_social)}</h2><p>RUC ${esc(e.ruc)}</p></div>
        <span class="e1-chip ${e.activa && c ? "ok" : ""}">${!e.activa ? "Desactivada" : c ? esc(c.periodo || "Conciliada") : e.cargas ? "Sin conciliar" : "Sin cargas"}</span>
      </header>
      <dl class="yp-company-metrics">
        <div><dt>Comprobantes</dt><dd>${c?.total ?? "—"}</dd></div>
        <div><dt>No cuadran</dt><dd>${c?.con_diferencia ?? "—"}</dd></div>
        <div><dt>Pendientes</dt><dd>${c?.pendientes ?? "—"}</dd></div>
        <div><dt>IGV bajo revisión</dt><dd>${c ? soles(c.igv_revision) : "—"}</dd></div>
      </dl>
      <div class="yp-company-detail">
        ${e.ultimo_mensaje ? `<span>Último mensaje · ${esc(fechaTexto(e.ultimo_mensaje))}</span>` : ""}
        ${e.accesos.length ? `<span>Acceso MYPE · ${esc(e.accesos.join(", "))}</span>`
          : `<button type="button" class="e1-link" data-acceso="${e.id}">Dar acceso MYPE</button>`}
      </div>
      <footer class="yp-company-actions">
        ${e.activa ? `<button type="button" class="e1-btn t" data-abrir="${e.id}" data-periodo="${esc(c?.periodo)}">Abrir empresa</button>` : ""}
        <button type="button" class="pn-icono" data-editar="${e.id}" title="Editar" aria-label="Editar ${esc(e.razon_social)}">${ic("edit")}</button>
        <button type="button" class="pn-icono${e.activa ? " peligro" : ""}" data-estado="${e.id}"
          aria-label="${e.activa ? "Desactivar" : "Reactivar"} ${esc(e.razon_social)}" title="${e.activa ? "Desactivar" : "Reactivar"}">${ic("power")}</button>
      </footer>
    </article>`;
  }).join("");
}
$("buscar-empresa").addEventListener("input", pintarEmpresas);

function digito(diez) {
  const suma = [...diez].reduce((s, c, i) => s + Number(c) * PESOS[i], 0);
  const r = 11 - (suma % 11);
  return { suma, resto: suma % 11, r, dv: r === 10 ? 0 : r === 11 ? 1 : r };
}

$("ruc-libre").addEventListener("click", () => {
  const usados = new Set(empresas.map(e => e.ruc));
  for (let i = 0; i < 10; i++) {
    const diez = PREFIJO + i, ruc = diez + digito(diez).dv;
    if (!usados.has(ruc)) { $("ruc").value = ruc; return; }
  }
  mensaje("msg-empresa", "Ya se usaron los 10 RUC ficticios válidos con el prefijo 209999100.");
});

$("form-empresa").addEventListener("submit", async e => {
  e.preventDefault();
  try {
    await api("/api/empresas", { method: "POST", body: JSON.stringify({ ruc: $("ruc").value.trim(), razon_social: $("razon").value, telefono: $("telefono").value }) });
    evento("empresa_creada");
    $("dlg-nueva").close();
    await cargar();
  } catch (err) { mensaje("msg-empresa", err.message); }
});

// ---------- Abrir empresa y dar acceso ----------
let accesoPara = null;
let editando = null;
document.addEventListener("click", async e => {
  const a = e.target.closest("button[data-abrir]");
  if (a) {
    try {
      await api(`/api/empresas/${a.dataset.abrir}/activar`, { method: "POST" });
      const destino = new URL("/demo.html", location.origin);
      if (a.dataset.periodo) destino.searchParams.set("periodo", a.dataset.periodo);
      location.href = destino.href;
    }
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
  if (e.target.closest("#btn-nueva")) {
    $("form-empresa").reset();
    $("msg-empresa").innerHTML = "";
    $("dlg-nueva").showModal();
    $("ruc").focus();
  }
  if (e.target.closest("[data-cerrar-nueva]")) $("dlg-nueva").close();
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
  $("ruc-libre").hidden = !yo.demo;
  return cargar();
}).catch(error => { $("empresa-estado").textContent = error.message; });
