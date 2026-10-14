export type AppFontSizeMode = "small" | "medium" | "large"

export const APP_FONT_SIZE_KEY = "appFontSize"

export const BASE_PX: Record<AppFontSizeMode, number> = {
    small: 12,
    medium: 14,
    large: 16,
}

export const MONO_BASE_PX: Record<AppFontSizeMode, number> = {
    small: 11,
    medium: 12,
    large: 14,
}
