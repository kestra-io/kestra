import {ref} from "vue"

export const designSystemLocale = ref("en")

export function setDesignSystemLocale(locale: string) {
    designSystemLocale.value = locale
}
