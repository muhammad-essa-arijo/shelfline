import * as api from "../api.js";
import { h } from "../util.js";
import { icon } from "../icons.js";
import { pageHeader, loadView, skeletonLines, selectField, notice, toast, setBusy } from "../ui.js";

export function renderExport(root) {
  root.append(pageHeader("Export", "Download the current stock as a PDF report or an Excel workbook."));
  const body = h("div", {});
  root.append(body);
  loadView(body, () => skeletonLines(5), api.getStock, (items) => build(body, items));
}

function build(body, items) {
  const medicine = selectField({
    id: "export-medicine", label: "Medicine",
    options: [{ value: "", label: "All medicines" }].concat(items.map((med) => ({ value: med.key, label: med.key })))
  });
  const onlyAlerts = h("input", { type: "checkbox", id: "export-alerts" });

  const pdfNote = h("div", {});
  const excelNote = h("div", {});
  const pdfBtn = h("button", { class: "btn btn-primary", type: "button" }, icon("download", 16), "Download PDF");
  const excelBtn = h("button", { class: "btn btn-primary", type: "button" }, icon("download", 16), "Download Excel");

  async function download(path, button, noteBox, fallbackName) {
    noteBox.replaceChildren();
    setBusy(button, true, "Preparing file");
    try {
      const { blob, filename } = await api.downloadFile(path);
      const name = filename || fallbackName;
      const url = URL.createObjectURL(blob);
      const link = h("a", { href: url, download: name });
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
      noteBox.replaceChildren(notice("success", `${name} downloaded.`));
      toast(`${name} downloaded.`);
    } catch (error) {
      noteBox.replaceChildren(notice("error", error.message));
    } finally {
      setBusy(button, false);
    }
  }

  pdfBtn.addEventListener("click", () => {
    const params = new URLSearchParams();
    if (medicine.input.value) params.set("medicine", medicine.input.value);
    if (onlyAlerts.checked) params.set("onlyAlerts", "true");
    const query = params.toString();
    download(`/api/export/pdf${query ? "?" + query : ""}`, pdfBtn, pdfNote, "shelfline-stock.pdf");
  });
  excelBtn.addEventListener("click", () => download("/api/export/excel", excelBtn, excelNote, "shelfline-stock.xlsx"));

  body.append(h("div", { class: "export-grid" },
    h("section", { class: "card export-card", "aria-label": "PDF report" },
      h("div", { class: "export-head" }, h("div", { class: "stat-icon" }, icon("file-text", 22)), h("h2", { class: "section-title" }, "PDF report")),
      h("p", {}, "A4 report with summary figures, a stock table and a front-to-rear diagram for each medicine."),
      medicine.wrap,
      h("label", { class: "check", for: "export-alerts" }, onlyAlerts, "Only batches that need attention"),
      h("div", {}, pdfBtn),
      pdfNote),
    h("section", { class: "card export-card", "aria-label": "Excel workbook" },
      h("div", { class: "export-head" }, h("div", { class: "stat-icon" }, icon("file-sheet", 22)), h("h2", { class: "section-title" }, "Excel workbook")),
      h("p", {}, "Four sheets: Stock, Summary, Expiry Alerts and Activity Log. Totals use live SUM formulas."),
      h("div", {}, excelBtn),
      excelNote)));
}
