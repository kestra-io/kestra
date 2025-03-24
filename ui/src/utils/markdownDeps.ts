import markdownIt from "markdown-it";
import mark from "markdown-it-mark";
import meta from "markdown-it-meta";
import anchor from "markdown-it-anchor";
import container from "markdown-it-container";
import {fromHighlighter} from "@shikijs/markdown-it/core";
import {createHighlighterCore} from "shiki/core";
import githubDark from "shiki/themes/github-dark.mjs";
import githubLight from "shiki/themes/github-light.mjs";
import {linkTag} from "./markdown_plugins/link";
import yaml from "shiki/langs/yaml.mjs";
import python from "shiki/langs/python.mjs";
import javascript from "shiki/langs/javascript.mjs";
import {createOnigurumaEngine} from "shiki/engine-oniguruma.mjs";

const langs = [yaml, python, javascript]
const onigurumaEngine = createOnigurumaEngine(() => import("shiki/wasm"));

export {
    markdownIt,
    mark,
    meta,
    anchor,
    container,
    fromHighlighter,
    createHighlighterCore,
    githubDark,
    githubLight,
    linkTag,
    langs,
    onigurumaEngine
}