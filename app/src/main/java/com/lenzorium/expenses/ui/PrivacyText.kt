package com.lenzorium.expenses.ui

/** In-app copy of the privacy policy (the public version is PRIVACY.md in the source repository). */
object PrivacyText {
    const val UPDATED = "31 October 2026"

    val sections: List<Pair<String, String>> = listOf(
        "Summary" to
            "Lenzorium Expenses keeps your financial records on your phone. The app has no account, no servers, no advertising and no analytics, and it never sends your data to us.",
        "What the app stores" to
            "The transactions, loans, categories, settings and receipt photos you enter. They are saved in the app's private storage on your device, which other apps can't read.",
        "Google backup" to
            "If Android backup is turned on in your phone's settings, Android may include the app's data in your personal Google account backup, so it can be restored when you set up a new phone. Google encrypts this backup, and we can't access it. You can turn it off in Android Settings › Google › Backup.",
        "Files you create" to
            "Backups and PDF/CSV reports are created only when you tap the button, and saved where you choose. Keep backup files private: they contain your full records.",
        "Permissions" to
            "Biometrics (optional) to unlock the app with your fingerprint or face. The camera opens only when you choose to photograph a receipt. The app doesn't use the internet.",
        "Deleting your data" to
            "Delete individual entries in the app, or uninstall the app to remove everything from your phone. Backup files you saved yourself must be deleted separately.",
        "Children" to
            "The app is not directed at children under 13.",
        "Changes" to
            "If this policy changes, the updated version will be published with the app update.",
    )
}
