package net.prsv.Wildcard;

import java.util.*;

/**
 * <p>Wildcard is my own implementation of glob pattern matching. Glob patterns specify sets of strings with wildcard
 * characters. This class provides a single static public method, {@link #match(String, String)}, which returns
 * {@code true} if the specified string matches the pattern.</p>
 *
 * <p>This implementation supports the following wildcards:</p>
 * <ul>
 *     <li><b>*</b> (star), which matches any number of any characters including none;</li>
 *     <li><b>?</b> (question mark), which matches any single character;</li>
 *     <li><b>[abc]</b> (brackets), which matches any single character given in the brackets. E.g., {@code [abc]}
 * would match either 'a', 'b' or 'c', but not 'd' or 'z';</li>
 *     <li><b>[a-z]</b> (brackets with ranges), which matches one character within the given range, e.g. {@code [a-z]}
 *     would match any character from 'a' to 'z', but not 'A' or '8'.</li>
 * </ul>
 *
 * <p>It is possible to combine single characters and ranges in one set of brackets, e.g. {@code [a-zZ0$]} would match
 * any of the following:</p>
 * <ul>
 *     <li>any character from 'a' to 'z';</li>
 *     <li>'Z' (capital z);</li>
 *     <li>'0' (zero);</li>
 *     <li>'$' (the dollar sign).</li>
 * </ul>
 *
 * <p>The bracket wildcard also supports <em>negation</em>. If the first character inside brackets is the exclamation
 * mark '!', the wildcard would match a single character that is <b>not</b> given in the bracket or is from the
 * given range.</p>
 *
 * <p>If special characters are escaped with a backslash '\', they are treated as literals. In other words, {@code "\?"}
 * matches a literal question mark, {@code "\-"} matches a literal dash, '\\' matches a literal backslash, etc.
 * Please note that '!' and '-' are not considered special characters outside of brackets. </p>
 *
 * <p>Unlike Unix glob, which never matches the forward slash character '/', this implementation treats '/'
 * as a regular character.</p>
 *
 * <p>Please take note that Wildcard will defensively replace all instances of the null terminator (U+0000) with
 * the Unicode replacement character (U+FFFD) in both text and pattern.</p>
 *
 * @author Pavel Urusov
 */
public class Wildcard {

    private enum GlobTokenType {
        LITERAL,
        ANY_CHAR,
        STAR,
        BRACKET
    }

    private static class GlobToken {
        private final GlobTokenType type;
        private final char c;
        private final HashSet<Character> matchingChars = new HashSet<>();
        private final boolean negate;

        GlobToken(GlobTokenType tokenType, char c, boolean negate) {
            this.type = tokenType;
            this.c = c;
            this.negate = negate;
        }

        GlobToken(char c) {
            this(GlobTokenType.LITERAL, c, false);
        }

        GlobToken(GlobTokenType tokenType) {
            this(tokenType, '\u0000', false);
        }

        GlobToken(GlobTokenType tokenType, boolean negate) {
            this(tokenType, '\u0000', negate);
        }

    }

    private static final Set<Character> specialChars = new HashSet<>();

    static {
        specialChars.add('[');
        specialChars.add(']');
        specialChars.add('*');
        specialChars.add('-');
        specialChars.add('!');
        specialChars.add('?');
    }


    private static GlobToken parseBracketPattern(String bracketPattern) {

        int position = 0;
        boolean negate = false;

        if (bracketPattern.charAt(position) == '!') {
            negate = true;
            bracketPattern = bracketPattern.substring(1);
        }

        GlobToken result = new GlobToken(GlobTokenType.BRACKET, negate);

        while (position < bracketPattern.length()) {
            char c = bracketPattern.charAt(position);

            if (c == '\\') {
                if (position + 1 < bracketPattern.length()) {
                    char c2 = bracketPattern.charAt(position + 1);
                    // if the next character in the pattern is a special character or another slash,
                    if (specialChars.contains(c2) || c2 == c) {
                        // add it to matching chars and skip to the next character
                        result.matchingChars.add(c2);
                        position += 1;
                    }
                    // otherwise do nothing -- swallow the backslash
                }
            } else if (specialChars.contains(c) && c != '-') { // skip unescaped special characters EXCEPT dash
                position += 1;
            } else if (c == '-' && position != 0 && position + 1 != bracketPattern.length()) {
                char fromChar = bracketPattern.charAt(position - 1);
                char toChar = bracketPattern.charAt(position + 1);
                // if the next character is escaped, skip the slash
                // this should work, because slashes on their own should always come in pairs
                if (toChar == '\\' && (position + 2 != bracketPattern.length())) {
                    char nextNextChar = bracketPattern.charAt(position + 2);
                    if (nextNextChar != toChar) {
                        toChar = nextNextChar;
                    }
                }
                if (toChar < fromChar) {
                    throw new IllegalArgumentException(String.format("Invalid pattern: '%c' higher than '%c' in [%s]",
                            fromChar, toChar, bracketPattern));
                }
                for (char charToAdd = fromChar; charToAdd <= toChar; charToAdd++) {
                    result.matchingChars.add(charToAdd);
                }
                position += 1;
            } else {
                result.matchingChars.add(c);
            }
            position++;
        }

        return result;
    }

