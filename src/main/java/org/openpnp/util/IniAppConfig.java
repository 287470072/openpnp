package org.openpnp.util;


import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class IniAppConfig {
    private INIConfiguration config;
    private String iniFileName = "config.ini";

    public IniAppConfig() {
        Configurations configs = new Configurations();
        try {
            config = configs.ini(iniFileName);
        } catch (ConfigurationException e) {
            System.out.println("配置文件不存在，将创建新的配置文件。");
            try {
                config = new INIConfiguration();
                // 使用FileWriter来保存配置到文件
                try (FileWriter writer = new FileWriter(iniFileName)) {
                    config.write(writer);
                }
            } catch (ConfigurationException | IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void setProperty(String section, String key, String value) {
        config.setProperty(section + "." + key, value);
        try {
            // 使用FileWriter来保存配置到文件
            try (FileWriter writer = new FileWriter(iniFileName)) {
                config.write(writer);
            }
        } catch (ConfigurationException | IOException e) {
            e.printStackTrace();
        }
    }

    public String getProperty(String section, String key) {
        return config.getString(section + "." + key);
    }
}
