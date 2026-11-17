import "@vue/runtime-dom"

declare module "@vue/runtime-dom" {
    interface InputHTMLAttributes {
        webkitdirectory?: boolean | ""
    }
}
