package org.bird.gateway;

import com.google.common.base.CharMatcher;

/**
 * @author bird-brother
 * @date 16:39 2021-2-26
 * @description TODO
 */
public class StringUtil {

    public static String trim(String value) {
        return CharMatcher.is('/').trimFrom(value);
    }

    public static String joinPath(String serviceUrlPrefix, String path){
        return StringUtil.trim(serviceUrlPrefix) + "/" + StringUtil.trim(path);
    }

}
