// Small shared state for the whole app.
export const state = {
  search: "",        // text typed in the top bar search
  justAdded: null    // { key, batchNo } so the Inventory page can animate the new batch
};
