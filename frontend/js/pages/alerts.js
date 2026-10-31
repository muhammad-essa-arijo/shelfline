import * as api from "../api.js";
import { h, units, formatDate, statusChip, chip } from "../util.js";
import { icon } from "../icons.js";
import { pageHeader, loadView, skeletonLines, emptyState, toast, confirmDialog, setBusy } from "../ui.js";

export function renderAlerts(root) {
  const removeBtn = h("button", { class: "btn btn-danger", type: "button", disabled: true }, icon("trash", 16), "Remove expired from front");
  root.append(pageHeader("Alerts", "Expired batches, batches that expire within 30 days, and medicines running low.", [removeBtn]));
  const body = h("div", { class: "stack" });
  root.append(body);

  function load() {
    removeBtn.disabled = true;
    loadView(body, () => h("div", { class: "stack" }, skeletonLines(3), skeletonLines(3), skeletonLines(3)), api.getAlerts, (data) => {
      removeBtn.disabled = data.expired.length === 0;
      body.append(
        batchGroup("Expired", "expired", data.expired, "Expired on",
          "Only expired batches at the front of a queue can be removed. A batch behind a good batch stays until it reaches the front."),
        batchGroup("Expires soon", "soon", data.expiringSoon, "Expires on", null),
        lowGroup(data.lowStock));
    });
  }

  removeBtn.addEventListener("click", async () => {
    const ok = await confirmDialog({
      title: "Remove expired batches",
      message: "Expired batches at the front of each queue will be removed and logged as wastage. This cannot be undone.",
      confirmLabel: "Remove expired", danger: true
    });
    if (!ok) return;
    setBusy(removeBtn, true, "Removing");
    try {
      const result = await api.removeExpired();
      toast(result.message);
    } catch (error) {
      toast(error.message, "error");
    } finally {
      setBusy(removeBtn, false);
      load();
    }
  });

  load();
}

function groupCard(title, count, kind, content, note) {
  return h("section", { class: "card", "aria-label": title },
    h("div", { class: "card-head" },
      h("h2", {}, title),
      chip(kind, `${count} ${count === 1 ? "item" : "items"}`)),
    content,
    note ? h("p", { class: "card-note" }, note) : null);
}

function batchGroup(title, kind, list, dateLabel, note) {
  const content = list.length === 0
    ? emptyState({ title: `No ${title.toLowerCase()} batches`, text: "Nothing needs attention here.", actionLabel: "View inventory", href: "#/inventory" })
    : h("div", { class: "table-wrap" }, h("table", {},
      h("thead", {}, h("tr", {},
        h("th", {}, "Medicine"), h("th", {}, "Batch"), h("th", { class: "right" }, "Units"),
        h("th", {}, dateLabel), h("th", {}, "Status"))),
      h("tbody", {}, list.map((item) => h("tr", {},
        h("td", { class: "strong" }, item.medicine),
        h("td", {}, item.batchNo),
        h("td", { class: "right" }, units(item.quantity)),
        h("td", {}, formatDate(item.expiryDate)),
        h("td", {}, statusChip(item.status, item.statusLabel)))))));
  return groupCard(title, list.length, kind, content, list.length ? note : null);
}

function lowGroup(list) {
  const content = list.length === 0
    ? emptyState({ title: "No low-stock medicines", text: "Every medicine is at or above its threshold.", actionLabel: "View inventory", href: "#/inventory" })
    : h("div", { class: "table-wrap" }, h("table", {},
      h("thead", {}, h("tr", {},
        h("th", {}, "Medicine"), h("th", { class: "right" }, "Units in stock"),
        h("th", { class: "right" }, "Threshold"), h("th", {}, "Status"), h("th", {}, "Action"))),
      h("tbody", {}, list.map((item) => h("tr", {},
        h("td", { class: "strong" }, item.medicine),
        h("td", { class: "right" }, units(item.totalUnits)),
        h("td", { class: "right" }, units(item.threshold)),
        h("td", {}, chip("low", "Low stock", "alert-triangle")),
        h("td", {}, h("a", { class: "btn btn-secondary btn-small", href: `#/add?medicine=${encodeURIComponent(item.medicine)}` }, "Add batch")))))));
  return groupCard("Low stock", list.length, "low", content, null);
}
