package io.github.ieshishinjin.splice.model;

import java.util.Objects;

/**
 * Represents a single mapping entry linking an intermediate/obfuscated name
 * to a human-readable name, with metadata.
 */
public class MappingEntry {

    public enum MappingType {
        CLASS,
        METHOD,
        FIELD,
        PARAM
    }

    private final MappingType type;
    private final String intermediateName;   // SRG name (func_XXX) or intermediary name (method_XXX)
    private final String mappedName;         // MCP name or Yarn named
    private final String owner;              // Owner class (for methods/fields)
    private final String descriptor;         // Method/field descriptor
    private final String side;               // "client", "server", or null (MCP only)

    public MappingEntry(MappingType type, String intermediateName, String mappedName,
                        String owner, String descriptor, String side) {
        this.type = Objects.requireNonNull(type);
        this.intermediateName = Objects.requireNonNull(intermediateName);
        this.mappedName = Objects.requireNonNull(mappedName);
        this.owner = owner;
        this.descriptor = descriptor;
        this.side = side;
    }

    public MappingType getType() { return type; }
    public String getIntermediateName() { return intermediateName; }
    public String getMappedName() { return mappedName; }
    public String getOwner() { return owner; }
    public String getDescriptor() { return descriptor; }
    public String getSide() { return side; }

    public boolean isClientOnly() {
        return "client".equals(side);
    }

    /**
     * Returns true if the intermediate name is an obfuscated (notch) name
     * rather than a proper SRG/intermediary name.
     * Obfuscated names are typically short (1-3 chars), while SRG names
     * follow patterns like func_XXXX_X or are fully qualified class names.
     */
    public boolean isObfuscated() {
        String name = intermediateName;
        // SRG names have clear patterns
        if (name.startsWith("func_") || name.startsWith("field_") || name.startsWith("p_")) {
            return false;
        }
        // Class names with packages are not obfuscated
        if (name.contains("/") || name.contains(".")) {
            return false;
        }
        // Proper class names (capitalized)
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0))) {
            return false;
        }
        // Short names (1-3 chars) are likely obfuscated
        if (name.length() <= 3 && name.chars().allMatch(Character::isLetter)) {
            return true;
        }
        // Numeric or very short names
        return name.length() <= 2 || name.chars().allMatch(Character::isDigit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MappingEntry that)) return false;
        return type == that.type
                && Objects.equals(intermediateName, that.intermediateName)
                && Objects.equals(owner, that.owner)
                && Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, intermediateName, owner, descriptor);
    }

    @Override
    public String toString() {
        return type + "{" + intermediateName + " -> " + mappedName +
                (owner != null ? ", owner=" + owner : "") +
                (descriptor != null ? ", desc=" + descriptor : "") +
                (side != null ? ", side=" + side : "") + "}";
    }
}
