import * as api from "../api.js";
import { state } from "../state.js";
import { h, units, sleep, motionMs, reducedMotion, todayISO } from "../util.js";
import { icon } from "../icons.js";
import { pageHeader, loadView, skeletonLanes, emptyState, field, notice, toast, modal, confirmDialog, setBusy } from "../ui.js";
import { renderLane, findCard, cardKey, captureLayout, restoreLayout, focusLane } from "../components/lane.js";
import { checkQuantity, checkPrice, checkExpiry } from "../validate.js";

export function renderInventory(root) {
  root.append(pageHeader("Inventory", "Each row is one queue. The batch on the left is at the front and sells first.", [
    h("a", { class: "btn btn-primary", href: "#/add" }, icon("plus", 16), "Add batch")
  ]));

  const filterNote = h("div", { class: "filter-note" });
  const lanes = h("div", { class: "lanes" });
  root.append(filterNote, lanes);

  let items = [];
  let working = false;

  function visible() {
    const text = state.search.trim().toLowerCase();
    return text ? items.filter((med) => med.key.toLowerCase().includes(text)) : items;
  }

  function clearSearch() {
    state.search = "";
    const input = document.getElementById("search");
    if (input) input.value = "";
    draw();
  }

  function draw({ animate = false } = {}) {
    const layout = animate ? captureLayout(lanes) : null;
    lanes.replaceChildren();
    filterNote.replaceChildren();

    const list = visible();
    if (items.length === 0) {
      lanes.append(emptyState({ title: "No medicines yet", text: "Add the first batch to create a queue.", actionLabel: "Add batch", href: "#/add" }));
    } else if (list.length === 0) {
      lanes.append(emptyState({
        title: `No medicine matches "${state.search.trim()}"`,
        text: "Check the spelling, or clear the search to see every queue.",
        actionLabel: "Clear search", onClick: clearSearch
      }));
    } else {
      if (state.search.trim()) {
        filterNote.append(
          h("span", {}, `Showing ${list.length} of ${items.length} medicines matching "${state.search.trim()}".`),
          h("button", { class: "btn btn-secondary btn-small", type: "button", onclick: clearSearch }, "Clear search"));
      }
      list.forEach((med) => lanes.append(renderLane(med, { onEdit: editBatch, onRemove: removeFront })));
    }

    if (layout) restoreLayout(lanes, layout);
    showJustAdded();
  }

  // A batch added on the Add page slides in at the rear when this page opens.
  function showJustAdded() {
    if (!state.justAdded) return;
    const { key, batchNo } = state.justAdded;
    state.justAdded = null;
    const card = findCard(lanes, cardKey(key, batchNo));
    if (!card) return;
    if (!reducedMotion()) card.classList.add("entering");
    const track = card.closest(".lane-track");
    if (track) track.scrollLeft = track.scrollWidth;
    card.scrollIntoView({ block: "nearest", inline: "nearest" });
  }

  async function removeFront(med, batch) {
    if (working) return;
    const ok = await confirmDialog({
      title: "Remove front batch",
      message: `Batch ${batch.batchNo} is at the front of ${med.key} and still has ${units(batch.quantity)}. Removing it cannot be undone.`,
      confirmLabel: "Remove batch",
      danger: true
    });
    if (!ok) return;

    working = true;
    const card = findCard(lanes, cardKey(med.key, batch.batchNo));
    try {
      if (card) {
        card.classList.add("leaving");
        await sleep(motionMs());
      }
      const result = await api.removeFront(med.key);
      items = await api.getStock();
      draw({ animate: true });
      focusLane(lanes, med.key);
      toast(result.message);
    } catch (error) {
      if (card) card.classList.remove("leaving");
      toast(error.message, "error");
    } finally {
      working = false;
    }
  }

  function editBatch(med, batch) {
    if (working) return;
    const qty = field({ id: "edit-qty", label: "Quantity (units)", type: "number", value: String(batch.quantity), attrs: { min: "1", step: "1", inputmode: "numeric" } });
    const price = field({ id: "edit-price", label: "Unit price (Rs.)", type: "number", value: String(batch.unitPrice), attrs: { min: "0.01", step: "0.01", inputmode: "decimal" } });
    const expiry = field({ id: "edit-expiry", label: "Expiry date", type: "date", value: batch.expiryDate });
    const formNotice = h("div", {});
    let handle = null;

    const form = h("form", { id: "edit-form", novalidate: true, style: "display:flex;flex-direction:column;gap:16px" },
      h("p", {}, `${med.key}, batch ${batch.batchNo}`),
      qty.wrap, price.wrap, expiry.wrap, formNotice);

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      formNotice.replaceChildren();
      const errors = [
        [qty, checkQuantity(qty.input.value)],
        [price, checkPrice(price.input.value)],
        [expiry, checkExpiry(expiry.input.value, true)]
      ];
      errors.forEach(([f, message]) => f.setError(message));
      const firstBad = errors.find(([, message]) => message);
      if (firstBad) { firstBad[0].input.focus(); return; }

      const unchanged = Number(qty.input.value) === batch.quantity
        && Number(price.input.value) === batch.unitPrice
        && expiry.input.value === batch.expiryDate;
      if (unchanged) {
        formNotice.replaceChildren(notice("info", "Change at least one value before saving."));
        return;
      }

      const saveBtn = handle.dialog.querySelector("button[type=submit]");
      setBusy(saveBtn, true, "Saving");
      try {
        const result = await api.updateBatch(med.key, batch.batchNo, {
          quantity: Number(qty.input.value),
          unitPrice: Number(price.input.value),
          expiryDate: expiry.input.value
        });
        handle.close();
        items = await api.getStock();
        draw({ animate: true });
        toast(result.message);
      } catch (error) {
        setBusy(saveBtn, false);
        formNotice.replaceChildren(notice("error", error.message));
      }
    });

    handle = modal({
      title: "Edit batch",
      body: form,
      buttons: [
        { label: "Cancel", kind: "secondary", onClick: (close) => close() },
        { label: "Save changes", kind: "primary", type: "submit", form: "edit-form" }
      ]
    });
  }

  document.addEventListener("shelfline:search", function onSearch() {
    if (!root.isConnected) {
      document.removeEventListener("shelfline:search", onSearch);
      return;
    }
    draw();
  });

  loadView(lanes, skeletonLanes, api.getStock, (data) => {
    items = data;
    draw();
  });
}
