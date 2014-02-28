package edu.ucsc.refactor.util;

import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
public class StringUtil {
    private StringUtil(){}

    public static String extractClassName(String content){
        final Iterable<String> chunks = Splitter.on(" ").split(content.substring(0, content.indexOf("{")));
        return Iterables.get(chunks, Iterables.indexOf(chunks, Predicates.equalTo("class")) + 1);
    }

    public static String extractFileName(String filePathName) {
        if ( filePathName == null )
            return null;

        int dotPos      = filePathName.lastIndexOf( '.' );
        int slashPos    = filePathName.lastIndexOf( '\\' );

        if ( slashPos == -1 ){
            slashPos = filePathName.lastIndexOf( '/' );
        }

        if ( dotPos > slashPos ){
            return filePathName.substring( slashPos > 0 ? slashPos + 1 : 0,
                    dotPos );
        }

        return filePathName.substring( slashPos > 0 ? slashPos + 1 : 0 );
    }


    public static String splitCamelCase(String s) {
        return s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        );
    }


    public static boolean isStringEmpty(String input){
        return ((input != null && input.length() == 0) || (input == null));
    }


    public static boolean equals(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

}
