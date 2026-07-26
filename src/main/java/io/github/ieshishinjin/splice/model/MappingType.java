package io.github.ieshishinjin.splice.model;

/**
 * Types of mappings supported by the tool.
 */
public enum MappingType {
    /** MCP mappings used by Minecraft Forge (searge -> MCP name) */
    MCP,
    /** Yarn mappings used by Fabric (intermediary -> named) */
    YARN;

    public boolean isForge() {
        return this == MCP;
    }

    public boolean isFabric() {
        return this == YARN;
    }

    public static MappingType fromLoader(LoaderType loader) {
        return switch (loader) {
            case FORGE, NEOFORGE -> MCP;
            case FABRIC, QUILT -> YARN;  // Quilt 和 Fabric 都用 Yarn
        };
    }
}
