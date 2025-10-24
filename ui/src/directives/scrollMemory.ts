import type {Directive} from "vue";
import {useViewStateStore} from "../stores/viewState";

type ElWithState = HTMLElement & {
    __scrollKey?: string;
    __scrollHandler?: (e: Event) => void;
    __isWindow?: boolean;
};

function getWindowScrollTop(): number {
    return window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
}

function setWindowScrollTop(top: number) {
    window.scrollTo({top, behavior: "auto"});
}

function save(el: ElWithState, key?: string) {
    const store = useViewStateStore();
    const k = key ?? el.__scrollKey;
    if (!k) return;
    const top = el.__isWindow ? getWindowScrollTop() : el.scrollTop;
    store.saveScrollPosition(k, top);
}

function restore(el: ElWithState, key?: string) {
    const store = useViewStateStore();
    const k = key ?? el.__scrollKey;
    if (!k) return;
    const top = store.getScrollPosition(k);
    if (typeof top !== "number") return;

    // Try multiple frames in case content height isn't ready yet
    let attempts = 0;
    const maxAttempts = 6; // ~100ms worst case
    const apply = () => {
        attempts++;
        if (el.__isWindow) {
            setWindowScrollTop(top);
            if (attempts < maxAttempts) requestAnimationFrame(apply);
            return;
        }
        el.scrollTop = top;
        if (attempts < maxAttempts && Math.abs(el.scrollTop - top) > 1) {
            requestAnimationFrame(apply);
        }
    };
    requestAnimationFrame(apply);
}

export const scrollMemory: Directive<ElWithState, string> = {
    mounted(el, binding) {
        const key = binding.value;
        el.__scrollKey = key;
        el.__isWindow = !!binding.modifiers?.window;

        // Attach listener
        let ticking = false;
        el.__scrollHandler = () => {
            if (ticking) return;
            ticking = true;
            requestAnimationFrame(() => {
                save(el);
                ticking = false;
            });
        };
        if (el.__isWindow) {
            window.addEventListener("scroll", el.__scrollHandler, {passive: true});
        } else {
            el.addEventListener("scroll", el.__scrollHandler, {passive: true});
        }
        restore(el, key);
    },
    updated(el, binding) {
        const oldKey = el.__scrollKey;
        const newKey = binding.value;
        const newIsWindow = !!binding.modifiers?.window;
        if (el.__isWindow !== newIsWindow) {
            // Rebind listener if target type changed
            if (el.__scrollHandler) {
                (el.__isWindow ? window : el).removeEventListener("scroll", el.__scrollHandler);
            }
            el.__isWindow = newIsWindow;
            if (el.__scrollHandler) {
                (el.__isWindow ? window : el).addEventListener("scroll", el.__scrollHandler, {passive: true});
            }
        }
        if (oldKey !== newKey) {
            if (oldKey) save(el, oldKey);
            el.__scrollKey = newKey;
            restore(el, newKey);
        }
    },
    unmounted(el) {
        if (el.__scrollHandler) {
            (el.__isWindow ? window : el).removeEventListener("scroll", el.__scrollHandler);
            save(el);
        }
        delete el.__scrollHandler;
        delete el.__scrollKey;
        delete el.__isWindow;
    },
};

export default scrollMemory;
