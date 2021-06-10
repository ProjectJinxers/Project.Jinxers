/*
 * Copyright (C) 2021 ProjectJinxers
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.config;

import java.util.Map;

/**
 * Corresponds to the test-secret-config.yml resource file.
 * 
 * @author ProjectJinxers
 */
public class TestSecretConfig extends YamlConfig {

    private static final String KEY_INFURA = "infura";
    private static final String KEY_INFURA_USER = "user";
    private static final String KEY_INFURA_PASS = "pass";

    private static TestSecretConfig sharedInstance;

    /**
     * @return the singleton shared instance
     */
    public static TestSecretConfig getSharedInstance() {
        if (sharedInstance == null) {
            sharedInstance = new TestSecretConfig();
        }
        return sharedInstance;
    }

    private String infuraUser;
    private String infuraPass;

    private TestSecretConfig() {
        super("test-secret-config.yml");
        Map<?, ?> infura = getRootObject(KEY_INFURA);
        this.infuraUser = (String) infura.get(KEY_INFURA_USER);
        this.infuraPass = (String) infura.get(KEY_INFURA_PASS);
    }

    /**
     * @return the Infura user (usually the project ID)
     */
    public String getInfuraUser() {
        return infuraUser;
    }

    /**
     * @return the Infura password (usually the project secret)
     */
    public String getInfuraPass() {
        return infuraPass;
    }

}
