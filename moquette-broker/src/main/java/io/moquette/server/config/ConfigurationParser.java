/*
 * Copyright (c) 2012-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.moquette.server.config;

import io.moquette.log.Logger;
import io.moquette.log.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.Properties;

/**
 * Mosquitto 配置文件解析.
 * <p>
 * 每行一开始带有#的是注释；每行都是key value的格式，分隔符用空格
 */
class ConfigurationParser {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationParser.class);

    private Properties properties = new Properties();

    /**
     * Parse the configuration from file.
     */
    void parse(File file) throws ParseException {
        if (file == null) {
            LOG.warn(() -> "parsing NULL file, so fallback on default configuration!");
            return;
        }
        if (!file.exists()) {
            LOG.warn(() -> "parsing not existing file {}, so fallback on default configuration!", file.getAbsolutePath());
            return;
        }
        try {
            FileReader reader = new FileReader(file);
            parse(reader);
        } catch (FileNotFoundException fex) {
            LOG.warn(() -> "parsing not existing file {}, so fallback on default configuration!", file.getAbsolutePath(), fex);
            return;
        }
    }

    /**
     * 解析配置文件
     *
     * @throws ParseException 格式不兼容.
     */
    void parse(Reader reader) throws ParseException {
        if (reader == null) {
            // just log and return default properties
            LOG.warn(() -> "parsing NULL reader, so fallback on default configuration!");
            return;
        }

        BufferedReader br = new BufferedReader(reader);
        String line;
        try {
            while ((line = br.readLine()) != null) {
                int commentMarker = line.indexOf('#');
                if (commentMarker != -1) {
                    if (commentMarker == 0) {
                        // 跳过注释
                        continue;
                    } else {
                        // it's a malformed comment
                        throw new ParseException(line, commentMarker);
                    }
                } else {
                    if (line.isEmpty() || line.matches("^\\s*$")) {
                        // skip it's a black line
                        continue;
                    }

                    // split till the first space
                    int delimiterIdx = line.indexOf(' ');
                    String key = line.substring(0, delimiterIdx).trim();
                    String value = line.substring(delimiterIdx).trim();

                    properties.put(key, value);
                }
            }
        } catch (IOException ex) {
            throw new ParseException("Failed to read", 1);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    Properties getProperties() {
        return properties;
    }
}
