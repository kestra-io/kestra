import Utils from "./utils";

export function getUid(): string | null {
    return localStorage.getItem("uid");
}

export function ensureUid(): string {
    const existing = getUid();
    if (existing) return existing;

    const uid = Utils.uid();
    localStorage.setItem("uid", uid);
    return uid;
}

