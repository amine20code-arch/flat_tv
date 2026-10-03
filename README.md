# Stream TV 1.1 (flat layout - all files in one folder)

Build the APK on GitHub: add the workflow (see the comment inside build-apk.yml), then Actions > Build APK > Run workflow.
Players: ExoPlayer (Media3) + VLC (libVLC). If the build fails resolving "libvlc-all:3.6.0", change the version to 3.5.1 in build.gradle.kts.
