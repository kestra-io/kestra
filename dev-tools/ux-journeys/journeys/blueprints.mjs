import {settle} from "../lib.mjs"

export const journey = {
    id: "blueprints",
    title: "Partir d'un blueprint (hors ligne)",
    summary: "L'utilisateur cherche un modèle de flow dans les Blueprints, depuis la sidebar puis depuis le panneau Blueprints de l'éditeur. Les blueprints communautaires sont chargés depuis api.kestra.io, injoignable dans cet environnement : les deux points d'entrée affichent une page 404 générique.",
    category: "Création",
    area: "Blueprints",
    tags: ["blueprints", "modèles", "hors ligne", "erreur", "404"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Entrée Blueprints dans la sidebar",
            caption: "Dans la section Resources de la sidebar, l'utilisateur survole l'entrée « Blueprints », sous Namespaces, Assets, Cases et Plugins.",
            screen: "Namespaces › Liste",
            url: "/ui/main/namespaces",
            elements: ["Sidebar", "File tree"],
            observation: ""
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Page 404 au lieu du catalogue",
            caption: "L'URL /ui/main/blueprints/flow/community affiche « Page not found – The requested URL was not found on this server » avec un bouton « Back to dashboard ». Les appels à api.kestra.io/v1/blueprints ont échoué en arrière-plan.",
            screen: "Blueprints › Liste",
            url: "/ui/main/blueprints/flow/community",
            elements: ["Empty state", "Button", "Sidebar"],
            observation: "Une panne réseau vers le catalogue distant est présentée comme une URL inexistante : l'utilisateur ne peut pas savoir que le problème vient de la connectivité, ni réessayer."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Panneau Blueprints de l'éditeur",
            caption: "Dans l'éditeur d'un nouveau flow, l'onglet Blueprints ouvre un panneau à droite qui affiche la même page 404 ; le titre de la page dans l'en-tête devient lui aussi « Page not found » alors que l'éditeur reste utilisable.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new",
            elements: ["Split view", "Code editor", "Empty state", "Tabs", "Tooltip"],
            observation: "L'erreur d'un panneau secondaire remplace le titre de toute la page ; le bouton « Back to dashboard » dans un panneau latéral ferait quitter l'éditeur."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur « Blueprints »"},
        {from: "s2", to: "s3", label: "Création d'un flow puis onglet « Blueprints »"}
    ]
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/namespaces")
    await settle(page, 1200)
    const sidebarEntry = page.locator("a[href=\"/ui/main/blueprints/flow/community\"]").first()
    await sidebarEntry.hover()
    await shot("s1", {wait: 600})

    await sidebarEntry.click()
    await shot("s2", {wait: 2500})

    await page.goto("/ui/main/flows/new")
    await settle(page, 1500)
    await page.getByText("Blueprints", {exact: true}).filter({visible: true}).last().click()
    await shot("s3", {wait: 3000})
}
