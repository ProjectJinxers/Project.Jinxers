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

/**
 * Corresponds to the secret-config.yml resource file.
 * 
 * @author ProjectJinxers
 */
public class SecretConfig extends YamlConfig<SecretConfig.Root> {

    static class Root {

        public Obfuscation obfuscation;

    }

    static class Obfuscation {

        public long[] params;

    }

    private static SecretConfig sharedInstance;

    /**
     * @return the singleton shared instance
     */
    public static SecretConfig getSharedInstance() {
        if (sharedInstance == null) {
            sharedInstance = new SecretConfig();
        }
        return sharedInstance;
    }

    private long[] obfuscationParams;

    private SecretConfig() {
        super("secret-config.yml", Root.class);
    }

    private SecretConfig(Root root) {
        super(root);
    }

    public long[] getObfuscationParams() {
        if (obfuscationParams == null) {
            obfuscationParams = root.obfuscation.params;
        }
        return obfuscationParams;
    }

    public SecretConfig subConfig(long[] obfuscationParams) {
        SecretConfig res = new SecretConfig(root);
        res.obfuscationParams = this.obfuscationParams;
        return res;
    }

}
