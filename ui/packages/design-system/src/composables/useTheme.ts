import {onMounted, onUnmounted, ref} from "vue"

export function useTheme() {
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
        isDark,
    }
}
