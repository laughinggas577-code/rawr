package com.rawr.automation.config;

import java.io.*;
import java.util.Properties;

/**
 * Simple config file for persisting module settings between sessions.
 * Stores key-value pairs in a .properties file in the Minecraft config directory.
 */
public class ModConfig {

    private final File configFile;
    private final Properties properties;

    public ModConfig(File configFile) {
        this.configFile = configFile;
        this.properties = new Properties();
    }

    public void load() {
        if (!configFile.exists()) return;

        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);
        } catch (IOException e) {
            System.err.println("[RawrAutomation] Failed to load config: " + e.getMessage());
        }
    }

    public void save() {
        try {
            configFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                properties.store(fos, "Rawr Automation Config");
            }
        } catch (IOException e) {
            System.err.println("[RawrAutomation] Failed to save config: " + e.getMessage());
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    public void setBoolean(String key, boolean value) {
        properties.setProperty(key, String.valueOf(value));
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void setInt(String key, int value) {
        properties.setProperty(key, String.valueOf(value));
    }

    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public void setString(String key, String value) {
        properties.setProperty(key, value);
    }
}
