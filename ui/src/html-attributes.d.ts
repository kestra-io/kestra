// Vue's InputHTMLAttributes has no webkitdirectory, which every current browser reads to pick a folder.
import "@vue/runtime-dom"

declare module "@vue/runtime-dom" {
    interface InputHTMLAttributes {
        webkitdirectory?: boolean | ""
    }
}
