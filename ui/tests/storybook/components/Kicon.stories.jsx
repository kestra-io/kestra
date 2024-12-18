import Kicon from "../../../src/components/Kicon.vue";
import LockOff from "vue-material-design-icons/LockOff.vue";

const meta = {
    title: "components/Kicon",
    component: Kicon,
}

export default meta;

export const Default = {
    render(){
        return <Kicon>
            <LockOff />
        </Kicon>
    }
}