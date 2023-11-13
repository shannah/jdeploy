package ca.weblite.jdeploy.helpers;

import javax.inject.Singleton;

@Singleton
public class StringUtils {
    public String camelCaseToCliFlag(String input) {
        StringBuilder builder = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isUpperCase(c)) {
                builder.append('-');
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    public String camelCaseToLowerCaseWithSeparator(String input, String separator) {
        StringBuilder builder = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (builder.length() > 0) {
                    builder.append(separator);
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    public String lowerCaseWithSeparatorToCamelCase(String input, String separator) {
        StringBuilder builder = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : input.toCharArray()) {
            if (c == separator.charAt(0)) {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    builder.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    builder.append(c);
                }
            }
        }

        return builder.toString();
    }

    public boolean isValidJavaClassName(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }

        // Split the class name into its constituent parts using dot as a delimiter
        String[] parts = className.split("\\.", -1);  // Negative limit to keep trailing empty strings

        // Check if any parts are empty (which means there were consecutive dots)
        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }

            // First character should be a letter (including underscore and dollar sign)
            if (!Character.isJavaIdentifierStart(part.charAt(0))) {
                return false;
            }

            // Remaining characters should be valid identifier parts
            for (char c : part.toCharArray()) {
                if (!Character.isJavaIdentifierPart(c)) {
                    return false;
                }
            }
        }

        return true;
    }

    public String ucWords(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        String[] words = input.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

    public int countCharInstances(String input, char target) {
        int count = 0;
        for (char c : input.toCharArray()) {
            if (c == target) {
                count++;
            }
        }
        return count;
    }

    public String ucFirst(String lowerCaseWithSeparatorToCamelCase) {
        if (lowerCaseWithSeparatorToCamelCase == null || lowerCaseWithSeparatorToCamelCase.isEmpty()) {
            return lowerCaseWithSeparatorToCamelCase;
        }

        return Character.toUpperCase(lowerCaseWithSeparatorToCamelCase.charAt(0)) + lowerCaseWithSeparatorToCamelCase.substring(1);
    }
}
