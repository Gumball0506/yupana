// Landing pública de Yupana: eventos anónimos, carrusel, animaciones suaves e imagen ampliada.
// Todo es mejora progresiva: sin JavaScript la página se ve completa y los enlaces siguen funcionando.

// ---------- Eventos anónimos (misma sesión aleatoria por pestaña que usa el sistema) ----------
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
  const w = e.target.closest("[data-wa]");
  if (w) evento("wa_me_abierto", w.dataset.wa);
});

const $ = id => document.getElementById(id);
const movimientoReducido = matchMedia("(prefers-reduced-motion: reduce)");
const fmt = (n, dec) => n.toLocaleString("en-US", { minimumFractionDigits: dec, maximumFractionDigits: dec });

// ---------- Aviso de oferta (se puede cerrar y se recuerda) ----------
const promo = $("ln-promo");
try { if (localStorage.getItem("yupana_promo_cerrada") === "1") promo.hidden = true; } catch {}
$("ln-promo-x").addEventListener("click", () => {
  promo.hidden = true;
  try { localStorage.setItem("yupana_promo_cerrada", "1"); } catch {}
});

// ---------- Barra con sombra al bajar ----------
const barra = $("ln-bar");
const alDesplazar = () => barra.classList.toggle("is-scrolled", scrollY > 8);
addEventListener("scroll", alDesplazar, { passive: true });
alDesplazar();

// ---------- Aparición al hacer scroll ----------
const reveladas = [...document.querySelectorAll("[data-reveal]")];
reveladas.forEach((el, i) => el.style.setProperty("--d", `${(i % 3) * 90}ms`));
if ("IntersectionObserver" in window && !movimientoReducido.matches) {
  const ver = new IntersectionObserver(entradas => {
    for (const en of entradas) if (en.isIntersecting) { en.target.classList.add("is-in"); ver.unobserve(en.target); }
  }, { threshold: 0.15, rootMargin: "0px 0px -6% 0px" });
  reveladas.forEach(el => ver.observe(el));
} else {
  reveladas.forEach(el => el.classList.add("is-in"));
}

// ---------- Cifras que suben al aparecer ----------
const contadores = [...document.querySelectorAll("[data-count]")];
function subir(el) {
  const meta = Number(el.dataset.count), dec = Number(el.dataset.dec || 0), dur = 1300, t0 = performance.now();
  const paso = ahora => {
    const p = Math.min(1, (ahora - t0) / dur), suave = 1 - Math.pow(1 - p, 3);
    el.textContent = fmt(meta * suave, dec);
    if (p < 1) requestAnimationFrame(paso); else el.textContent = fmt(meta, dec);
  };
  requestAnimationFrame(paso);
}
if ("IntersectionObserver" in window && !movimientoReducido.matches) {
  const ver = new IntersectionObserver(entradas => {
    for (const en of entradas) if (en.isIntersecting) { subir(en.target); ver.unobserve(en.target); }
  }, { threshold: 0.6 });
  contadores.forEach(el => ver.observe(el));
}

// ---------- Botón fijo en celular: aparece al pasar la portada ----------
const fijo = $("ln-sticky"), portada = document.querySelector(".ln-hero"), cierre = document.querySelector(".ln-final");
if ("IntersectionObserver" in window && fijo && portada) {
  let enPortada = true, enCierre = false;
  const actualizar = () => {
    const on = !enPortada && !enCierre;
    fijo.classList.toggle("is-on", on);
    fijo.setAttribute("aria-hidden", String(!on));
    fijo.querySelectorAll("a").forEach(l => { l.tabIndex = on ? 0 : -1; });
  };
  new IntersectionObserver(([e]) => { enPortada = e.isIntersecting; actualizar(); }).observe(portada);
  if (cierre) new IntersectionObserver(([e]) => { enCierre = e.isIntersecting; actualizar(); }).observe(cierre);
}

