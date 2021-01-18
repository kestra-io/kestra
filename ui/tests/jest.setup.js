import {config} from "@vue/test-utils";
import "../src/filters"

String.prototype.hashCode = function () {
    let hash = 0;
    for (let i = 0; i < this.length; i++) {
        let character = this.charCodeAt(i);
        hash = ((hash << 5) - hash) + character;
        hash = hash & hash;
    }
    return hash;
}

config.mocks.$t = key => key;

