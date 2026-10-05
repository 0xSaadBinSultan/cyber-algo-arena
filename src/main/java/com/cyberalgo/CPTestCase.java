package com.cyberalgo;

/**
 * One server-side competitive-programming testcase.
 * Hidden cases are never serialized to public challenge APIs.
 */
public record CPTestCase(String input, String expectedOutput, boolean hidden) {

    private static final int MAX_TEXT_LENGTH = 500_000;

    public CPTestCase {
        input = input == null ? "" : input;
        expectedOutput = expectedOutput == null ? "" : expectedOutput;
        if (input.length() > MAX_TEXT_LENGTH || expectedOutput.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("CP testcase input/output is too large");
        }
    }

    public static CPTestCase sample(String input, String expectedOutput) {
        return new CPTestCase(input, expectedOutput, false);
    }

    public static CPTestCase hidden(String input, String expectedOutput) {
        return new CPTestCase(input, expectedOutput, true);
    }
}
