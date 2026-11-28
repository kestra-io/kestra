import {visibleDropdownItems} from "../lib.mjs"

export const journey = {
    id: "system-alert-wizard",
    title: "Créer une alerte via l'assistant system flow",
    summary: "Nouveauté de develop : le namespace system propose un assistant en trois étapes (Trigger, Notify, Review) pour créer un flow d'alerte sur les échecs. L'utilisateur configure une alerte sur company, mais le flow généré est refusé par le validateur de l'éditeur.",
    category: "Configuration",
    area: "Namespaces",
    tags: ["system flow", "alerte", "assistant", "stepper", "validation", "nouveauté"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Assistant « Create a system flow »",
            caption: "Le namespace system s'ouvre sur l'entrée « Create a system flow » : un stepper Trigger › Notify › Review et un premier écran « When should this flow run? » avec quatre options (Execution state, Schedule, Webhook, Other trigger). Execution state est présélectionné, avec FAILED et WARNING cochés.",
            screen: "Namespace › Assistant system flow",
            url: "/ui/main/namespaces/edit/system/blueprints",
            elements: ["Onboarding", "Form", "Card", "Tag", "Button", "Sidebar"],
            observation: "Le libellé « Include sub-namespaces » est collé à sa description sans espace (« sub-namespacesFlows in child… »)."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Namespace surveillé choisi",
            caption: "Le namespace company est sélectionné dans « Watch namespace », avec les sous-namespaces inclus et les états FAILED et WARNING. L'utilisateur clique sur Next.",
            screen: "Namespace › Assistant system flow",
            url: "/ui/main/namespaces/edit/system/blueprints",
            elements: ["Form", "Dropdown", "Tag", "Button"],
            observation: ""
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Étape Notify",
            caption: "« How should we notify your team? » propose Slack, Microsoft Teams et Email, tous marqués « Plugin not installed » et grisés, ainsi qu'une option Custom. Un message orange indique déjà « Select at least one notification channel ».",
            screen: "Namespace › Assistant system flow",
            url: "/ui/main/namespaces/edit/system/blueprints",
            elements: ["Form", "Card", "Alert", "Button", "Onboarding"],
            observation: "L'erreur de validation s'affiche avant toute interaction, et trois canaux sur quatre sont inutilisables sur cette image sans plugins."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Canal Custom sélectionné",
            caption: "La carte Custom est cochée : une note précise qu'une tâche fictive sera ajoutée et devra être remplacée par un plugin de notification. Le bouton Next devient actif.",
            screen: "Namespace › Assistant system flow",
            url: "/ui/main/namespaces/edit/system/blueprints",
            elements: ["Form", "Card", "Button"],
            observation: ""
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Récapitulatif et aperçu YAML",
            caption: "L'étape Review résume l'alerte en une phrase (« When a flow in company (and sub-namespaces) reaches state FAILED, WARNING, notify via Custom ») et affiche l'aperçu YAML « LIVE » du flow, avec un bouton « Create flow ».",
            screen: "Namespace › Assistant system flow",
            url: "/ui/main/namespaces/edit/system/blueprints",
            elements: ["Code editor", "Card", "Button", "Onboarding"],
            observation: "La phrase de synthèse en langage naturel est un bon contrôle avant création."
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Flow pré-rempli dans l'éditeur",
            caption: "« Create flow » ouvre l'éditeur sur un nouveau flow non enregistré (id system-flow-alert-flow_XXXXXXX) ; l'en-tête signale « 1 Error(s) » et « Warning(s) detected » et le mot conditions est souligné en rouge.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new?recipePreset=true",
            elements: ["Code editor", "Split view", "Alert", "Button"],
            observation: "L'assistant produit un flow invalide sur cette version, et l'id affiché dans l'aperçu change à l'ouverture de l'éditeur."
        },
        {
            key: "s7",
            file: "07.jpg",
            title: "Détail de l'erreur",
            caption: "Le clic sur « 1 Error(s) » affiche : « triggers[0].conditions: Unrecognized field \"conditions\" (class io.kestra.plugin.core.trigger.Flow) ». Le panneau Docs montre la documentation du trigger Flow.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new?recipePreset=true",
            elements: ["Code editor", "Tooltip", "Split view"],
            observation: "Le parcours guidé se termine sur une erreur technique que l'utilisateur débutant doit corriger seul dans le YAML."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Choix du namespace « company »"},
        {from: "s2", to: "s3", label: "Clic sur « Next »"},
        {from: "s3", to: "s4", label: "Sélection de « Custom »"},
        {from: "s4", to: "s5", label: "Clic sur « Next »"},
        {from: "s5", to: "s6", label: "Clic sur « Create flow »"},
        {from: "s6", to: "s7", label: "Clic sur « 1 Error(s) »"},
        {from: "s4", to: "s3", label: "Clic sur « Back »"}
    ]
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/namespaces")
    await page.waitForTimeout(1000)
    await page.getByText("system", {exact: true}).first().click()
    await shot("s1", {wait: 2000})

    await page.locator(".kel-select").filter({visible: true}).first().click()
    await page.waitForTimeout(700)
    await visibleDropdownItems(page).filter({hasText: /^\s*company\s*$/}).first().click()
    await page.waitForTimeout(500)
    await shot("s2", {instant: true})

    await page.locator("[data-test=recipe-next-btn]").click()
    await page.waitForTimeout(1200)
    await shot("s3")

    await page.getByText("Custom", {exact: true}).first().click()
    await page.waitForTimeout(1000)
    await shot("s4", {instant: true})

    await page.locator("[data-test=recipe-next-btn]").click()
    await page.waitForTimeout(1500)
    await shot("s5")

    await page.locator("[data-test=recipe-create-btn]").click()
    await page.waitForTimeout(2000)
    await shot("s6", {wait: 1500, toasts: true})

    await page.getByText(/1 Error/).first().click()
    await page.waitForTimeout(1000)
    await shot("s7", {instant: true})
}
