import {settle} from "../lib.mjs"

export const journey = {
    id: "run-execution",
    title: "Exécuter un flow avec des inputs",
    summary: "Depuis la page d'un flow, l'utilisateur lance une exécution via la boîte de dialogue des inputs, suit son avancement en direct dans le Gantt puis consulte topologie, logs et outputs. Une branche montre la même exécution forcée en échec.",
    category: "Exécution",
    area: "Executions",
    tags: ["execute", "inputs", "gantt", "logs", "outputs", "échec"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Vue d'ensemble du flow",
            caption: "La page Overview de process_orders affiche des KPI (Success Ratio, Failed Ratio, In Progress, Pending), la description, un donut des exécutions par statut et un graphique dans le temps. L'utilisateur clique sur « Execute ».",
            screen: "Flow › Vue d'ensemble",
            url: "/ui/main/flows/edit/company.data/process_orders/overview",
            elements: ["Chart", "Card", "Filter bar", "Tabs", "Breadcrumb", "Button"],
            observation: ""
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Dialogue des inputs",
            caption: "La modale « Execute the flow process_orders » propose les onglets Inputs, Other properties et cURL command. batch_size est un champ numérique (120) et simulate_failure un interrupteur, chacun avec sa description.",
            screen: "Dialog › Exécuter",
            url: "/ui/main/flows/edit/company.data/process_orders/overview",
            elements: ["Modal", "Form", "Tabs", "Button"],
            observation: "L'interrupteur booléen n'a pas de libellé on/off : seul l'état visuel indique la valeur. L'onglet cURL est un bon pont vers l'API."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Exécution en cours (Gantt)",
            caption: "Après validation, l'exécution s'ouvre sur l'onglet Gantt : statut « Running », barre « Execution progress » avec temps restant estimé, et tâches extract_orders, quality_checks, validate_schema en cours.",
            screen: "Exécution › Gantt",
            url: "/ui/main/executions/company.data/process_orders/<id>/gantt",
            elements: ["Gantt", "Status badge", "Filter bar", "Tabs", "Breadcrumb", "Button"],
            observation: "Suivi live avec estimation du temps restant ; le bouton d'en-tête devient « Pause » pendant l'exécution."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Topologie de l'exécution réussie",
            caption: "L'onglet Overview montre le statut « Success », les labels, la durée, 7/7 tâches et le graphe de topologie avec les blocs Parallel et If, puis des liens Previous / Next execution.",
            screen: "Exécution › Topologie",
            url: "/ui/main/executions/company.data/process_orders/<id>/overview",
            elements: ["Topology graph", "Status badge", "Tag", "Tabs", "Button"],
            observation: "Le graphe est rendu très petit dans l'espace disponible : les noms de tâches sont illisibles sans zoomer."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Logs de l'exécution",
            caption: "L'onglet Logs regroupe les lignes par tâche (extract_orders, quality_checks, validate_schema…) avec le niveau, l'horodatage, le statut et le sélecteur d'attempt. Des boutons permettent de tout replier ou de passer en « Temporal view ».",
            screen: "Exécution › Logs",
            url: "/ui/main/executions/company.data/process_orders/<id>/logs",
            elements: ["Logs", "Filter bar", "Status badge", "Tabs", "Button"],
            observation: ""
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Inputs et outputs",
            caption: "L'onglet Input/Output liste les inputs du flow et les outputs des tâches. La sélection de summarize affiche ses valeurs en JSON (processed_orders, rejected_orders, status) et pré-remplit l'expression {{ outputs.summarize.values }} dans le panneau « Debug Expression ».",
            screen: "Exécution › Outputs",
            url: "/ui/main/executions/company.data/process_orders/<id>/outputs",
            elements: ["Split view", "Code editor", "Search", "Card", "Button"],
            observation: "L'expression Pebble correspondant à la sortie sélectionnée est générée automatiquement, ce qui aide à la réutiliser dans le flow."
        },
        {
            key: "s7",
            file: "07.jpg",
            title: "Relance avec échec simulé",
            caption: "Retour sur le flow et nouvelle ouverture du dialogue : l'interrupteur simulate_failure est activé avant de cliquer sur « Execute ».",
            screen: "Dialog › Exécuter",
            url: "/ui/main/flows/edit/company.data/process_orders/overview",
            elements: ["Modal", "Form", "Button"],
            observation: ""
        },
        {
            key: "s8",
            file: "08.jpg",
            title: "Exécution en échec (Gantt)",
            caption: "L'exécution passe en « Failed » : validation_gate et reject_batch sont en rouge. Le Gantt propose « Go to first failure », un filtre « Failed only » et un bouton « Fix with AI » ; l'en-tête affiche « Restart ».",
            screen: "Exécution › Gantt",
            url: "/ui/main/executions/company.data/process_orders/<id>/gantt",
            elements: ["Gantt", "Status badge", "Filter bar", "Button", "Tabs"],
            observation: "Raccourcis de diagnostic bien placés : « Go to first failure » et « Failed only » évitent de chercher la tâche fautive."
        },
        {
            key: "s9",
            file: "09.jpg",
            title: "Topologie de l'échec",
            caption: "L'onglet Overview affiche une alerte « Execution failed! » avec le dernier message d'erreur (« Validation failed: 3 orders have a negative amount ») au-dessus de la topologie, où le bloc If est marqué en échec.",
            screen: "Exécution › Topologie",
            url: "/ui/main/executions/company.data/process_orders/<id>/overview",
            elements: ["Alert", "Topology graph", "Status badge", "Button"],
            observation: "Le message d'erreur est remonté en haut de page, sans devoir ouvrir les logs."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur « Execute »"},
        {from: "s2", to: "s3", label: "Clic sur « Execute » (succès)"},
        {from: "s3", to: "s4", label: "Onglet « Overview » une fois terminé"},
        {from: "s4", to: "s5", label: "Onglet « Logs »"},
        {from: "s5", to: "s6", label: "Onglet « Input/Output » puis clic sur summarize"},
        {from: "s1", to: "s7", label: "Nouvel « Execute » avec simulate_failure activé"},
        {from: "s7", to: "s8", label: "Clic sur « Execute » (échec)"},
        {from: "s8", to: "s9", label: "Onglet « Overview »"}
    ]
}

