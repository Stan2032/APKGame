// Top-level build file.
//
// Deliberately empty of plugin declarations: each module declares its own
// plugins (resolved via pluginManagement + the version catalog). Combined with
// configure-on-demand, this lets `:core` (pure JVM engine + tests) build in
// environments that cannot reach Google's Maven repository — only `:app`
// needs AGP and the Android SDK.
