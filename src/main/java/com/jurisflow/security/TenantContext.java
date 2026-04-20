package com.jurisflow.security;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    public static void setCurrentTenantId(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getCurrentTenantId() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }
}
