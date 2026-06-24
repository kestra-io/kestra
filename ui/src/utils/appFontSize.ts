import {APP_FONT_SIZE_KEY, BASE_PX, type AppFontSizeMode} from "@kestra-io/design-system"

export {APP_FONT_SIZE_KEY, BASE_PX, type AppFontSizeMode}

const SCALE: Record<AppFontSizeMode, number> = {
    small: 12 / 14,
    medium: 1,
    large: 16 / 14,
}

export function appFontSizeInfo(mode: AppFontSizeMode): {scale: number; base: number} {
    return {scale: SCALE[mode], base: BASE_PX[mode]}
}

export function getAppFontSizeMode(): AppFontSizeMode {
    const stored = localStorage.getItem(APP_FONT_SIZE_KEY)
    if (stored === "small" || stored === "medium" || stored === "large") {
        return stored
    }
    return "medium"
}

export function applyFontScale(mode: AppFontSizeMode): void {
    document.documentElement.style.setProperty("--ks-font-scale", String(appFontSizeInfo(mode).scale))
}
