import { h, units, money, formatDate, statusKind, statusIcon, chip, reducedMotion } from "../util.js";
import { icon } from "../icons.js";

export const cardKey = (medicineKey, batchNo) => `${medicineKey}|${batchNo}`;

// One batch card. The front card carries the solid FRONT badge, the last card the outlined REAR badge.
export function renderBatchCard(med, batch, opts = {}) {
  const kind = statusKind(batch.status);
  const use = opts.consume ? opts.consume.get(batch.batchNo) : null;

  const badges = h("div", { class: "badges" },
    batch.front ? h("span", { class: "badge badge-front" }, "FRONT, sells next") : null,
    batch.rear ? h("span", { class: "badge badge-rear" }, "REAR") : null);

  const actions = [];
  if (!opts.readonly) {
    actions.push(h("button", {
      class: "btn btn-secondary btn-small", type: "button",
      "aria-label": `Edit batch ${batch.batchNo} of ${med.key}`,
      onclick: () => opts.onEdit(med, batch)
    }, icon("pencil", 16), "Edit"));
    if (batch.front) {
      actions.push(h("button", {
        class: "btn btn-danger btn-small", type: "button",
        "aria-label": `Remove batch ${batch.batchNo} from the front of ${med.key}`,
        onclick: () => opts.onRemove(med, batch)
      }, icon("trash", 16), "Remove"));
    }
  }

  return h("article", {
    class: `batch-card${batch.front ? " is-front" : ""}${use ? " will-use" : ""}`,
    "data-key": cardKey(med.key, batch.batchNo)
  },
    h("div", { class: "batch-body" },
      badges,
      h("h3", { class: "batch-no" }, batch.batchNo),
      h("dl", { style: "margin:0;display:flex;flex-direction:column;gap:4px" },
        h("div", { class: "kv" }, h("dt", {}, "Quantity"), h("dd", {}, units(batch.quantity))),
        h("div", { class: "kv" }, h("dt", {}, "Unit price"), h("dd", {}, money(batch.unitPrice))),
        h("div", { class: "kv" }, h("dt", {}, "Expiry"), h("dd", {}, formatDate(batch.expiryDate)))),
      use ? h("p", { class: "use-note" },
        use.empty ? `Uses all ${units(use.units)}, then removed` : `Uses ${use.units} of ${units(batch.quantity)}`) : null),
    h("div", { class: `strip st-${kind}` }, icon(statusIcon(batch.status), 16), batch.statusLabel),
    actions.length ? h("div", { class: "batch-actions" }, actions) : null);
}

function laneChips(med) {
  const chips = [];
  const expired = med.batches.filter((b) => b.status === "EXPIRED").length;
  const soon = med.batches.filter((b) => b.status === "EXPIRES_SOON").length;
  if (med.lowStock) chips.push(chip("low", "Low stock", "alert-triangle"));
  if (expired) chips.push(chip("expired", `${expired} expired`, "alert-circle"));
  if (soon) chips.push(chip("soon", `${soon} expiring soon`, "clock"));
  return chips;
}

// One lane: the medicine header and its batches from FRONT (left) to REAR (right).
export function renderLane(med, opts = {}) {
  const head = h("div", { class: "lane-head" },
    h("div", { class: "lane-title" },
      h("h2", { class: "lane-name" }, med.name),
      h("span", { class: "lane-strength" }, med.strength)),
    h("div", { class: "lane-meta" },
      h("span", { class: "lane-total" }, units(med.totalUnits)),
      laneChips(med),
      med.sellsNext ? h("span", { class: "lane-next" }, `Sells next: ${med.sellsNext}`) : null),
    opts.readonly ? null : h("div", { class: "lane-actions" },
      h("a", {
        class: "btn btn-secondary btn-small",
        href: `#/add?medicine=${encodeURIComponent(med.name)}&strength=${encodeURIComponent(med.strength)}`
      }, icon("plus", 16), "Add batch"),
      h("a", { class: "btn btn-secondary btn-small", href: `#/sell?medicine=${encodeURIComponent(med.key)}` },
        icon("cart", 16), "Sell stock")));

  let body;
  if (med.batches.length === 0) {
    body = h("div", { class: "lane-empty" },
      h("span", {}, "This queue has no batches."),
      opts.readonly ? null : h("a", {
        class: "btn btn-primary btn-small",
        href: `#/add?medicine=${encodeURIComponent(med.name)}&strength=${encodeURIComponent(med.strength)}`
      }, "Add batch"));
  } else {
    const track = h("div", {
      class: "lane-track", role: "region", tabindex: "0",
      "aria-label": `${med.key} queue, front to rear`, "data-lane": med.key
    });
    med.batches.forEach((batch, index) => {
      if (index > 0) track.append(h("div", { class: "queue-arrow" }, icon("arrow-right", 20)));
      track.append(renderBatchCard(med, batch, opts));
    });
    body = track;
  }
  return h("section", { class: "lane", "aria-label": med.key }, head, body);
}

export function findCard(container, key) {
  return [...container.querySelectorAll(".batch-card")].find((card) => card.dataset.key === key) || null;
}

// Remember where each card and each scrollable lane is, so the lane can shift smoothly after a change.
export function captureLayout(container) {
  const rects = new Map();
  container.querySelectorAll(".batch-card").forEach((card) => rects.set(card.dataset.key, card.getBoundingClientRect()));
  const scrolls = new Map();
  container.querySelectorAll(".lane-track").forEach((track) => scrolls.set(track.dataset.lane, track.scrollLeft));
  return { rects, scrolls };
}

export function restoreLayout(container, layout) {
  container.querySelectorAll(".lane-track").forEach((track) => {
    if (layout.scrolls.has(track.dataset.lane)) track.scrollLeft = layout.scrolls.get(track.dataset.lane);
  });
  if (reducedMotion()) return;
  container.querySelectorAll(".batch-card").forEach((card) => {
    const before = layout.rects.get(card.dataset.key);
    if (!before || typeof card.animate !== "function") return;
    const shift = before.left - card.getBoundingClientRect().left;
    if (Math.abs(shift) > 1) {
      card.animate([{ transform: `translateX(${shift}px)` }, { transform: "none" }], { duration: 250, easing: "ease-out" });
    }
  });
}

export function focusLane(container, medicineKey) {
  const track = [...container.querySelectorAll(".lane-track")].find((el) => el.dataset.lane === medicineKey);
  if (track) track.focus({ preventScroll: false });
}
