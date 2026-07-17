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


    private static Optional<GlobToken> parseBracketPattern(String bracketPattern) {

        if (bracketPattern.isEmpty()) {
            return Optional.empty();
        }

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

        return Optional.of(result);
    }

    private static ArrayList<GlobToken> tokenize(String pattern) {

        ArrayList<GlobToken> result = new ArrayList<>();

        if (pattern.isEmpty()) {
            return result;
        }

        int position = 0;
        while (position < pattern.length()) {

            char c = pattern.charAt(position);

            Optional<GlobToken> token = Optional.empty();

            if (c == '\\') {
                if (position + 1 < pattern.length()) {
                    char c2 = pattern.charAt(position + 1);
                    // if the next character in the pattern is a special character or another slash,
                    if (specialChars.contains(c2) || c2 == c) {
                        // create a new literal token and skip the next character
                        token = Optional.of(new GlobToken(c2));
                        position = position + 1;
                    }
                    // otherwise do nothing -- swallow the backslash
                }
            } else if (c == '*') {
                if (result.isEmpty() || result.get(result.size() - 1).type != GlobTokenType.STAR) {
                    token = Optional.of(new GlobToken(GlobTokenType.STAR));
                }
            } else if (c == '?') {
                token = Optional.of(new GlobToken(GlobTokenType.ANY_CHAR));
            } else if (c == '[') {
                int closingPosition = -1;
                int i = 0;
                boolean escaped = false;
                // find the position of the next unescaped closing bracket
                while (position + i < pattern.length()) {
                    char bracketChar = pattern.charAt(position + i);
                    if (bracketChar == ']' && !escaped) {
                        closingPosition = position + i;
                        break;
                    }
                    if (bracketChar == '\\') {
                        escaped = !escaped;
                    } else {
                        escaped = false;
                    }
                    i += 1;
                }
                // if not found, the pattern is unbalanced -- throw an exception
                if (closingPosition == -1) {
                    throw new IllegalArgumentException(String.format("Invalid pattern: unbalanced '[' at %d", position));
                }

                String bracketPattern = pattern.substring(position + 1, closingPosition);
                int bracketPatternPosition = position;

                // don't forget to move the cursor past the closing bracket
                position = closingPosition;

                token = parseBracketPattern(bracketPattern);
                if (token.isEmpty()) {
                    throw new IllegalArgumentException(
                            String.format("Invalid pattern: empty '[]' at %d", bracketPatternPosition));
                }
                if (token.get().matchingChars.isEmpty() && token.get().negate) {
                    throw new IllegalArgumentException(
                            String.format("Invalid pattern: '[!]' at %d", bracketPatternPosition));
                }


            } else {
                token = Optional.of(new GlobToken(c));
            }
            token.ifPresent(result::add);
            position += 1;
        }
        return result;
    }

    private static boolean tokenMatches(GlobToken token, char textChar) {
        switch (token.type) {
            case ANY_CHAR:
                return true;
            case BRACKET:
                boolean matched = token.matchingChars.contains(textChar);
                return token.negate ? !matched : matched;
            case LITERAL:
                return token.c == textChar;
            case STAR:
                throw new IllegalStateException("STAR tokens must be handled by the matching algorithm");
            default:
                throw new IllegalStateException("Unsupported token type: " + token.type);
        }
    }

    private static boolean match(List<GlobToken> tokenStream, String text) {
        boolean[] state = new boolean[text.length() + 1];
        boolean[] nextState = new boolean[text.length() + 1];
        state[0] = true;

        for (GlobToken token : tokenStream) {
            Arrays.fill(nextState, false);

            if (token.type == GlobTokenType.STAR) {
                nextState[0] = state[0];
                for (int textIndex = 1; textIndex <= text.length(); textIndex++) {
                    nextState[textIndex] = state[textIndex] || nextState[textIndex - 1];
                }
            } else {
                for (int textIndex = 1; textIndex <= text.length(); textIndex++) {
                    nextState[textIndex] = state[textIndex - 1]
                            && tokenMatches(token, text.charAt(textIndex - 1));
                }
            }

            boolean[] oldState = state;
            state = nextState;
            nextState = oldState;
        }

        return state[text.length()];
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
