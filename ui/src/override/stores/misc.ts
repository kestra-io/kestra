import {defineStore} from "pinia"
import {useBaseMiscStore} from "../../composables/useBaseMisc"

export const useMiscStore = defineStore("misc", useBaseMiscStore)
