# Wildcard

Wildcard is my own implementation of glob pattern matching. Glob patterns specify sets of strings with wildcard
characters. The `match(String pattern, String text)` method returns `true` if the text matches the pattern.

The `matchWithLimit(String pattern, String text, long workLimit)` method performs the same match after checking that
the product of the pattern and text lengths, measured in Unicode code points, does not exceed the provided work limit.
It throws an `IllegalArgumentException` when the estimate exceeds the limit.

<p>This implementation supports the following wildcards:</p>

 - <b>*</b> (star), which matches any number of any characters including none;
 - <b>?</b> (question mark), which matches any single character;
 - <b>[abc]</b> (brackets), which matches any single character given in the brackets. E.g., `[abc]`
would match either 'a', 'b' or 'c', but not 'd' or 'z';
 - <b>[a-z]</b> (brackets with ranges), which matches one character within the given range, e.g. `[a-z]`
would match any character from 'a' to 'z', but not 'A' or '8'.


It is possible to combine single characters and ranges in one set of brackets, e.g. `[a-zZ0$]` would match
any of the following:

 - any character from 'a' to 'z';
 - 'Z' (capital z);
 - '0' (zero);
 - '$' (the dollar sign).

The bracket wildcard also supports *negation*. If the first character inside brackets is the exclamation
mark '!', the wildcard would match a single character that is **not** given in the bracket or is from the
given range.

Inside brackets, `*`, `?`, and `[` are literals and do not need escaping. A backslash and a closing bracket must be
escaped to be treated as literals. A dash denotes a range only when it appears between two range endpoints, and an
exclamation mark denotes negation only in the first position. For example, `[[]` matches `[`, `[*]` matches `*`, and
`[?]` matches `?`.

If special characters are escaped with a backslash '\\', they are treated as literals. In other words, `\?`
matches a literal question mark, `\-` matches a literal dash, `\\` matches a literal backslash, etc.
Please note that '!' and '-' are not considered special characters outside of brackets.

An unescaped backslash is always swallowed, both in ordinary patterns and inside bracket patterns. If it precedes a
character that does not form a recognized escape sequence, the backslash is discarded and the following character is
processed normally. Consequently, `\a` matches `a`, and a pattern consisting of a single trailing `\` matches the
empty string.

Unlike Unix glob, which never matches the forward slash character '/', this implementation treats '/'
as a regular character.

Wildcard matches Unicode code points rather than UTF-16 code units. Supplementary characters such as emoji therefore
count as one character for `?`, bracket patterns, and ranges.

Positions included in error messages are zero-based Unicode code-point offsets into the pattern, not UTF-16 code-unit
indices. For example, a pattern element immediately following an emoji is at offset 1.

Please take note that Wildcard will defensively replace all instances of the null terminator (U+0000) with
the Unicode replacement character (U+FFFD) in both text and pattern.

`null` arguments are treated as empty strings.

## Compatibility with POSIX and Java NIO glob patterns

Wildcard is a platform-independent matcher for complete strings. It resembles POSIX pattern matching and Java NIO
glob syntax, but it is not a pathname-expansion API and is not a drop-in replacement for either dialect. In the table
below, POSIX refers specifically to shell pathname expansion, and Java NIO refers to
`FileSystem.getPathMatcher("glob:...")`.

| Area | Wildcard | POSIX pathname expansion                                                                                                                             | Java NIO glob                                                                                                                                      |
| --- | --- |------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| Matching target | Matches the entire supplied string without accessing the filesystem. | Expands a pattern against existing filesystem entries.                                                                                               | Matches the string representation of a `Path`; details can depend on the filesystem provider.                                                      |
| Path separators | `/` is an ordinary character and can be matched by `*`, `?`, or a bracket pattern. | `/` must be matched explicitly and cannot appear in a pathname bracket expression.                                                                   | `*`, `?`, and bracket expressions do not cross name-component boundaries. `**` can cross them. Separator and root behavior are provider-dependent. |
| Consecutive stars | Consecutive stars are equivalent to one `*`, which can also match `/`. | Consecutive stars have the same matching effect as one `*`; POSIX assigns no special recursive meaning to `**`.                                      | `**` has distinct recursive/path-crossing behavior.                                                                                                |
| Leading periods | A leading `.` is ordinary and can be matched by `*` or `?`. | During pathname expansion, a leading `.` must be matched explicitly.                                                                                 | A leading `.` is treated as an ordinary character.                                                                                                 |
| Bracket expressions | Supports literals, numeric Unicode code-point ranges, and leading-`!` negation. `*`, `?`, and `[` are literals inside brackets. `]` and `\` must be escaped when literal. | Supports locale-sensitive bracket expressions, including character classes, collating symbols, and equivalence classes where provided by the locale. | Supports literals, ranges, and leading-`!` negation. Inside brackets, `*`, `?`, and `\` match themselves.                                          |
| Range ordering | Ranges use deterministic Unicode code-point order. | Range and class behavior can depend on the current locale and collation sequence.                                                                    | Range syntax is supported; case sensitivity and other matching details can depend on the filesystem implementation.                                |
| Escaping | A backslash escapes recognized special characters. Unknown escape introducers and a trailing backslash are swallowed. | Backslash handling interacts with shell quoting; where applicable, it preserves the literal value of the following character.                        | Backslash escapes special characters outside bracket expressions; inside brackets it matches itself.                                               |
| Groups | Braces have no special meaning; `{java,class}` is matched literally. | Brace groups are not part of POSIX pattern matching, although individual shells may provide brace expansion as an extension.                         | `{java,class}` is a group of alternatives; groups cannot be nested.                                                                                |
| Malformed patterns | Certain malformed patterns, including unbalanced or empty brackets and reversed ranges, throw `IllegalArgumentException`. | Partially unspecified; behaviour differs across implementations                                                                                      | Invalid patterns throw `PatternSyntaxException`.                                                                                                   |
| Character model | Matches Unicode code points and replaces U+0000 with U+FFFD before matching. | Matching is locale-oriented; pathnames cannot contain a null byte.                                                                                   | Character, case, separator, and root details may be provider- and platform-dependent.                                                              |

See also: [POSIX Pattern Matching Notation](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/V3_chap02.html#tag_19_14)
and [Java NIO `FileSystem.getPathMatcher`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)).
