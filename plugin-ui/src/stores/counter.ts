import { defineStore } from "pinia";

const _useStore = defineStore("store", {
  state: () => ({ count: 0 }),
  getters: {
    getCount(): number {
      return this.count;
    },
  },
  actions: {
    increment() {
      this.count++;
    },
  },
});

declare global {
  interface Window {
    useStore: typeof _useStore;
  }
}

export const useStore = window.useStore || _useStore;
