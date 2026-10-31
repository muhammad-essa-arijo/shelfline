import { h } from "./util.js";
import { icon } from "./icons.js";

// ----- Page header -----
export function pageHeader(title, subtitle, actions = []) {
  return h("div", { class: "page-head" },
    h("div", {}, h("h1", {}, title), subtitle ? h("p", {}, subtitle) : null),
    actions.length ? h("div", { class: "page-actions" }, actions) : null);
}

// ----- Loading, empty and error states -----
export function skeletonLines(count = 3) {
  return h("div", { class: "card", "aria-busy": "true" },
    Array.from({ length: count }, () => h("div", { class: "skeleton skel-line" })));
}

export function skeletonCards(count = 5) {
  return h("div", { class: "stats", "aria-busy": "true" },
    Array.from({ length: count }, () => h("div", { class: "skeleton skel-card" })));
}

export function skeletonLanes(count = 3) {
  return h("div", { class: "lanes", "aria-busy": "true" },
    Array.from({ length: count }, () => h("div", { class: "skeleton skel-lane" })));
}

export function emptyState({ title, text, actionLabel, href, onClick }) {
  let action = null;
  if (actionLabel && href) {
    action = h("a", { class: "btn btn-primary", href }, actionLabel);
  } else if (actionLabel && onClick) {
    action = h("button", { class: "btn btn-primary", type: "button", onclick: onClick }, actionLabel);
  }
  return h("div", { class: "state" },
    h("div", { class: "state-icon" }, icon("inbox", 24)),
    h("h2", {}, title),
    text ? h("p", {}, text) : null,
    action);
}

export function errorState(message, onRetry) {
  return h("div", { class: "card" },
    h("div", { class: "state state-error", role: "alert" },
      h("div", { class: "state-icon" }, icon("alert-circle", 24)),
      h("h2", {}, "Could not load this page"),
      h("p", {}, message),
      h("button", { class: "btn btn-primary", type: "button", onclick: onRetry }, icon("refresh", 16), "Retry")));
}

// Shows a skeleton, loads the data, then draws it. Shows an error with Retry if loading fails.
export async function loadView(container, skeleton, load, render) {
  container.replaceChildren(skeleton());
  try {
    const data = await load();
    container.replaceChildren();
    render(data);
  } catch (error) {
    container.replaceChildren(errorState(error.message, () => loadView(container, skeleton, load, render)));
  }
}

// ----- Buttons -----
export function setBusy(button, busy, busyLabel) {
  if (busy) {
    button._saved = Array.from(button.childNodes);
    button.disabled = true;
    button.setAttribute("aria-busy", "true");
    button.replaceChildren(h("span", { class: "spin", style: "display:inline-flex" }, icon("loader", 16)), busyLabel);
  } else {
    button.disabled = false;
    button.removeAttribute("aria-busy");
    if (button._saved) button.replaceChildren(...button._saved);
  }
}

// ----- Form fields -----
export function field({ id, label, type = "text", value = "", hint = "", list = null, attrs = {} }) {
  const input = h("input", { class: "input", id, name: id, type, value, list, autocomplete: "off", ...attrs });
  const error = h("p", { class: "field-error", id: `${id}-error`, hidden: true });
  const hintEl = hint ? h("p", { class: "hint", id: `${id}-hint` }, hint) : null;
  input.setAttribute("aria-describedby", [hint ? `${id}-hint` : null, `${id}-error`].filter(Boolean).join(" "));
  const wrap = h("div", { class: "field" }, h("label", { for: id }, label), input, hintEl, error);
  return { wrap, input, setError: (message) => showError(input, error, message) };
}

export function selectField({ id, label, options, value = "" }) {
  const select = h("select", { class: "select", id, name: id },
    options.map((option) => h("option", { value: option.value }, option.label)));
  select.value = value;
  const error = h("p", { class: "field-error", id: `${id}-error`, hidden: true });
  select.setAttribute("aria-describedby", `${id}-error`);
  const wrap = h("div", { class: "field" }, h("label", { for: id }, label), select, error);
  return { wrap, input: select, setError: (message) => showError(select, error, message) };
}

function showError(input, errorEl, message) {
  if (message) {
    errorEl.replaceChildren(icon("alert-circle", 14), message);
    errorEl.hidden = false;
    input.setAttribute("aria-invalid", "true");
  } else {
    errorEl.replaceChildren();
    errorEl.hidden = true;
    input.removeAttribute("aria-invalid");
  }
}

export function notice(kind, ...children) {
  const iconName = kind === "success" ? "check-circle" : kind === "error" ? "alert-circle" : "info";
  return h("div", { class: `notice notice-${kind}`, role: kind === "error" ? "alert" : "status" },
    icon(iconName, 18), h("div", {}, children));
}

// ----- Toasts -----
export function toast(message, kind = "success") {
  const region = document.getElementById("toasts");
  let timer = null;
  const node = h("div", { class: `toast toast-${kind}`, role: kind === "error" ? "alert" : null },
    icon(kind === "error" ? "alert-circle" : "check-circle", 20),
    h("p", { class: "toast-text" }, message),
    h("button", { class: "icon-btn", type: "button", "aria-label": "Dismiss message", onclick: remove }, icon("x", 16)));
  function remove() {
    clearTimeout(timer);
    node.remove();
  }
  region.append(node);
  timer = setTimeout(remove, 4000);
}

export function dismissLatestToast() {
  const toasts = document.querySelectorAll("#toasts .toast");
  if (toasts.length === 0) return false;
  toasts[toasts.length - 1].remove();
  return true;
}

// ----- Dialogs -----
// buttons: [{ label, kind: "primary" | "secondary" | "danger", onClick(close), type, form }]
export function modal({ title, body, buttons, onClosed }) {
  const previous = document.activeElement;
  const titleId = `dialog-title-${Math.random().toString(36).slice(2, 8)}`;
  const dialog = h("dialog", { class: "dialog", "aria-labelledby": titleId },
    h("div", { class: "dialog-body" }, h("h2", { class: "dialog-title", id: titleId }, title), body),
    h("div", { class: "dialog-foot" }, buttons.map((spec) =>
      h("button", {
        class: `btn btn-${spec.kind || "secondary"}`,
        type: spec.type || "button",
        form: spec.form,
        onclick: spec.onClick ? () => spec.onClick(close) : null
      }, spec.label))));

  function close() {
    if (dialog.open) dialog.close();
  }

  // Escape triggers the native close event. A click on the backdrop closes too.
  dialog.addEventListener("close", () => {
    dialog.remove();
    if (previous && previous.isConnected && typeof previous.focus === "function") previous.focus();
    if (onClosed) onClosed();
  });
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) close();
  });

  document.body.append(dialog);
  dialog.showModal();
  return { dialog, close };
}

export function confirmDialog({ title, message, confirmLabel, danger = false }) {
  return new Promise((resolve) => {
    let result = false;
    modal({
      title,
      body: h("p", {}, message),
      buttons: [
        { label: "Cancel", kind: "secondary", onClick: (close) => close() },
        { label: confirmLabel, kind: danger ? "danger" : "primary", onClick: (close) => { result = true; close(); } }
      ],
      onClosed: () => resolve(result)
    });
  });
}
