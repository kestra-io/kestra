import * as Utils from "./utils"
import {storageKeys} from "./constants"

export function getUid(): string | null {
    return localStorage.getItem(storageKeys.UID)
}

export function ensureUid(): string {
    const existing = getUid()
    if (existing) return existing

    const uid = Utils.uid()
    localStorage.setItem(storageKeys.UID, uid)
    return uid
}

