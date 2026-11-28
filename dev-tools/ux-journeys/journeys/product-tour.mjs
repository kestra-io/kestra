const TOUR_STORAGE_KEY = "kestra.productTour.state"

export const journey = {
    id: "product-tour",
    title: "Suivre la visite guidée produit",
    summary: "La visite guidée en 14 étapes promet d'apprendre Kestra en moins de 5 minutes à partir d'un flow d'exemple. Sur cette image sans plugins, l'exemple utilise une tâche Python non installée : la visite bloque dès l'étape 2 sur une erreur de validation.",
    category: "Onboarding",
    area: "Setup",
    tags: ["visite guidée", "onboarding", "exemple", "erreur", "plugins manquants", "ai"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Accueil de la visite",
            caption: "Sur la page AI Copilot, la carte « Welcome to Kestra – Learn Kestra in under 5 minutes » liste les quatre objectifs (écrire et lancer un flow, récupérer d'un échec, déclencher sur événement, chaîner deux flows) avec « Start the tour » et « Skip tour ».",
            screen: "AI Copilot",
            url: "/ui/main/ai?tour=start",
            elements: ["Onboarding", "Card", "Button", "Search"],
            observation: "Promesse claire et engagement faible (« You can stop at any point »)."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Étape 1 : flow d'exemple",
            caption: "« Step 1 of 14 – Write and run a flow » : la carte explique que les flows peuvent être rédigés depuis un prompt, mais que la visite utilise un exemple prêt à l'emploi. Bouton « Use the example flow ».",
            screen: "AI Copilot",
            url: "/ui/main/ai",
            elements: ["Onboarding", "Card", "Button"],
            observation: "Une barre de progression segmentée (14 segments) situe l'utilisateur dans la visite."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Étape 2 : le flow dans l'éditeur",
            caption: "L'éditeur s'ouvre sur le flow tutorial/order_summary (une tâche HTTP Download et une tâche Python), mis en valeur par un halo violet. La carte explique les deux tâches et propose « Save and execute » ; l'en-tête affiche déjà « 1 Error(s) » et le type Python est souligné en rouge.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new?onboardingPreset=true",
            elements: ["Onboarding", "Code editor", "Card", "Alert", "Button"],
            observation: "L'exemple de la visite dépend du plugin scripts Python, absent de l'image, et d'un appel à dummyjson.com : l'erreur est visible avant même que l'utilisateur agisse."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Validation échouée",
            caption: "« Save and execute » déclenche une notification « Validation failed » : « Invalid type: io.kestra.plugin.scripts.python.Script » suivi d'une trace technique, avec un bouton « Fix with AI ».",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new?onboardingPreset=true",
            elements: ["Toast", "Onboarding", "Code editor", "Button"],
            observation: "Le message expose une chaîne de référence Java (FlowWithSource, ArrayList) à un utilisateur en pleine découverte du produit."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Fix with AI sous la visite",
            caption: "« Fix with AI » ouvre le panneau AI Copilot avec l'erreur pré-remplie, mais il reste grisé sous le voile de la visite ; la carte de l'étape 2 affiche l'erreur en rouge et la visite ne peut pas avancer.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/new?onboardingPreset=true",
            elements: ["Onboarding", "Drawer", "Alert", "Code editor"],
            observation: "Le recours proposé (Copilot) est masqué par l'overlay de la visite : l'utilisateur n'a d'autre issue que « Skip tour »."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur « Start the tour »"},
        {from: "s2", to: "s3", label: "Clic sur « Use the example flow »"},
        {from: "s3", to: "s4", label: "Clic sur « Save and execute »"},
        {from: "s4", to: "s5", label: "Clic sur « Fix with AI »"}
    ]
}

function nextButton(page) {
    return page.locator(".guide-card button.kel-button--primary").filter({visible: true}).first()
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/flows")
    await page.evaluate((key) => localStorage.removeItem(key), TOUR_STORAGE_KEY)
    await page.goto("/ui/main/ai?tour=start")
    await shot("s1", {wait: 2000})

    await nextButton(page).click()
    await shot("s2", {wait: 1500})

    await nextButton(page).click()
    await shot("s3", {wait: 2500})

    await nextButton(page).click()
    await page.waitForTimeout(2000)
    await shot("s4", {instant: true, toasts: true})

    await page.getByRole("button", {name: /Fix with AI/}).filter({visible: true}).first().click({force: true})
    await page.waitForTimeout(6000)
    await shot("s5", {instant: true, toasts: true})
}
