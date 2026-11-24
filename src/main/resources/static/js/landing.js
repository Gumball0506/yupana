// Marketing 5.0: mismos eventos anónimos que la demo (sesión aleatoria por pestaña)
let sesion;
try { sesion = sessionStorage.getItem("yupana_sesion"); } catch {}
if (!sesion) {
  sesion = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(16).slice(2).padEnd(12, "0");
  try { sessionStorage.setItem("yupana_sesion", sesion); } catch {}
}
const evento = (tipo, detalle) => fetch("/api/eventos", { method: "POST", headers: { "Content-Type": "application/json" },
  keepalive: true, body: JSON.stringify({ sesion, tipo, detalle }) }).catch(() => {});
evento("landing_vista");
document.addEventListener("click", e => {
  const a = e.target.closest("[data-cta]");
  if (a) evento("cta_probar_demo", a.dataset.cta);
});

const recorrido = document.getElementById("recorrido");
const controles = recorrido.querySelector(".yp-tour-controls");
const botones = [...controles.querySelectorAll("button")];
function mostrarPantalla(activo) {
  for (const boton of botones) {
    const seleccionado = boton === activo;
    boton.setAttribute("aria-pressed", String(seleccionado));
    document.getElementById(boton.getAttribute("aria-controls")).hidden = !seleccionado;
  }
}
for (const boton of botones) boton.addEventListener("click", () => mostrarPantalla(boton));
mostrarPantalla(botones[0]);
controles.hidden = false;
