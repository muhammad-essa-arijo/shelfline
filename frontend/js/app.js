import { onSlowChange } from "./api.js";
import { state } from "./state.js";
import { h, formatDate, todayISO } from "./util.js";
import { icon } from "./icons.js";
import { dismissLatestToast } from "./ui.js";
import { renderDashboard } from "./pages/dashboard.js";
import { renderInventory } from "./pages/inventory.js";
import { renderAdd } from "./pages/add.js";
import { renderSell } from "./pages/sell.js";
import { renderAlerts } from "./pages/alerts.js";
import { renderActivity } from "./pages/activity.js";
import { renderExport } from "./pages/export.js";

const ROUTES = {
  dashboard: { title: "Dashboard", icon: "dashboard", render: renderDashboard },
  inventory: { title: "Inventory", icon: "layers", render: renderInventory },
  add: { title: "Add batch", icon: "plus", render: renderAdd },
  sell: { title: "Sell stock", icon: "cart", render: renderSell },
  alerts: { title: "Alerts", icon: "alert-triangle", render: renderAlerts },
  activity: { title: "Activity", icon: "history", render: renderActivity },
  export: { title: "Export", icon: "download", render: renderExport }
};

const main = document.getElementById("main");
const sidebar = document.getElementById("sidebar");
const scrim = document.getElementById("scrim");
const menuBtn = document.getElementById("menu-btn");
const themeBtn = document.getElementById("theme-btn");

// ----- Sidebar -----
document.getElementById("brand").append(
  icon("pill", 28),
  h("div", {}, h("div", { class: "brand-name" }, "ShelfLine"), h("div", { class: "brand-tag" }, "Oldest stock out first.")));

const nav = document.getElementById("nav");
Object.entries(ROUTES).forEach(([name, route]) => {
  nav.append(h("a", { class: "nav-link", href: `#/${name}`, "data-route": name }, icon(route.icon, 20), route.title));
});

// ----- Top bar -----
menuBtn.append(icon("menu", 20));
document.getElementById("topbar-date").append(icon("calendar", 18), formatDate(todayISO()));

const searchWrap = document.getElementById("search-wrap");
const searchInput = h("input", {
  class: "input", id: "search", type: "search", placeholder: "Search medicines", autocomplete: "off"
});
searchWrap.append(icon("search", 18), searchInput);

let searchTimer = null;
searchInput.addEventListener("input", () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    state.search = searchInput.value;
    if (currentRoute() !== "inventory") {
      location.hash = "#/inventory";
    } else {
      document.dispatchEvent(new CustomEvent("shelfline:search"));
    }
  }, 200);
});

// ----- Theme -----
function applyTheme(theme) {
  if (theme === "dark") document.documentElement.setAttribute("data-theme", "dark");
  else document.documentElement.removeAttribute("data-theme");
  const dark = theme === "dark";
  themeBtn.replaceChildren(icon(dark ? "sun" : "moon", 20));
  themeBtn.setAttribute("aria-pressed", String(dark));
  themeBtn.setAttribute("aria-label", dark ? "Switch to light theme" : "Switch to dark theme");
}
themeBtn.addEventListener("click", () => {
  const next = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
  applyTheme(next);
  try { localStorage.setItem("shelfline-theme", next); } catch (ignored) { /* storage blocked */ }
});
applyTheme(document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light");

// ----- Drawer (below 900 px) -----
function openDrawer() {
  sidebar.classList.add("open");
  scrim.hidden = false;
  menuBtn.setAttribute("aria-expanded", "true");
  const first = sidebar.querySelector(".nav-link");
  if (first) first.focus();
}
function closeDrawer(returnFocus = false) {
  if (!sidebar.classList.contains("open")) return;
  sidebar.classList.remove("open");
  scrim.hidden = true;
  menuBtn.setAttribute("aria-expanded", "false");
  if (returnFocus) menuBtn.focus();
}
menuBtn.addEventListener("click", () => (sidebar.classList.contains("open") ? closeDrawer(true) : openDrawer()));
scrim.addEventListener("click", () => closeDrawer(true));

// ----- Escape closes the drawer, then dismisses the newest toast (dialogs close themselves) -----
document.addEventListener("keydown", (event) => {
  if (event.key !== "Escape" || document.querySelector("dialog[open]")) return;
  if (sidebar.classList.contains("open")) { closeDrawer(true); return; }
  dismissLatestToast();
});

// ----- Slow server notice -----
const banner = document.getElementById("slow-banner");
onSlowChange((slow) => {
  if (slow) {
    banner.replaceChildren(icon("info", 18), "Starting the server. This can take up to a minute on the free plan.");
    banner.hidden = false;
  } else {
    banner.hidden = true;
    banner.replaceChildren();
  }
});

// ----- Router -----
function parseHash() {
  const raw = location.hash.replace(/^#\/?/, "");
  const [name, queryText = ""] = raw.split("?");
  return { name: ROUTES[name] ? name : "dashboard", query: new URLSearchParams(queryText) };
}
function currentRoute() {
  return parseHash().name;
}

function navigate() {
  const { name, query } = parseHash();
  nav.querySelectorAll(".nav-link").forEach((link) => {
    if (link.dataset.route === name) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });

  const view = h("section", { class: "view", tabindex: "-1" });
  main.replaceChildren(view);
  document.title = `${ROUTES[name].title} | ShelfLine`;
  ROUTES[name].render(view, query);
  closeDrawer();
  window.scrollTo(0, 0);
  view.focus({ preventScroll: true });
}

window.addEventListener("hashchange", navigate);
navigate();
