package com.invest;

import org.junit.jupiter.api.Test;

class SchedulerServiceApplicationTest {

    @Test
    void contextLoadsMainClass() {
        // Verifies the main class exists and is loadable
        var app = new SchedulerServiceApplication();
        assert app != null;
    }
}
