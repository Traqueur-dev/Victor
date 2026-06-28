package fr.traqueur.victor.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void testSimpleCamelCase() {
        assertEquals("first_name", StringUtils.camelToSnakeCase("firstName"));
    }

    @Test
    void testMultipleWords() {
        assertEquals("my_user_name", StringUtils.camelToSnakeCase("myUserName"));
    }

    @Test
    void testAlreadyLowerCase() {
        assertEquals("username", StringUtils.camelToSnakeCase("username"));
    }

    @Test
    void testSingleCharacter() {
        assertEquals("a", StringUtils.camelToSnakeCase("a"));
    }

    @Test
    void testEmptyString() {
        assertEquals("", StringUtils.camelToSnakeCase(""));
    }

    @Test
    void testAllUpperCase() {
        // No lowercase→uppercase transition, just lowercased
        assertEquals("url", StringUtils.camelToSnakeCase("URL"));
    }

    @Test
    void testStartsWithUppercase() {
        // No lowercase→uppercase transition before 'U', result is simply lowercased
        assertEquals("username", StringUtils.camelToSnakeCase("Username"));
    }

    @Test
    void testLongMethodName() {
        assertEquals("find_by_username_and_email", StringUtils.camelToSnakeCase("findByUsernameAndEmail"));
    }
}