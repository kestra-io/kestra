declare module "*.vue" {
  import type {DefineComponent} from "vue"
  // Use Record<string, unknown> instead of {} to satisfy lint rules
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, any>
  export default component
}
