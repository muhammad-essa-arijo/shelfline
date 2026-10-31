import * as api from "../api.js";
import { state } from "../state.js";
import { h, units, todayISO } from "../util.js";
import { icon } from "../icons.js";
import { pageHeader, loadView, skeletonLines, field, notice, toast, setBusy } from "../ui.js";
import { checkRequired, checkQuantity, checkPrice, checkExpiry } from "../validate.js";

const tidy = (text) => String(text || "").trim().replace(/\s+/g, " ");

export function renderAdd(root, query) {
  root.append(pageHeader("Add batch", "A new batch always joins the rear of its medicine's queue."));
  const body = h("div", {});
  root.append(body);
  loadView(body, () => skeletonLines(6), api.getStock, (items) => build(body, items, query));
}

function build(body, items, query) {
  let stock = items;

  const name = field({ id: "add-name", label: "Medicine name", value: query.get("medicine") || "", list: "medicine-names", hint: "Example: Paracetamol" });
  const strength = field({ id: "add-strength", label: "Strength", value: query.get("strength") || "", list: "strengths", hint: "Example: 500 mg" });
  const batchNo = field({ id: "add-batch", label: "Batch number", hint: "Example: B-2041" });
  const quantity = field({ id: "add-qty", label: "Quantity (units)", type: "number", attrs: { min: "1", step: "1", inputmode: "numeric" } });
  const price = field({ id: "add-price", label: "Unit price (Rs.)", type: "number", attrs: { min: "0.01", step: "0.01", inputmode: "decimal" } });
  const expiry = field({ id: "add-expiry", label: "Expiry date", type: "date", attrs: { min: todayISO() } });

  const nameList = h("datalist", { id: "medicine-names" });
  const strengthList = h("datalist", { id: "strengths" });
  const formNotice = h("div", { "aria-live": "polite" });
  const panel = h("aside", { class: "card" });

  const submit = h("button", { class: "btn btn-primary", type: "submit" }, icon("plus", 16), "Add batch");
  const form = h("form", { novalidate: true },
    h("div", { class: "form-grid" }, name.wrap, strength.wrap, batchNo.wrap, quantity.wrap, price.wrap, expiry.wrap),
    h("div", { class: "form-actions" }, submit),
    h("div", { style: "margin-top:16px" }, formNotice),
    nameList, strengthList);

  body.append(h("div", { class: "split" }, h("div", { class: "card" }, form), panel));

  // ----- helpers -----
  function findMedicine() {
    const key = tidy(`${name.input.value} ${strength.input.value}`).toLowerCase();
    return stock.find((med) => med.key.toLowerCase() === key) || null;
  }

  function refreshLists() {
    const typed = tidy(name.input.value).toLowerCase();
    const names = [...new Set(stock.map((med) => med.name))];
    const strengths = stock.filter((med) => !typed || med.name.toLowerCase() === typed).map((med) => med.strength);
    nameList.replaceChildren(...names.map((value) => h("option", { value })));
    strengthList.replaceChildren(...[...new Set(strengths)].map((value) => h("option", { value })));
  }

  function refreshPanel() {
    const med = findMedicine();
    const parts = [
      h("h2", { style: "font-size:var(--fs-16);font-weight:600;color:var(--text-strong);margin-bottom:8px" }, "Where this batch goes"),
      h("p", {}, "This batch will join the rear of the queue for the selected medicine.")
    ];
    if (med) {
      parts.push(h("p", { style: "margin-top:12px" },
        `${med.key} has ${med.batches.length} ${med.batches.length === 1 ? "batch" : "batches"} (${units(med.totalUnits)}).`));
      const chain = h("div", { class: "side-list", "aria-label": "Current queue, front to rear" });
      med.batches.forEach((batch, index) => {
        if (index > 0) chain.append(icon("arrow-right", 16));
        chain.append(h("span", { class: "mini-batch" }, batch.batchNo));
      });
      chain.append(icon("arrow-right", 16), h("span", { class: "chip chip-info" }, icon("plus", 14), "New batch"));
      parts.push(chain);
    } else if (tidy(name.input.value) && tidy(strength.input.value)) {
      parts.push(h("p", { style: "margin-top:12px" }, "No queue exists for this medicine yet. A new queue will start with this batch at the front."));
    }
    panel.replaceChildren(...parts);
  }

  // ----- validation -----
  const checks = {
    name: () => checkRequired(name.input.value, "Medicine name is required."),
    strength: () => checkRequired(strength.input.value, "Strength is required. Example: 500 mg."),
    batchNo: () => {
      const missing = checkRequired(batchNo.input.value, "Batch number is required. Example: B-2041.");
      if (missing) return missing;
      const med = findMedicine();
      const entered = tidy(batchNo.input.value);
      if (med && med.batches.some((batch) => batch.batchNo.toLowerCase() === entered.toLowerCase())) {
        return `Batch number ${entered} already exists for this medicine. Use a different number.`;
      }
      return "";
    },
    quantity: () => checkQuantity(quantity.input.value),
    price: () => checkPrice(price.input.value),
    expiry: () => checkExpiry(expiry.input.value)
  };
  const fields = { name, strength, batchNo, quantity, price, expiry };

  function validateOne(key) {
    const message = checks[key]();
    fields[key].setError(message);
    return message;
  }

  Object.keys(fields).forEach((key) => {
    const input = fields[key].input;
    input.addEventListener("blur", () => validateOne(key));
    input.addEventListener("input", () => {
      if (input.getAttribute("aria-invalid") === "true") validateOne(key);
    });
  });

  [name, strength].forEach((f) => f.input.addEventListener("input", () => {
    refreshLists();
    refreshPanel();
    if (batchNo.input.getAttribute("aria-invalid") === "true") validateOne("batchNo");
  }));

  // ----- submit -----
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    formNotice.replaceChildren();

    const firstBad = Object.keys(fields).filter((key) => validateOne(key)).shift();
    if (firstBad) {
      fields[firstBad].input.focus();
      return;
    }

    setBusy(submit, true, "Adding batch");
    const entered = tidy(batchNo.input.value);
    try {
      const result = await api.addBatch({
        medicineName: tidy(name.input.value),
        strength: tidy(strength.input.value),
        batchNo: entered,
        quantity: Number(quantity.input.value),
        unitPrice: Number(price.input.value),
        expiryDate: expiry.input.value
      });
      state.justAdded = { key: result.medicine.key, batchNo: entered };
      try { stock = await api.getStock(); } catch (ignored) { /* the list refreshes on the next page load */ }

      Object.values(fields).forEach((f) => { f.input.value = ""; f.setError(""); });
      refreshLists();
      refreshPanel();
      formNotice.replaceChildren(notice("success",
        h("p", {}, result.message),
        h("p", {}, h("a", { href: "#/inventory" }, "View in Inventory"))));
      toast(result.message);
      name.input.focus();
    } catch (error) {
      if (error.status === 409) batchNo.setError(error.message);
      formNotice.replaceChildren(notice("error", error.message));
    } finally {
      setBusy(submit, false);
    }
  });

  refreshLists();
  refreshPanel();
}
