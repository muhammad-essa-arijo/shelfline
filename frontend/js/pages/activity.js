import * as api from "../api.js";
import { h, formatDateTime, actionChip } from "../util.js";
import { pageHeader, loadView, skeletonLines, emptyState } from "../ui.js";

export function renderActivity(root) {
  root.append(pageHeader("Activity", "The latest 50 actions, newest first."));
  const body = h("div", {});
  root.append(body);

  loadView(body, () => skeletonLines(6), api.getActivity, (entries) => {
    if (entries.length === 0) {
      body.append(h("div", { class: "card" }, emptyState({
        title: "No activity yet",
        text: "Actions appear here after you add, update, sell or remove a batch.",
        actionLabel: "Add batch", href: "#/add"
      })));
      return;
    }
    body.append(h("div", { class: "card" }, h("div", { class: "table-wrap" }, h("table", {},
      h("thead", {}, h("tr", {},
        h("th", {}, "Time"), h("th", {}, "Action"), h("th", {}, "Medicine"), h("th", {}, "Batch"), h("th", {}, "Details"))),
      h("tbody", {}, entries.map((entry) => h("tr", {},
        h("td", {}, formatDateTime(entry.time)),
        h("td", {}, actionChip(entry.action)),
        h("td", { class: "strong" }, entry.medicine),
        h("td", {}, entry.batchNo),
        h("td", {}, entry.details))))))));
  });
}
