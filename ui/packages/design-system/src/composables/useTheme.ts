import {onMounted, onUnmounted, ref, type Ref} from "vue"

export function useTheme(): {isDark: Ref<boolean>} {
    const isDark = ref(false)
    let observer: MutationObserver | null = null

    function detect() {
        isDark.value = document.documentElement.classList.contains("dark")
    }

    onMounted(() => {
        detect()
        observer = new MutationObserver(detect)
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["class"],
        })
    })

    onUnmounted(() => {
        observer?.disconnect()
    })

    return {
        isDark: isDark,
    }
}
