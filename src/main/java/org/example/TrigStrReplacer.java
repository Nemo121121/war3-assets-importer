package org.example;

import java.util.regex.*;
import java.util.*;

public class TrigStrReplacer {

    public static void main(String[] args) {
        String sourceText = "Some text TRIGSTR_003 and another TRIGSTR_007 here.";
        String stringBlock = """
            STRING 3
            {
            Test sample for War3ModelImporter {coucou} string 9 {test}
            }
            STRING 7
            {
            Another sample string
            }
            """;

        String result = replaceTrigStr(sourceText, stringBlock);
        System.out.println(result);
    }

    public static String replaceTrigStr(String input, String stringBlock) {
        // Parse STRING blocks into map
        Map<Integer, String> trigStrMap = new HashMap<>();
        Pattern stringPattern = Pattern.compile("STRING\\s+(\\d+)\\s*\\{\\s*(.*?)\\s*\\}", Pattern.DOTALL);
        Matcher matcher = stringPattern.matcher(stringBlock);

        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            String value = matcher.group(2).trim();
            trigStrMap.put(id, value);
        }

        // Replace TRIGSTR_### using the map
        Pattern trigstrPattern = Pattern.compile("TRIGSTR_(\\d+)");
        Matcher trigMatcher = trigstrPattern.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (trigMatcher.find()) {
            int id = Integer.parseInt(trigMatcher.group(1));
            String replacement = trigStrMap.getOrDefault(id, trigMatcher.group());
            trigMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        trigMatcher.appendTail(sb);
        return sb.toString();
    }
}
