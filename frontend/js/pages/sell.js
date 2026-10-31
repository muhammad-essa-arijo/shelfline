import * as api from "../api.js";
import { h, units, money, formatDate, sleep, motionMs, chip } from "../util.js";
import { icon } from "../icons.js";
import { pageHeader, loadView, skeletonLines, field, selectField, notice, toast, setBusy, emptyState } from "../ui.js";
import { renderLane, findCard, cardKey, captureLayout, restoreLayout } from "../components/lane.js";
import { checkQuantity } from "../validate.js";

export function renderSell(root, query) {
  root.append(pageHeader("Sell stock", "A sale takes units from the front batch first. Empty batches leave the queue."));
  const body = h("div", { class: "stack" });
  root.append(body);
  loadView(body, () => skeletonLines(5), api.getStock, (items) => build(body, items, query));
}

function build(body, items, query) {
  if (items.length === 0) {
    body.append(h("div", { class: "card" }, emptyState({
      title: "Nothing to sell yet", text: "Add a batch first, then come back to sell stock.", actionLabel: "Add batch", href: "#/add"
    })));
    return;
  }

  let stock = items;
  let plan = null;
  let latest = 0;       // ignores old preview answers that arrive late
  let timer = null;
  let working = false;

  const medicine = selectField({
    id: "sell-medicine", label: "Medicine",
    options: [{ value: "", label: "Select a medicine" }].concat(
      stock.map((med) => ({ value: med.key, label: `${med.key} (${units(med.totalUnits)})` })))
  });
  const quantity = field({ id: "sell-qty", label: "Quantity (units)", type: "number", attrs: { min: "1", step: "1", inputmode: "numeric" } });
  const confirm = h("button", { class: "btn btn-primary", type: "button", disabled: true }, icon("cart", 16), "Confirm sale");

  const previewBox = h("div", { "aria-live": "polite" });
  const laneBox = h("div", {});

  body.append(
    h("div", { class: "two-col" },
      h("div", { class: "card" },
        h("div", { class: "form-grid" }, medicine.wrap, quantity.wrap),
        h("div", { class: "form-actions" }, confirm)),
      h("div", { class: "card" }, h("div", { class: "card-head" }, h("h2", {}, "Sale preview")), previewBox)),
    laneBox);

  const preselect = query.get("medicine");
  if (preselect && stock.some((med) => med.key === preselect)) medicine.input.value = preselect;

  // ----- drawing -----
  function drawLane(consume, animate = false) {
    const med = stock.find((entry) => entry.key === medicine.input.value);
    const layout = animate ? captureLayout(laneBox) : null;
    laneBox.replaceChildren();
    if (!med) return;
    laneBox.append(h("div", { class: "lanes" }, renderLane(med, { readonly: true, consume })));
    if (layout) restoreLayout(laneBox, layout);
  }

  function showHint() {
    previewBox.replaceChildren(h("p", {}, "Select a medicine and enter a quantity to see which batches will be used."));
  }

  function showPlan(result) {
    const rows = result.lines.map((line) => h("tr", {},
      h("td", { class: "strong" }, line.batchNo),
      h("td", {}, formatDate(line.expiryDate)),
      h("td", { class: "right" }, units(line.unitsTaken)),
      h("td", { class: "right" }, line.willBeEmpty
        ? chip("info", "Empty, removed", "info")
        : `${(line.batchQuantityBefore - line.unitsTaken).toLocaleString("en-US")} left`),
      h("td", { class: "right" }, money(line.lineValue))));

    previewBox.replaceChildren(
      h("div", { class: "table-wrap" },
        h("table", { class: "compact" },
          h("thead", {}, h("tr", {},
            h("th", {}, "Batch"), h("th", {}, "Expiry"),
            h("th", { class: "right" }, "Units used"), h("th", { class: "right" }, "Left in batch"),
            h("th", { class: "right" }, "Value"))),
          h("tbody", {}, rows))),
      h("div", { class: "preview-total" },
        h("span", {}, `Total: ${units(result.requestedQuantity)}, ${money(result.totalValue)}`),
        h("span", {}, `Stock left after sale: ${units(result.remainingTotal)}`)));
  }

  function consumeMap(result) {
    const map = new Map();
    result.lines.forEach((line) => map.set(line.batchNo, { units: line.unitsTaken, empty: line.willBeEmpty }));
    return map;
  }

  // ----- preview -----
  async function updatePreview() {
    plan = null;
    confirm.disabled = true;
    quantity.setError("");

    if (!medicine.input.value) {
      drawLane(null);
      showHint();
      return;
    }
    drawLane(null);

    const raw = quantity.input.value;
    if (raw.trim() === "") { showHint(); return; }
    const problem = checkQuantity(raw);
    if (problem) {
      quantity.setError(problem);
      previewBox.replaceChildren();
      return;
    }

    const mine = ++latest;
    previewBox.replaceChildren(h("div", { class: "skeleton skel-line" }), h("div", { class: "skeleton skel-line" }));
    try {
      const result = await api.previewSale({ medicine: medicine.input.value, quantity: Number(raw) });
      if (mine !== latest) return;
      plan = result;
      showPlan(result);
      drawLane(consumeMap(result));
      confirm.disabled = false;
    } catch (error) {
      if (mine !== latest) return;
      previewBox.replaceChildren(notice("error", error.message));
    }
  }

  function scheduleUpdate() {
    clearTimeout(timer);
    latest++;
    timer = setTimeout(updatePreview, 300);
  }

  medicine.input.addEventListener("change", () => { clearTimeout(timer); updatePreview(); });
  quantity.input.addEventListener("input", scheduleUpdate);

  // ----- confirm -----
  confirm.addEventListener("click", async () => {
    if (!plan || working) return;
    working = true;
    const key = medicine.input.value;
    const requested = plan.requestedQuantity;
    setBusy(confirm, true, "Selling");
    try {
      const result = await api.sell({ medicine: key, quantity: requested });

      // Emptied batches slide out at the front, then the lane shifts left.
      result.batchesDequeued.forEach((batchNo) => {
        const card = findCard(laneBox, cardKey(key, batchNo));
        if (card) card.classList.add("leaving");
      });
      if (result.batchesDequeued.length) await sleep(motionMs());

      stock = await api.getStock();
      latest++;
      plan = null;
      quantity.input.value = "";
      drawLane(null, true);
      previewBox.replaceChildren(notice("success",
        h("p", {}, result.message),
        h("p", {}, `Total ${money(result.totalValue)}. Stock left: ${units(result.remainingTotal)}.`)));
      toast(result.message);
    } catch (error) {
      previewBox.replaceChildren(notice("error", error.message));
      toast(error.message, "error");
    } finally {
      setBusy(confirm, false);
      confirm.disabled = true;
      working = false;
    }
  });

  drawLane(null);
  if (medicine.input.value) updatePreview(); else showHint();
}
