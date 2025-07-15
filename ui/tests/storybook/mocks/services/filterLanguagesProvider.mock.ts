import loadFilterLanguages from "../../../../src/override/services/filterLanguagesProvider.ts";
import {fn} from "storybook/test";

export default fn(loadFilterLanguages).mockName("loadFilterLanguages");
