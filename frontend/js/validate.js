import { todayISO } from "./util.js";

// Each check returns an error message, or an empty string when the value is fine.

export function checkRequired(raw, message) {
  return String(raw ?? "").trim() === "" ? message : "";
}

export function checkQuantity(raw) {
  const text = String(raw ?? "").trim();
  if (text === "") return "Quantity must be 1 or more.";
  if (!/^\d+$/.test(text)) return "Quantity must be a whole number, such as 120.";
  if (Number(text) < 1) return "Quantity must be 1 or more.";
  return "";
}

export function checkPrice(raw) {
  const text = String(raw ?? "").trim();
  const number = Number(text);
  if (text === "" || !Number.isFinite(number) || number <= 0) return "Unit price must be above Rs. 0.00.";
  return "";
}

export function checkExpiry(raw, allowPast = false) {
  if (!raw) return "Expiry date is required.";
  if (!allowPast && raw < todayISO()) return "Expiry date is in the past. Enter today or a later date.";
  return "";
}
