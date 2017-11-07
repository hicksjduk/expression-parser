package uk.org.thehickses.expressions;

import static org.assertj.core.api.Assertions.*;

import java.util.function.IntSupplier;

import org.junit.Test;

import uk.org.thehickses.expressions.ExpressionParser.ParseException;

public class ExpressionParserTest
{
    @Test(expected = ParseException.class)
    public void testParseNullString() throws Exception
    {
        ExpressionParser.parse(null);
    }

    @Test(expected = ParseException.class)
    public void testParseEmptyString() throws Exception
    {
        ExpressionParser.parse("");
    }

    @Test(expected = ParseException.class)
    public void testParseBlankString() throws Exception
    {
        ExpressionParser.parse("   \t  \r  ");
    }

    @Test
    public void testParseNumberOnly() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("47");
        assertThat(result.getAsInt()).isEqualTo(47);
    }

    @Test
    public void testParseSurroundedByBlanks() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("   562    ");
        assertThat(result.getAsInt()).isEqualTo(562);
    }

    @Test
    public void testParseSurroundedByParentheses() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("(1541)");
        assertThat(result.getAsInt()).isEqualTo(1541);
    }

    @Test
    public void testParseSurroundedByMultipleParentheses() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("((1113))");
        assertThat(result.getAsInt()).isEqualTo(1113);
    }

    @Test
    public void testParseSurroundedByParenthesesAndSpaces() throws Exception
    {
        IntSupplier result = ExpressionParser.parse(" (  (   94  )  )   ");
        assertThat(result.getAsInt()).isEqualTo(94);
    }

    @Test
    public void testParseAddition() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("11 + 32");
        assertThat(result.getAsInt()).isEqualTo(43);
    }

    @Test
    public void testParseMixedAdditionAndSubtraction() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("50 - 11 + 32");
        assertThat(result.getAsInt()).isEqualTo(71);
    }

    @Test
    public void testParseMixedAdditionAndSubtractionWithParentheses() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("50 - (11 + 32)");
        assertThat(result.getAsInt()).isEqualTo(7);
    }

    @Test
    public void testParseMixedLowAndHighPriority() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("50 - 11 * 2");
        assertThat(result.getAsInt()).isEqualTo(28);
    }

    @Test
    public void testParseMixedLowAndHighPriorityWithParentheses() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("(50 - 11) * 2");
        assertThat(result.getAsInt()).isEqualTo(78);
    }

    @Test
    public void testMismatchedParenthesesTooManyOpening() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("((((50 - 11) * 2) + 41");
        assertThat(result.getAsInt()).isEqualTo(119);
    }

    @Test
    public void testMismatchedParenthesesTooManyClosing() throws Exception
    {
        IntSupplier result = ExpressionParser.parse("((50 - 11) * 2) + 41) ) )");
        assertThat(result.getAsInt()).isEqualTo(119);
    }

    @Test(expected = ParseException.class)
    public void testEmptyParentheses() throws Exception
    {
        ExpressionParser.parse("()");
    }

    @Test(expected = ParseException.class)
    public void testJustALeftParenthesis() throws Exception
    {
        ExpressionParser.parse("(");
    }

    @Test(expected = ParseException.class)
    public void testJustARightParenthesis() throws Exception
    {
        ExpressionParser.parse("      )        ");
    }
}
