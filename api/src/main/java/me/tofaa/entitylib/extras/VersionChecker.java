package me.tofaa.entitylib.extras;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import me.tofaa.entitylib.utils.VersionUtil;

public final class VersionChecker {

    private VersionChecker() {}


    /**
     * Throws when the running server is older than the version a feature needs.
     *
     * @param version the oldest server version the feature works on
     * @param message the message of the thrown exception
     */
    public static void verifyVersion(ServerVersion version, String message) {
        if (VersionUtil.isOlderThan(version)) {
            throw new InvalidVersionException(message);
        }
    }

}
