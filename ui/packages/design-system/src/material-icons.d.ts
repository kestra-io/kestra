declare module "vue-material-design-icons/*.vue" {
    import {Component} from "vue"
    const icon: Component
    export default icon
}

declare module "*.png" {
    const url: string
    export default url
}