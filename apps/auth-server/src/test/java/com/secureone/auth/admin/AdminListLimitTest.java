package com.secureone.auth.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdminListLimitTest {

    @Test
    void defaultsAndClamps() {
        assertEquals(AdminListLimit.DEFAULT, AdminListLimit.newest(null).getPageSize());
        assertEquals(1, AdminListLimit.newest(0).getPageSize());
        assertEquals(1, AdminListLimit.newest(-5).getPageSize());
        assertEquals(250, AdminListLimit.newest(250).getPageSize());
        assertEquals(AdminListLimit.MAX, AdminListLimit.newest(999_999).getPageSize());
        assertEquals(0, AdminListLimit.newest(10).getPageNumber());
    }
}
