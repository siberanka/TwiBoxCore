package net.twilightnw.twiboxcore.shopbridge;

import java.util.Collection;
import java.util.function.Predicate;

/** Fail-closed all-of permission gate, kept independent for negative testing. */
public final class StrictPermissionGate {
    private StrictPermissionGate() {
    }

    public static boolean hasAll(Collection<String> permissions, Predicate<String> permissionCheck) {
        if (permissions == null || permissions.isEmpty() || permissionCheck == null) {
            return false;
        }
        for (String permission : permissions) {
            if (permission == null || permission.isBlank() || !permissionCheck.test(permission)) {
                return false;
            }
        }
        return true;
    }
}
