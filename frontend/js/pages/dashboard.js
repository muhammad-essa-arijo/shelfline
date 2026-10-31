import * as api from "../api.js";
import { h, money, units, formatDate, formatDateTime, statusChip, actionChip } from "../util.js";
import { icon } from "../icons.js";
import { pageHeader, loadView, skeletonCards, emptyState } from "../ui.js";

export function renderDashboard(root) {
  root.append(pageHeader("Dashboard", "Stock levels, expiry dates and the latest actions."));
  const body = h("div", {});
  root.append(body);

  loadView(body, skeletonCards,
    () => Promise.all([api.getSummary(), api.getAlerts(), api.getActivity()]),
    ([summary, alerts, activity]) => {
      const cards = [
        ["Total medicines", summary.totalMedicines, "layers", false],
        ["Total batches", summary.totalBatches, "package", false],
        ["Expiring soon", summary.expiringSoonCount, "clock", false],
        ["Low stock", summary.lowStockCount, "alert-triangle", false],
        ["Total stock value", money(summary.totalStockValue), "banknote", true]
      ];
      body.append(h("div", { class: "stats" }, cards.map(([label, value, iconName, small]) =>
        h("div", { class: "card stat" },
          h("div", { class: "stat-icon" }, icon(iconName, 22)),
          h("div", {}, h("div", { class: "stat-label" }, label), h("div", { class: `stat-value${small ? " small" : ""}` }, value))))));

      const soon = alerts.expiringSoon.slice().sort((a, b) => a.daysToExpiry - b.daysToExpiry).slice(0, 8);
      const expiringCard = h("section", { class: "card", "aria-label": "Expiring soon" },
        h("div", { class: "card-head" },
          h("h2", {}, "Expiring soon"),
          h("a", { href: "#/alerts" }, "View all alerts")),
        soon.length === 0
          ? emptyState({ title: "No batches expire in the next 30 days", text: "Check the inventory to see every queue.", actionLabel: "View inventory", href: "#/inventory" })
          : h("div", { class: "table-wrap" }, h("table", {},
            h("thead", {}, h("tr", {},
              h("th", {}, "Medicine"), h("th", {}, "Batch"), h("th", { class: "right" }, "Units"),
              h("th", {}, "Expiry"), h("th", {}, "Status"))),
            h("tbody", {}, soon.map((item) => h("tr", {},
              h("td", { class: "strong" }, item.medicine),
              h("td", {}, item.batchNo),
              h("td", { class: "right" }, units(item.quantity)),
              h("td", {}, formatDate(item.expiryDate)),
              h("td", {}, statusChip(item.status, item.statusLabel))))))),
        alerts.expired.length
          ? h("p", { class: "card-note" }, `${alerts.expired.length} expired ${alerts.expired.length === 1 ? "batch is" : "batches are"} still in stock. `,
            h("a", { href: "#/alerts" }, "Review expired batches"))
          : null);

      const recent = activity.slice(0, 5);
      const activityCard = h("section", { class: "card", "aria-label": "Recent activity" },
        h("div", { class: "card-head" }, h("h2", {}, "Recent activity"), h("a", { href: "#/activity" }, "View all")),
        recent.length === 0
          ? emptyState({ title: "No activity yet", text: "Actions appear here after you add, sell or remove a batch.", actionLabel: "Add batch", href: "#/add" })
          : h("ul", { class: "feed" }, recent.map((entry) => h("li", {},
            h("div", { class: "feed-top" }, actionChip(entry.action), h("span", { class: "num strong" }, `${entry.medicine}, ${entry.batchNo}`)),
            h("span", {}, entry.details),
            h("span", { class: "feed-time" }, formatDateTime(entry.time))))));

      body.append(h("div", { class: "two-col" }, expiringCard, activityCard));
    });
}
