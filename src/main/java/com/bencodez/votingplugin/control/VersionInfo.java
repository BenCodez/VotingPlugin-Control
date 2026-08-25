package com.bencodez.votingplugin.control;

/** Authoritative runtime application version sourced from the Maven-generated manifest. */
public final class VersionInfo {
    private VersionInfo() { }

    public static String applicationVersion() {
        String version = VersionInfo.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }
}
