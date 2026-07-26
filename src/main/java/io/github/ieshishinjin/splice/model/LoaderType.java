package io.github.ieshishinjin.splice.model;

/**
 * Supported Minecraft mod loader types.
 */
public enum LoaderType {
    FORGE,
    FABRIC,
    NEOFORGE,
    QUILT;

    public static LoaderType fromString(String s) {
        return switch (s.toLowerCase()) {
            case "forge" -> FORGE;
            case "fabric" -> FABRIC;
            case "neoforge" -> NEOFORGE;
            case "quilt" -> QUILT;
            default -> throw new IllegalArgumentException(
                    "Unknown loader: " + s + ". Supported: forge, fabric, neoforge, quilt");
        };
    }
}
