const $ = id => document.getElementById(id);
const mensaje = (texto, tipo) => { $("msg").innerHTML = `<p class="e1-msg ${tipo}"></p>`; $("msg").firstChild.textContent = texto; };

$("form").addEventListener("submit", async e => {
  e.preventDefault();
  if ($("nueva").value !== $("repetir").value) { mensaje("Las dos claves nuevas no coinciden.", "err"); return; }
  const r = await fetch("/api/clave", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ actual: $("actual").value, nueva: $("nueva").value }) }).catch(() => null);
  if (r?.status === 401) { location.href = "/login.html"; return; }
  if (r?.status === 204) {
    $("form").reset();
    mensaje("Clave cambiada. Tus otras sesiones se cerraron.", "ok");
    return;
  }
  const d = await r?.json().catch(() => ({}));
  mensaje((d?.errores || ["No hay conexión con el servidor."]).join(" "), "err");
});

// Volver al lugar correcto según el rol; sin sesión, al ingreso
fetch("/api/yo").then(r => r.ok ? r.json() : null).then(d => {
  if (!d) { location.href = "/login.html"; return; }
  $("usuario").textContent = d.usuario;
  $("volver").href = d.rol === "ESTUDIO" ? "/panel.html" : "/demo.html";
}).catch(() => {});