    private static ArrayList<GlobToken> tokenize(String pattern) {

        ArrayList<GlobToken> result = new ArrayList<>();

        if (pattern == null || pattern.isEmpty()) {
            return null;
        }

        int position = 0;
        while (position < pattern.length()) {

            char c = pattern.charAt(position);

            GlobToken token = null;

            if (c == '\\') {
                if (position + 1 < pattern.length()) {
                    char c2 = pattern.charAt(position + 1);
                    // if the next character in the pattern is a special character or another slash,
                    if (specialChars.contains(c2) || c2 == c) {
                        // create a new literal token and skip the next character
                        token = new GlobToken(c2);
                        position = position + 1;
                    }
                    // otherwise do nothing -- swallow the backslash
                }
            } else if (c == '*') {
                token = new GlobToken(GlobTokenType.STAR);
            } else if (c == '?') {
                token = new GlobToken(GlobTokenType.ANY_CHAR);
            } else if (c == '[') {
                int closingPosition = -1;
                int i = 0;
                // find the position of the next unescaped closing bracket
                while (position + i < pattern.length()) {
                    if (pattern.charAt(position + i) == ']' && pattern.charAt(position + i - 1) != '\\') {
                        closingPosition = position + i;
                        break;
                    }
                    i += 1;
                }
                // if not found, the pattern is unbalanced -- throw an exception
                if (closingPosition == -1) {
                    throw new IllegalArgumentException(String.format("Invalid pattern: unbalanced '[' at %d", position));
                }

                String bracketPattern = pattern.substring(position + 1, closingPosition);

                // don't forget to move the cursor past the closing bracket
                position = closingPosition;

                token = parseBracketPattern(bracketPattern);


            } else {
                token = new GlobToken(c);
            }
            if (token != null) {
                result.add(token);
            }
            position += 1;
        }
        return result;
    }

    private static boolean match(List<GlobToken> tokenStream, String text) {
        if (tokenStream == null) {
            return (text == null || text.isEmpty());
        }
        int textIndex = 0;
        int patternIndex = 0;

        while (patternIndex < tokenStream.size() && textIndex < text.length()) {

            GlobToken currentToken = tokenStream.get(patternIndex);
            char currentChar = text.charAt(textIndex);

            switch (currentToken.type) {
                case ANY_CHAR: // '?'
                    // simply advance both the pattern and the text
                    patternIndex++;
                    textIndex++;
                    break;
                case STAR: // '*'
                    // call match() recursively until we either find a match or not
                    if (match(tokenStream.subList(patternIndex + 1, tokenStream.size()), text.substring(textIndex))) {
                        return true;
                    }
                    textIndex += 1;
                    break;
                case BRACKET: // handle the bracket pattern
                    boolean matched = currentToken.matchingChars.contains(currentChar);
                    if (currentToken.negate) {
                        matched = !matched;
                    }
                    if (!matched) {
                        return false;
                    }
                    patternIndex += 1;
                    textIndex += 1;
                    break;
                default: // character literal
                    if (currentToken.c == currentChar) { // if we match, advance
                        patternIndex += 1;
                        textIndex += 1;
                    }
                    else { // else fail
                        return false;
                    }
            }
        }

        // make sure that we properly handle "*", "**", "***", etc.
        if (textIndex == text.length()) {
            while (patternIndex < tokenStream.size() && tokenStream.get(patternIndex).type == GlobTokenType.STAR) {
                patternIndex++;
            }
            return patternIndex == tokenStream.size();
        }

        return false;
    }

    /**
     * Checks whether the specified string matches the specified glob pattern.
     * @param pattern pattern to match the specified string against
     * @param text string to match against the specified pattern
     * @return {@code true} if the specified string matches the specified pattern
     * @throws IllegalArgumentException if the pattern is malformed in such a way that it can't be sanitized
     */
    public static boolean match(String pattern, String text) throws IllegalArgumentException {
        pattern = pattern.replace('\u0000', '\ufffd');
        text = text.replace('\u0000', '\ufffd');
        ArrayList<GlobToken> tokenStream = tokenize(pattern);
        return match(tokenStream, text);
    }


}
