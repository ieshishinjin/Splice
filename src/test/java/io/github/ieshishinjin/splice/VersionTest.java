package io.github.ieshishinjin.splice;

import io.github.ieshishinjin.splice.model.Version;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VersionTest {

    @Test
    void testValidVersions() {
        assertEquals(1, new Version("1.20.1").getMajor());
        assertEquals(20, new Version("1.20.1").getMinor());
        assertEquals(1, new Version("1.20.1").getPatch());

        assertEquals(1, new Version("1.21").getMajor());
        assertEquals(21, new Version("1.21").getMinor());
        assertEquals(0, new Version("1.21").getPatch());

        assertEquals(1, new Version("1.0.0").getMajor());
    }

    @Test
    void testVersionComparison() {
        assertTrue(new Version("1.21").compareTo(new Version("1.20.1")) > 0);
        assertTrue(new Version("1.19.2").compareTo(new Version("1.20")) < 0);
        assertEquals(0, new Version("1.20").compareTo(new Version("1.20.0")));
    }

    @Test
    void testInvalidVersions() {
        assertThrows(IllegalArgumentException.class, () -> new Version("invalid"));
        assertThrows(IllegalArgumentException.class, () -> new Version(""));
        assertThrows(IllegalArgumentException.class, () -> new Version("a.b.c"));
    }

    @Test
    void testPreReleaseVersions() {
        Version pre = new Version("1.20-pre1");
        assertTrue(pre.isPreRelease());
        assertTrue(pre.compareTo(new Version("1.20")) < 0);
    }

    @Test
    void testLoaderType() {
        assertEquals(io.github.ieshishinjin.splice.model.LoaderType.FORGE,
                io.github.ieshishinjin.splice.model.LoaderType.fromString("forge"));
        assertEquals(io.github.ieshishinjin.splice.model.LoaderType.FABRIC,
                io.github.ieshishinjin.splice.model.LoaderType.fromString("fabric"));
        assertThrows(IllegalArgumentException.class,
                () -> io.github.ieshishinjin.splice.model.LoaderType.fromString("unknown"));
    }
}
