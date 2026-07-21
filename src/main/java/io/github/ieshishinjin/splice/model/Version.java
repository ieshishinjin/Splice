package io.github.ieshishinjin.splice.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a Minecraft version (e.g., "1.20.1", "1.21").
 * Provides comparison and validation.
 */
public class Version implements Comparable<Version> {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(pre|rc|beta|alpha)(\\d+))?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final int preReleaseNum;
    private final String raw;

    public Version(String raw) {
        this.raw = Objects.requireNonNull(raw, "Version string must not be null");
        var matcher = VERSION_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Minecraft version format: " + raw);
        }
        this.major = Integer.parseInt(matcher.group(1));
        this.minor = Integer.parseInt(matcher.group(2));
        this.patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        this.preRelease = matcher.group(4);
        this.preReleaseNum = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
    }

    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }
    public String getRaw() { return raw; }

    public boolean isPreRelease() {
        return preRelease != null;
    }

    @Override
    public int compareTo(Version other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.patch, other.patch);
        if (cmp != 0) return cmp;

        // Pre-release versions are "less than" release versions
        if (this.preRelease == null && other.preRelease != null) return 1;
        if (this.preRelease != null && other.preRelease == null) return -1;
        if (this.preRelease != null && other.preRelease != null) {
            return Integer.compare(this.preReleaseNum, other.preReleaseNum);
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Version version)) return false;
        return major == version.major && minor == version.minor
                && patch == version.patch
                && Objects.equals(preRelease, version.preRelease)
                && preReleaseNum == version.preReleaseNum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease, preReleaseNum);
    }

    @Override
    public String toString() { return raw; }
}
