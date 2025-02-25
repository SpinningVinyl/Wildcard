package net.prsv.Wildcard.test;

import net.prsv.Wildcard.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WildcardTests {

    @Test
    void WildcardStarTest() {
        assertTrue(Wildcard.match("Law*", "Law"));
        assertTrue(Wildcard.match("Law*", "Lawyer"));
        assertFalse(Wildcard.match("Law*", "GrokLaw"));
        assertFalse(Wildcard.match("Law*", "aw"));
        assertFalse(Wildcard.match("Law*", "La"));

        assertTrue(Wildcard.match("*Law*", "Law"));
        assertTrue(Wildcard.match("*Law*", "GrokLaw"));
        assertTrue(Wildcard.match("*Law*", "Lawyer"));
        assertFalse(Wildcard.match("*Law*", "La"));
        assertFalse(Wildcard.match("*Law*", "aw"));
    }

    @Test
    void escapedCharactersTest() {
        assertTrue(Wildcard.match("\\?\\*\\[\\]\\\\", "?*[]\\"));
        assertTrue(Wildcard.match("[\\]\\[]", "]"));
        assertTrue(Wildcard.match("[\\]\\[]", "["));
        assertTrue(Wildcard.match("[a\\-c]", "a"));
        assertTrue(Wildcard.match("[a\\-c]", "c"));
        assertTrue(Wildcard.match("[a\\-c]", "-"));
        assertFalse(Wildcard.match("[a\\-c]", "b"));
    }

    @Test
    void unescapedBangDashOutsideOfBracketsTest() {
        assertTrue(Wildcard.match("!", "!"));
        assertTrue(Wildcard.match("-", "-"));
    }

    @Test
    void emptyPatternTest() {
        assertTrue(Wildcard.match("", ""));
        assertFalse(Wildcard.match("", "a"));
    }

    @Test
    void emptyStringTest() {
        assertTrue(Wildcard.match("*", ""));
        assertTrue(Wildcard.match("**", ""));
        assertTrue(Wildcard.match("***", ""));
    }

    @Test
    void unmatchedBracketTest() {
        assertThrows(IllegalArgumentException.class, () -> Wildcard.match("*[Qqueen", "zz queen"));
    }

    @Test
    void incorrectRangeTest() {
        assertThrows(IllegalArgumentException.class, () -> Wildcard.match("[z-a]", "z"));
    }

    @Test
    void WildcardAnyCharTest() {
        assertTrue(Wildcard.match("?at", "Cat"));
        assertTrue(Wildcard.match("?at", "cat"));
        assertTrue(Wildcard.match("?at", "Bat"));
        assertTrue(Wildcard.match("?at", "bat"));
        assertFalse(Wildcard.match("?at", "at"));
    }

    @Test
    void WildcardBracketBasicTest() {
        assertTrue(Wildcard.match("[CB]at", "Cat"));
        assertTrue(Wildcard.match("[CB]at", "Bat"));
        assertFalse(Wildcard.match("[CB]at", "cat"));
        assertFalse(Wildcard.match("[CB]at", "bat"));
        assertFalse(Wildcard.match("[CB]at", "CBat"));
    }
    
    @Test
    void WildcardBracketBasicNegationTest() {
        assertFalse(Wildcard.match("*.[!abc]", "main.a"));
        assertFalse(Wildcard.match("*.[!abc]", "main.b"));
        assertFalse(Wildcard.match("*.[!abc]", "main.c"));
        assertFalse(Wildcard.match("*.[!abc]", "main.abc"));
        assertTrue(Wildcard.match("*.[!abc]", "main.d"));
    }

    @Test
    void WildcardBracketRangeTest() {
        assertTrue(Wildcard.match("Letter[0-9]", "Letter0"));
        assertTrue(Wildcard.match("Letter[0-9]", "Letter1"));
        assertTrue(Wildcard.match("Letter[0-9]", "Letter2"));
        assertTrue(Wildcard.match("Letter[0-9]", "Letter9"));
        assertFalse(Wildcard.match("Letter[0-9]", "Letters" ));
        assertFalse(Wildcard.match("Letter[0-9]", "Letter"  ));
        assertFalse(Wildcard.match("Letter[0-9]", "Letter10"));
    }

    @Test
    void WildcardBracketRangeNegationTest() {
        assertTrue(Wildcard.match("Letter[!3-5]", "Letter1"));
        assertTrue(Wildcard.match("Letter[!3-5]", "Letter2"));
        assertTrue(Wildcard.match("Letter[!3-5]", "Letter6"));
        assertTrue(Wildcard.match("Letter[!3-5]", "Letters"));
        assertFalse(Wildcard.match("Letter[!3-5]", "Letter3"));
        assertFalse(Wildcard.match("Letter[!3-5]", "Letter4"));
        assertFalse(Wildcard.match("Letter[!3-5]", "Letter5"));
        assertFalse(Wildcard.match("Letter[!3-5]", "LetterXX"));
    }

    @Test
    void WildcardBracketEdgeCases() {
        assertTrue(Wildcard.match("[\\--0]", "-" ));
        assertTrue(Wildcard.match("[\\--0]", "." ));
        assertTrue(Wildcard.match("[\\--0]", "/" ));
        assertTrue(Wildcard.match("[\\--0]", "0" ));
        assertFalse(Wildcard.match("[\\--0]", "z"));
        assertFalse(Wildcard.match("[[-]", "-"  ));
        assertTrue(Wildcard.match("[[\\-]", "-"  ));
        assertFalse(Wildcard.match("[[-]", "["  ));
        assertTrue(Wildcard.match("[\\[-]", "["));
        assertFalse(Wildcard.match("[[-]", "z" ));

        assertTrue(Wildcard.match("[a-d-i]", "a"));
        assertTrue(Wildcard.match("[a-d-i]", "b"));
        assertTrue(Wildcard.match("[a-d-i]", "c"));
        assertTrue(Wildcard.match("[a-d-i]", "d"));
        assertTrue(Wildcard.match("[a-d-i]", "e"));
        assertTrue(Wildcard.match("[a-d-i]", "f"));
        assertTrue(Wildcard.match("[a-d-i]", "g"));
        assertTrue(Wildcard.match("[a-d-i]", "h"));
        assertTrue(Wildcard.match("[a-d-i]", "i"));
        assertFalse(Wildcard.match("[a-d-i]", "j"));
        assertFalse(Wildcard.match("[a-d-i]", "a-d-i"));

        assertTrue(Wildcard.match("[a-d1-3]", "a"));
        assertTrue(Wildcard.match("[a-d1-3]", "b"));
        assertTrue(Wildcard.match("[a-d1-3]", "c"));
        assertTrue(Wildcard.match("[a-d1-3]", "d"));
        assertTrue(Wildcard.match("[a-d1-3]", "1"));
        assertTrue(Wildcard.match("[a-d1-3]", "2"));
        assertTrue(Wildcard.match("[a-d1-3]", "3"));
        assertFalse(Wildcard.match("[a-d1-3]", "5"));
        assertFalse(Wildcard.match("[a-d1-3]", "g"));
        assertFalse(Wildcard.match("[a-d1-3]", "a-d1-3"));

    }



}