const FLOW_PAGE = "/ui/main/flows/edit/company.data/process_orders/overview"

async function openExecutionTab(page, tab) {
    await page.locator(`a[href*="/executions/"][href*="/${tab}"]`).first().click()
}

export async function capture({page, shot}) {
    await page.goto(FLOW_PAGE)
    await shot("s1", {wait: 1500})

    await page.getByRole("button", {name: "Execute"}).first().click()
    await shot("s2", {wait: 1200})

    await page.locator("[data-test=execute-dialog-button]").click()
    await page.waitForURL(/executions\//)
    await page.waitForTimeout(1500)
    await shot("s3", {instant: true})

    await page.waitForTimeout(5000)
    await openExecutionTab(page, "overview")
    await shot("s4", {wait: 1500})

    await openExecutionTab(page, "logs")
    await shot("s5", {wait: 1500})

    await openExecutionTab(page, "outputs")
    await settle(page, 1200)
    await page.getByText("summarize", {exact: true}).first().click()
    await shot("s6", {wait: 1200})

    await page.goto(FLOW_PAGE)
    await settle(page, 1000)
    await page.getByRole("button", {name: "Execute"}).first().click()
    await settle(page, 1000)
    await page.locator("[data-testid=input-form-simulate_failure]").click()
    await shot("s7", {wait: 600})

    await page.locator("[data-test=execute-dialog-button]").click()
    await page.waitForURL(/executions\//)
    await page.waitForTimeout(6000)
    await shot("s8", {wait: 1000})

    await openExecutionTab(page, "overview")
    await shot("s9", {wait: 1500})
}
