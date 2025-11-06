package com.example.queuectl.shell;

import com.example.queuectl.service.JobService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.Map;

@ShellComponent
public class ConfigCommands {

    private final JobService jobService;

    public ConfigCommands(JobService jobService) {
        this.jobService = jobService;
    }

    @ShellMethod(key = "config get", value = "Get a configuration value. Example: config get max_retries")
    public String get(String key) {
        Map<String,Object> cfg = jobService.config();
        Object v = cfg.get(key);
        return v == null ? "(not set)" : key + " = " + v;
    }

    @ShellMethod(key = "config set", value = "Set a configuration value. Example: config set max_retries 5")
    public String set(String key, String value) {
        jobService.setConfig(key, value);
        return "Updated " + key + " = " + value;
    }
}
