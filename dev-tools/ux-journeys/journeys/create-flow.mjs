import fs from "node:fs/promises"
import path from "node:path"
import {fileURLToPath} from "node:url"

import {pasteInEditor} from "../lib.mjs"

const HELLO_WORLD = path.join(path.dirname(fileURLToPath(import.meta.url)), "../seed/flows/hello_world.yml")

export const journey = {
    id: "create-flow",
    title: "Créer un premier flow",
    summary: "L'utilisateur part d'une liste de flows vide, ouvre l'éditeur, remplace le YAML d'exemple par un flow hello world avec un input et deux tâches Log, puis publie la révision. L'éditeur combine code, documentation contextuelle et topologie en panneaux.",
    category: "Création",
    area: "Flows",
    tags: ["yaml", "éditeur", "topologie", "publication", "brouillon"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Liste des flows vide",
            caption: "La page Flows affiche les colonnes du tableau (Id, Labels, Namespace, Last execution…) et un état vide « No flows found ». L'utilisateur clique sur le bouton violet « Create » en haut à droite.",
            screen: "Flows › Liste",
            url: "/ui/main/flows",
            elements: ["Table", "Empty state", "Filter bar", "Search", "Button", "Sidebar"],
            observation: "L'état vide n'offre aucune action (pas de bouton « Create » ni de lien vers les blueprints au centre) : l'utilisateur doit repérer le bouton en haut à droite."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Éditeur avec YAML par défaut",
            caption: "L'éditeur s'ouvre avec un flow d'exemple (id flow_XXXXXXX, namespace company.team, une tâche Log « Hello World! »). À droite, le panneau Docs « Flow documentation » propose un guide en trois étapes.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new",
            elements: ["Code editor", "Split view", "Tabs", "Button"],
            observation: "Le panneau Docs guide le débutant pas à pas (id & namespace, tâches, inputs)."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "YAML hello world saisi",
            caption: "Le YAML est remplacé par un flow hello_world avec labels, un input user_name et deux tâches Log. Le panneau Docs bascule automatiquement sur la documentation du plugin Log (Overview, Properties, Examples). Un point dans l'onglet signale des modifications non enregistrées.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new",
            elements: ["Code editor", "Split view", "Tabs"],
            observation: "La documentation suit le curseur : placer le curseur sur un type de tâche ouvre sa doc sans quitter l'éditeur."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Topologie du flow",
            caption: "Clic sur l'onglet Topology : le graphe affiche les deux tâches say_hello et log_summary reliées verticalement, à côté du code.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new",
            elements: ["Code editor", "Topology graph", "Split view", "Tabs", "Tooltip"],
            observation: "L'en-tête du panneau topologie affiche encore l'id généré (company.team / flow_XXXXXXX) alors que le YAML déclare hello_world : l'info n'est pas synchronisée avant enregistrement."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Menu de sauvegarde : Publish",
            caption: "Le bouton principal est « Save draft » ; sa flèche ouvre l'option « Publish », décrite comme rendant la révision live (triggers, appels par d'autres flows, alertes).",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new",
            elements: ["Dropdown", "Button", "Code editor"],
            observation: "La distinction brouillon / publication est expliquée directement dans le menu, mais l'action qui rend le flow exécutable est cachée derrière la flèche."
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Flow publié",
            caption: "Après « Publish », l'URL devient /flows/edit/company.team/hello_world, les onglets du flow apparaissent (Overview, Executions, Edit, Revisions…) et un toast « Published » confirme la révision 1. Le bouton « Execute » est maintenant disponible.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/edit/company.team/hello_world/edit",
            elements: ["Toast", "Code editor", "Topology graph", "Tabs", "Breadcrumb", "Button"],
            observation: "Le fil d'Ariane découpe le namespace en segments cliquables avec menus (company ▾ / team / hello_world ▾)."
        },
        {
            key: "s7",
            file: "07.jpg",
            title: "Vue d'ensemble du flow",
            caption: "L'onglet Overview du nouveau flow montre un état vide « Start automating with Kestra » invitant à cliquer sur Execute, et trois liens d'aide (Get Started, Workflow Components, Video Tutorials).",
            screen: "Flow › Vue d'ensemble",
            url: "/ui/main/flows/edit/company.team/hello_world/overview",
            elements: ["Empty state", "Tabs", "Breadcrumb", "Button", "Card"],
            observation: "Le bouton « Default Dashboard » porte un cadenas (fonction Enterprise) mais reste très visible en OSS."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur « Create »"},
        {from: "s2", to: "s3", label: "Collage du YAML"},
        {from: "s3", to: "s4", label: "Clic sur « Topology »"},
        {from: "s4", to: "s5", label: "Clic sur la flèche de « Save draft »"},
        {from: "s5", to: "s6", label: "Clic sur « Publish »"},
        {from: "s6", to: "s7", label: "Onglet « Overview »"}
    ]
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/flows")
    await shot("s1")

    await page.getByRole("link", {name: "Create"}).or(page.getByRole("button", {name: "Create"})).first().click()
    await page.waitForSelector(".monaco-editor")
    await shot("s2", {wait: 1500})

    await pasteInEditor(page, await fs.readFile(HELLO_WORLD, "utf8"))
    await page.waitForTimeout(1500)
    await page.keyboard.press("Escape")
    await shot("s3", {wait: 1500})

    await page.getByText("Topology", {exact: true}).first().click()
    await shot("s4", {wait: 2000})

    await page.getByRole("button", {name: "Save draft"}).locator("xpath=following::button[1]").click()
    await page.waitForTimeout(800)
    await shot("s5", {instant: true})

    await page.locator(".kel-dropdown-menu__item").filter({hasText: "Publish"}).first().click()
    await page.waitForTimeout(1500)
    await shot("s6", {instant: true, toasts: true})

    await page.goto("/ui/main/flows/edit/company.team/hello_world/overview")
    await shot("s7", {wait: 1500})
}
