import { icon } from "./icons.js";

// Builds an element. Text is always added as text, never as HTML, so user input cannot inject markup.
export function h(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === "class") node.className = value;
    else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2).toLowerCase(), value);
    else node.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    node.append(typeof child === "object" ? child : document.createTextNode(String(child)));
  }
  return node;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// "2026-11-23" -> "23 Nov 2026"
export function formatDate(iso) {
  if (!iso) return "";
  const [year, month, day] = iso.slice(0, 10).split("-");
  return `${day} ${MONTHS[Number(month) - 1]} ${year}`;
}

// "2026-10-28T14:05:11" -> "28 Oct 2026, 14:05"
export function formatDateTime(iso) {
  if (!iso) return "";
  return `${formatDate(iso)}, ${iso.slice(11, 16)}`;
}

export function money(value) {
  return "Rs. " + Number(value).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function units(count) {
  return Number(count) === 1 ? "1 unit" : `${Number(count).toLocaleString("en-US")} units`;
}

export function todayISO() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export function reducedMotion() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

// How long to wait for a slide-out animation. Zero when the person prefers reduced motion.
export function motionMs() {
  return reducedMotion() ? 0 : 250;
}

// ----- Chips -----
const STATUS = {
  SAFE: { kind: "safe", icon: "check-circle" },
  EXPIRES_SOON: { kind: "soon", icon: "clock" },
  EXPIRED: { kind: "expired", icon: "alert-circle" }
};

export function chip(kind, text, iconName) {
  return h("span", { class: `chip chip-${kind}` }, iconName ? icon(iconName, 14) : null, text);
}

export function statusChip(status, label) {
  const meta = STATUS[status] || STATUS.SAFE;
  return chip(meta.kind, label, meta.icon);
}

export function statusKind(status) {
  return (STATUS[status] || STATUS.SAFE).kind;
}

export function statusIcon(status) {
  return (STATUS[status] || STATUS.SAFE).icon;
}

export function actionChip(action) {
  const map = {
    ADDED: ["info", "Added", "plus"],
    UPDATED: ["info", "Updated", "pencil"],
    SOLD: ["safe", "Sold", "cart"],
    REMOVED: ["low", "Removed", "trash"],
    EXPIRED: ["expired", "Expired", "alert-circle"]
  };
  const [kind, label, iconName] = map[action] || ["info", action, "info"];
  return chip(kind, label, iconName);
}
