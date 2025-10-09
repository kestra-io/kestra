import {FilterLanguage} from "../filterLanguage";

class DefaultFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new DefaultFilterLanguage();

    private constructor() {
        super(undefined, {});
    }
}

export default DefaultFilterLanguage.INSTANCE as FilterLanguage;