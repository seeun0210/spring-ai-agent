package com.baedal.support.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "baedal.assistant")
public class AssistantProperties {

    private boolean scopeGuardEnabled = true;

    public boolean isScopeGuardEnabled() {
        return scopeGuardEnabled;
    }

    public void setScopeGuardEnabled(boolean scopeGuardEnabled) {
        this.scopeGuardEnabled = scopeGuardEnabled;
    }
}
