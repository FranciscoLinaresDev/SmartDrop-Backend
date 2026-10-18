
package com.smart.drop.iam.domain.model.repositories;

public interface AdminRoleGuard {

    void assertCanRemoveAdminRole(Integer adminRoleId);
}
