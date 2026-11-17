const $ = id => document.getElementById(id);
$("form").addEventListener("submit", async e => {
  e.preventDefault();
  $("msg").innerHTML = "";
  const r = await fetch("/api/login", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ usuario: $("usuario").value, clave: $("clave").value }) }).catch(() => null);
  const d = await r?.json().catch(() => ({}));
  if (!r?.ok) {
    $("msg").innerHTML = `<p class="e1-msg err"></p>`;
    $("msg").firstChild.textContent = (d?.errores || ["No hay conexión con el servidor."]).join(" ");
    return;
  }
  location.href = d.rol === "ESTUDIO" ? "/panel.html" : "/demo.html";
});
// Si ya hay sesión, no pedir login otra vez
fetch("/api/yo").then(r => r.ok ? r.json() : null).then(d => { if (d) location.href = d.rol === "ESTUDIO" ? "/panel.html" : "/demo.html"; });
