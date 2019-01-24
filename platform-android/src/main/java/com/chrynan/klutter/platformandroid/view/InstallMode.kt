package com.chrynan.klutter.platformandroid.view

// Controls when to check that a new patch has been downloaded and needs to be applied.
enum class InstallMode {
    // Wait for next application restart before applying downloaded patch. With this
    // setting, the application will not block to wait for patch download to finish.
    // The application can be restarted later either by the user, or by the system,
    // for any reason, at which point the newly downloaded patch will get applied.
    // This is the default setting, and is the least disruptive way to apply patches.
    ON_NEXT_RESTART,

    // Apply patch as soon as it's downloaded. This will block to wait for new patch
    // download to finish, and will immediately apply it. This setting increases the
    // urgency with which patches are installed, but may also affect startup latency.
    // For now, this setting is only effective when download happens during restart.
    // Patches downloaded during resume will not get installed immediately as that
    // requires force restarting the app (which might be implemented in the future).
    IMMEDIATE
}