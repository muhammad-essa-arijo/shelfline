import { API_URL } from "./config.js";

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const SLOW_AFTER_MS = 3000;
const TIMEOUT_MS = 90000;

// Tells the page when a request has been waiting longer than 3 seconds.
const slowListeners = new Set();
let slowCount = 0;
export function onSlowChange(listener) {
  slowListeners.add(listener);
}
function announceSlow(isSlow) {
  slowListeners.forEach((listener) => listener(isSlow));
}

async function request(path, options = {}) {
  const controller = new AbortController();
  const timeoutTimer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let slowFired = false;
  const slowTimer = setTimeout(() => {
    slowFired = true;
    slowCount++;
    announceSlow(true);
  }, SLOW_AFTER_MS);

  try {
    let response;
    try {
      response = await fetch(API_URL + path, {
        method: options.method || "GET",
        headers: options.body ? { "Content-Type": "application/json" } : undefined,
        body: options.body ? JSON.stringify(options.body) : undefined,
        signal: controller.signal
      });
    } catch (error) {
      const message = error.name === "AbortError"
        ? "The server did not answer in time. Wait a moment, then select Retry."
        : "Cannot reach the server. Check your internet connection and the API address in js/config.js, then select Retry.";
      throw new ApiError(0, message);
    }

    if (!response.ok) {
      let message = `The server returned an error (${response.status}). Try again.`;
      try {
        const body = await response.json();
        if (body && body.message) message = body.message;
      } catch (ignored) { /* the body was not JSON, keep the default message */ }
      throw new ApiError(response.status, message);
    }

    if (options.blob) {
      const blob = await response.blob();
      const disposition = response.headers.get("Content-Disposition") || "";
      const match = /filename="?([^";]+)"?/.exec(disposition);
      return { blob, filename: match ? match[1] : null };
    }
    return await response.json();
  } finally {
    clearTimeout(timeoutTimer);
    clearTimeout(slowTimer);
    if (slowFired) {
      slowCount--;
      if (slowCount === 0) announceSlow(false);
    }
  }
}

const enc = encodeURIComponent;

export const getSummary = () => request("/api/summary");
export const getStock = () => request("/api/stock");
export const getAlerts = () => request("/api/alerts");
export const getActivity = () => request("/api/activity");
export const addBatch = (body) => request("/api/batches", { method: "POST", body });
export const updateBatch = (medicine, batchNo, body) =>
  request(`/api/batches/${enc(medicine)}/${enc(batchNo)}`, { method: "PUT", body });
export const removeFront = (medicine) => request(`/api/batches/${enc(medicine)}/front`, { method: "DELETE" });
export const previewSale = (body) => request("/api/sell/preview", { method: "POST", body });
export const sell = (body) => request("/api/sell", { method: "POST", body });
export const removeExpired = () => request("/api/maintenance/remove-expired", { method: "POST" });
export const downloadFile = (path) => request(path, { blob: true });