// ---------- Carrusel del recorrido ----------
const raiz = $("ln-tour");
if (raiz) {
  const tabs = [...raiz.querySelectorAll('[role="tab"]')];
  const slides = tabs.map(t => $(t.getAttribute("aria-controls")));
  const escenario = $("ln-stage"), ahora = $("ln-now"), cuenta = $("ln-count"), btnPlay = $("ln-play");
  const DURACION = 6500;
  let indice = 0, pausadoPorUsuario = false, enPantalla = false, temporizador;
  raiz.style.setProperty("--ln-dur", DURACION + "ms");

  const reproduciendo = () => !pausadoPorUsuario && enPantalla && !document.hidden && !movimientoReducido.matches
    && !escenario.matches(":hover") && !raiz.matches(":focus-within");

  function pintarReproduccion() {
    raiz.classList.toggle("ln-playing", reproduciendo());
  }

  function mostrar(nuevo, { foco = false } = {}) {
    const anterior = indice;
    indice = (nuevo + tabs.length) % tabs.length;
    tabs.forEach((t, i) => {
      const activo = i === indice;
      t.setAttribute("aria-selected", String(activo));
      t.tabIndex = activo ? 0 : -1;
      // Reinicia la barra de progreso de la pestaña activa
      const barra = t.querySelector(".ln-fill");
      if (barra) { barra.style.animation = "none"; void barra.offsetWidth; barra.style.animation = ""; }
    });
    slides.forEach((s, i) => {
      s.classList.toggle("is-active", i === indice);
      s.classList.toggle("is-prev", i === anterior && i !== indice);
      s.inert = i !== indice;
      const img = s.querySelector("img");
      if (img && i === indice) img.loading = "eager";
    });
    const t = tabs[indice];
    const titulo = document.createElement("b"); titulo.textContent = t.querySelector("b").textContent;
    ahora.replaceChildren(titulo, t.querySelector("small").textContent);
    cuenta.textContent = `${indice + 1} / ${tabs.length}`;
    if (foco) t.focus();
    // En pantallas angostas, deja visible la pestaña activa en la tira
    if (!matchMedia("(min-width: 1000px)").matches) {
      const tira = t.parentElement;
      tira.scrollTo({ left: t.offsetLeft - tira.clientWidth / 2 + t.clientWidth / 2, behavior: movimientoReducido.matches ? "auto" : "smooth" });
    }
    programar();
  }

  function programar() {
    clearTimeout(temporizador);
    pintarReproduccion();
    if (!reproduciendo()) return;
    temporizador = setTimeout(() => mostrar(indice + 1), DURACION);
  }

  tabs.forEach((t, i) => {
    t.addEventListener("click", () => mostrar(i));
    t.addEventListener("keydown", e => {
      const mapa = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
      if (e.key in mapa) { e.preventDefault(); mostrar(indice + mapa[e.key], { foco: true }); }
      else if (e.key === "Home") { e.preventDefault(); mostrar(0, { foco: true }); }
      else if (e.key === "End") { e.preventDefault(); mostrar(tabs.length - 1, { foco: true }); }
    });
  });
  $("ln-prev").addEventListener("click", () => mostrar(indice - 1));
  $("ln-next").addEventListener("click", () => mostrar(indice + 1));
  btnPlay.addEventListener("click", () => {
    pausadoPorUsuario = !pausadoPorUsuario;
    btnPlay.setAttribute("aria-pressed", String(pausadoPorUsuario));
    btnPlay.setAttribute("aria-label", pausadoPorUsuario ? "Reanudar recorrido automático" : "Pausar recorrido automático");
    btnPlay.querySelector("use").setAttribute("href", pausadoPorUsuario ? "#i-play" : "#i-pause");
    programar();
  });

  // Pausa al pasar el mouse o enfocar; reanuda al salir
  for (const ev of ["pointerenter", "pointerleave", "focusin"]) escenario.addEventListener(ev, programar);
  raiz.addEventListener("focusin", programar);
  raiz.addEventListener("focusout", () => requestAnimationFrame(programar));
  document.addEventListener("visibilitychange", programar);
  movimientoReducido.addEventListener("change", programar);
  if ("IntersectionObserver" in window) {
    new IntersectionObserver(([e]) => { enPantalla = e.isIntersecting; programar(); }, { threshold: 0.35 }).observe(raiz);
  }

  // Deslizar con el dedo
  let x0 = null, y0 = null;
  escenario.addEventListener("pointerdown", e => { if (e.pointerType === "touch") { x0 = e.clientX; y0 = e.clientY; } });
  escenario.addEventListener("pointerup", e => {
    if (x0 === null) return;
    const dx = e.clientX - x0, dy = e.clientY - y0;
    x0 = y0 = null;
    if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.4) mostrar(indice + (dx < 0 ? 1 : -1));
  });
  escenario.addEventListener("pointercancel", () => { x0 = y0 = null; });

  mostrar(0);
}

// ---------- Imagen ampliada ----------
const lb = $("ln-lb");
if (lb && typeof lb.showModal === "function") {
  const img = $("ln-lb-img"), txt = $("ln-lb-txt");
  document.addEventListener("click", e => {
    const a = e.target.closest("[data-zoom]");
    if (!a) return;
    e.preventDefault();
    const miniatura = a.querySelector("img");
    img.src = a.getAttribute("href");
    img.alt = miniatura.alt;
    txt.textContent = miniatura.alt;
    lb.showModal();
    $("ln-lb-x").focus();
  });
  $("ln-lb-x").addEventListener("click", () => lb.close());
  lb.addEventListener("click", e => { if (e.target === lb) lb.close(); });
  lb.addEventListener("close", () => { img.src = "data:,"; });
}
