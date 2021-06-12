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

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Base class for config classes, whose instances read their properties from YAML files on the classpath.
 * 
 * @author ProjectJinxers
 */
public class YamlConfig<Y> {

    public final Y root;

    /**
     * Constructor.
     * 
     * @param filePath the path to the YAML file (passed to {@link ClassLoader#getResourceAsStream(String)})
     */
    public YamlConfig(String filePath, Class<Y> yamlClass) {
        Yaml yaml = new Yaml(new Constructor(yamlClass));
        InputStream is = getClass().getClassLoader().getResourceAsStream(filePath);
        root = yaml.loadAs(is, yamlClass);
    }

}
