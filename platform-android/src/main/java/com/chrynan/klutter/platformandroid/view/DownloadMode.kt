package com.chrynan.klutter.platformandroid.view

// Controls when to check if a new patch is available for download, and start downloading.
// Note that by default the application will not block to wait for the download to finish.
// Patches are downloaded in the background, but the developer can also use [InstallMode]
// to control whether to block on download completion, in order to install patches sooner.
enum class DownloadMode {

    // Check for and download patch on application restart (but not necessarily apply it).
    // This is the default setting which will also check for new patches least frequently.
    ON_RESTART,

    // Check for and download patch on application resume (but not necessarily apply it).
    // By definition, this setting will check for new patches both on restart and resume.
    ON_RESUME
}