import {breakpointsElement, useBreakpoints} from "@vueuse/core"

export const MOBILE_QUERY = "(max-width: 767px)"

export function useIsMobile() {
    return useBreakpoints(breakpointsElement).smaller("sm")
}
