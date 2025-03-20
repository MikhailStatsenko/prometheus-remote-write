package ru.mirea.prometheus.exporter;

import lombok.Getter;

@Getter
public class SystemInfo {
    private final String group;
    private final String system;
    private final String env;
    private final String instance;

    public SystemInfo(String group, String system, String env) {
        this(group, system, env, null);
    }

    public SystemInfo(String group, String system, String env, String instance) {
        this.group = group;
        this.system = system;
        this.env = env;
        this.instance = instance;
    }
}
