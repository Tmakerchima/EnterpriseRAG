package com.tmakerchima.enterpriserag.model;

import java.util.Locale;

/**
 * Authorization context passed to every retrieval branch.
 *
 * <p>The tenant is never optional. An administrator can have broad ACL access
 * inside a tenant, but still cannot turn a missing tenant into a cross-tenant
 * search. This keeps the demo boundary aligned with a production data-access
 * rule: authorization is carried by the server-side context, not by a prompt.</p>
 */
public record EnterpriseAccessContext(String role, String tenantId, String department) {

    public EnterpriseAccessContext {
        role = normalizeRole(role);
        tenantId = normalizeTenant(tenantId);
        department = normalizeNullable(department);
    }

    public static EnterpriseAccessContext from(String requestedRole, String requestedTenantId) {
        String role = normalizeRole(requestedRole);
        String department = switch (role) {
            case "engineering", "finance", "hr" -> role;
            default -> null;
        };
        return new EnterpriseAccessContext(role, requestedTenantId, department);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    private static String normalizeRole(String value) {
        String role = value == null ? "public" : value.trim().toLowerCase(Locale.ROOT);
        return switch (role) {
            case "public", "engineering", "finance", "hr", "admin" -> role;
            default -> "public";
        };
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeTenant(String value) {
        String tenant = normalizeNullable(value);
        return tenant == null ? "default" : tenant;
    }
}
