/*
 * Copyright (C) 2021 ProjectJinxers
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.config;

import java.io.InputStream;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Base class for config classes, whose instances read their properties from YAML files on the classpath.
 * 
 * @author ProjectJinxers
 */
public class YamlConfig {

    private Map<String, ?> root;

    /**
     * Constructor.
     * 
     * @param filePath the path to the YAML file (passed to {@link ClassLoader#getResourceAsStream(String)})
     */
    public YamlConfig(String filePath) {
        Yaml yaml = new Yaml();
        InputStream is = getClass().getClassLoader().getResourceAsStream(filePath);
        root = yaml.load(is);
    }

    /**
     * Checks if the given root property exists.
     * 
     * @param key the key
     * @return true iff the root property exists
     */
    public boolean containsRootProperty(String key) {
        return root.containsKey(key);
    }

    /**
     * Convenience method for accessing a sub tree.
     * 
     * @param key the root key
     * @return the sub tree
     */
    public Map<?, ?> getRootObject(String key) {
        return (Map<?, ?>) root.get(key);
    }

    /**
     * Gets the value of a root property.
     * 
     * @param key the key
     * @return the value of the root property
     */
    public Object getRootValue(String key) {
        return root.get(key);
    }

}
