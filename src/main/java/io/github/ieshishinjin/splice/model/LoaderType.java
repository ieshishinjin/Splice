package io.github.ieshishinjin.splice.model;

/**
 * Supported Minecraft mod loader types.
 */
public enum LoaderType {
    FORGE,
    FABRIC,
    NEOFORGE;

    public static LoaderType fromString(String s) {
        return switch (s.toLowerCase()) {
            case "forge" -> FORGE;
            case "fabric" -> FABRIC;
            case "neoforge" -> NEOFORGE;
            default -> throw new IllegalArgumentException(
                    "Unknown loader: " + s + ". Supported: forge, fabric, neoforge");
        };
    }
}
