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
const diapositivas = recorrido.querySelector(".yp-tour-slides");
const botones = [...controles.querySelectorAll("[aria-controls]")];
const movimientoReducido = matchMedia("(prefers-reduced-motion: reduce)");
let indice = 0;
let visible = false;
let temporizador;

function mostrarPantalla(nuevoIndice) {
  indice = (nuevoIndice + botones.length) % botones.length;
  botones.forEach((boton, posicion) => {
    const seleccionado = posicion === indice;
    boton.setAttribute("aria-pressed", String(seleccionado));
    document.getElementById(boton.getAttribute("aria-controls")).hidden = !seleccionado;
  });
}

function programarAvance() {
  clearTimeout(temporizador);
  if (movimientoReducido.matches || !visible || document.hidden || diapositivas.matches(":hover, :focus-within")) return;
  temporizador = setTimeout(() => {
    mostrarPantalla(indice + 1);
    programarAvance();
  }, 7000);
}

function seleccionar(nuevoIndice) {
  mostrarPantalla(nuevoIndice);
  programarAvance();
}

botones.forEach((boton, posicion) => boton.addEventListener("click", () => seleccionar(posicion)));
for (const flecha of controles.querySelectorAll("[data-tour-step]")) {
  flecha.addEventListener("click", () => seleccionar(indice + Number(flecha.dataset.tourStep)));
}
for (const evento of ["pointerenter", "pointerleave", "focusin"]) {
  diapositivas.addEventListener(evento, programarAvance);
}
diapositivas.addEventListener("focusout", () => requestAnimationFrame(programarAvance));
document.addEventListener("visibilitychange", programarAvance);
movimientoReducido.addEventListener("change", programarAvance);
new IntersectionObserver(([entrada]) => {
  visible = entrada.isIntersecting;
  programarAvance();
}).observe(controles);

mostrarPantalla(0);
controles.hidden = false;
programarAvance();
