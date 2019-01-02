package uk.org.thehickses.expressions;

import java.util.Map;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A parser for arithmetic expressions where all the numbers are integers. Supports the four basic arithmetic operators
 * (though division is integer division, truncating towards zero), with higher precedence for multiplication and
 * division, and the use of parentheses to override precedence. Also supports mismatched parentheses, assuming that the
 * missing parentheses are on the start or end of the string.
 * 
 * @author Jeremy Hicks
 */
public class ExpressionParser
{
    private static final Logger LOG = LoggerFactory.getLogger(ExpressionParser.class);

    private static final Pattern ALL_VALID_CHARS = Pattern.compile("(\\d|\\s|[()+\\-*/])+");
    private static final Pattern VALID_START_CHARS = Pattern.compile("(\\s|\\()*\\d.*");
    private static final Pattern LEFT_PARENTHESIS = Pattern.compile("\\(");
    private static final Pattern RIGHT_PARENTHESIS = Pattern.compile("\\)");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern VALID_TRAILING_CHARS = Pattern.compile("(\\s|\\))+");

    private final static Function<ExpressionParser, IntBinaryOperator> LOW_PRIORITY_OPERATOR_PARSER = operatorParser(
            Operation.ADD, Operation.SUBTRACT);
    private final static Function<ExpressionParser, IntBinaryOperator> HIGH_PRIORITY_OPERATOR_PARSER = operatorParser(
            Operation.MULTIPLY, Operation.DIVIDE);

    /**
     * Parses the specified string as an expression.
     * 
     * @param expression
     *            the string.
     * @return an IntSupplier which returns the result of evaluating the expression.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    public static IntSupplier parse(String expression) throws ParseException
    {
        LOG.debug(StringUtils.repeat('>', 30));
        LOG.debug("Parsing expression: '{}'", expression);
        try
        {
            IntSupplier answer = new ExpressionParser(expression).parse();
            LOG.debug("Parsed expression '{}' is valid and has value {}", expression,
                    answer.getAsInt());
            return answer;
        }
        catch (ParseException ex)
        {
            LOG.debug("Parsed expression '{}' is invalid", expression, ex);
            throw ex;
        }
        finally
        {
            LOG.debug(StringUtils.repeat('<', 30));
        }
    }

    /**
     * Gets a parser for the operators associated with the specified operations.
     * 
     * @param operations
     *            the operations.
     * @return an operation parser.
     */
    private static Function<ExpressionParser, IntBinaryOperator> operatorParser(
            Operation... operations)
    {
        Map<String, IntBinaryOperator> opsBySymbol = Stream
                .of(operations)
                .collect(Collectors.toMap(o -> o.symbol, o -> o.operation));
        Pattern pattern = Pattern.compile(new StringBuilder("[")
                .append(opsBySymbol.keySet().stream().collect(Collectors.joining()))
                .append("]")
                .toString());
        return parser -> opsBySymbol.get(parser.nextMatch(pattern));
    }

    private final String input;
    private int parsePosition = 0;

    /**
     * Initialises the parser with the specified expression string.
     * 
     * @param expression
     *            the expression.
     * @throws ParseException
     *             if the expression is blank, or contains any invalid characters.
     */
    private ExpressionParser(String expression) throws ParseException
    {
        if (StringUtils.isBlank(expression))
            throw new ParseException("No expression specified");
        if (!ALL_VALID_CHARS.matcher(expression).matches())
            throw new ParseException("Input expression contains invalid characters");
        if (!VALID_START_CHARS.matcher(expression).matches())
            throw new ParseException(
                    "Invalid expression, the first character that is not whitespace "
                            + "or a left parenthesis must be numeric");
        input = expression;
    }

    /**
     * Parses the expression string.
     * 
     * @return an IntSupplier which returns the result of evaluating the expression.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parse() throws ParseException
    {
        IntSupplier answer = parseExpression();
        // If there are any characters after the parsed expression, this is OK as long as they are whitespace or
        // (mismatched) right parentheses. Anything else is invalid.
        nextMatch(VALID_TRAILING_CHARS);
        if (input.length() > parsePosition)
            throw new ParseException(parsePosition,
                    "Expression contains extraneous characters '%s'",
                    input.substring(parsePosition));
        return answer;
    }

    /**
     * Parses an expression at the current position in the input.
     * 
     * @return an IntSupplier which returns the result of evaluating the expression, or null if no expression could be
     *         parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseExpression() throws ParseException
    {
        return parseLowPriorityExpression();
    }

    /**
     * Parses a low-priority expression at the current position in the input. A low-priority expression consists of a
     * high-priority expression, followed by zero or more sequences of a low-priority operator and a high-priority
     * expression.
     * 
     * @return an IntSupplier which returns the result of evaluating the expression, or null if no expression could be
     *         parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseLowPriorityExpression() throws ParseException
    {
        return parseExpression(this::parseHighPriorityExpression, LOW_PRIORITY_OPERATOR_PARSER);
    }

    /**
     * Parses a high-priority expression at the current position in the input. A high-priority expression consists of an
     * atomic expression, followed by zero or more sequences of a high-priority operator and an atomic expression.
     * 
     * @return an IntSupplier which returns the result of evaluating the expression, or null if no expression could be
     *         parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseHighPriorityExpression() throws ParseException
    {
        return parseExpression(this::parseAtomicExpression, HIGH_PRIORITY_OPERATOR_PARSER);
    }

    /**
     * Parses an expression at the current position in the input, using the specified operand and operator parsers. The
     * expression consists of an operand, followed by zero or more sequences of an operator and an operand.
     * 
     * @param operandParser
     *            the operand parser.
     * @param operatorParser
     *            the operator parser.
     * @return an IntSupplier which returns the result of evaluating the expression, or null if no expression could be
     *         parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseExpression(ExpressionSupplier operandParser,
            Function<ExpressionParser, IntBinaryOperator> operatorParser) throws ParseException
    {
        IntSupplier answer = operandParser.get();
        if (answer == null)
            return null;
        while (true)
        {
            IntBinaryOperator operator = operatorParser.apply(this);
            if (operator == null)
                return answer;
            IntSupplier rightOperand = operandParser.get();
            if (rightOperand == null)
                throw new ParseException(parsePosition,
                        "Operator must be followed by an expression");
            IntSupplier leftOperand = answer;
            answer = () -> operator.applyAsInt(leftOperand.getAsInt(), rightOperand.getAsInt());
        }
    }

    /**
     * Parses an atomic expression at the current position in the input. This is either a number or a parenthesised
     * expression, optionally surrounded by whitespace.
     * 
     * @return an IntSupplier which returns the result of evaluating the expression, or null if no expression could be
     *         parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseAtomicExpression() throws ParseException
    {
        nextMatch(WHITESPACE);
        IntSupplier answer = ((ExpressionSupplier) this::parseNumber)
                .orIfNull(this::parseParenthesisedExpression)
                .get();
        if (answer != null)
            nextMatch(WHITESPACE);
        return answer;
    }

    /**
     * Looks for a string of characters at the current position in the input, that matches the specified regular
     * expression pattern. If a matching string is found, the character(s) are consumed and the string is returned;
     * otherwise no characters are consumed and the result is null.
     * 
     * @param pattern
     *            the pattern.
     * @return the matching character(s), or null if no match was found.
     */
    private String nextMatch(Pattern pattern)
    {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find(parsePosition) || matcher.start() > parsePosition)
        {
            LOG.debug("No match found for '{}' at position {}", pattern.pattern(), parsePosition);
            return null;
        }
        String answer = matcher.group();
        LOG.debug("Found character(s) matching '{}' at position {}: '{}'", pattern.pattern(),
                parsePosition, answer);
        parsePosition = matcher.end();
        return answer;
    }

    /**
     * Parses a number at the current position in the input. This is a string of one or more digits.
     * 
     * @return an IntSupplier which returns the number found, or null if no number could be parsed.
     */
    private IntSupplier parseNumber()
    {
        String token = nextMatch(NUMBER);
        if (token == null)
            return null;
        int i = Integer.parseInt(token);
        return () -> i;
    }

    /**
     * Parses a parenthesised expression at the current position in the input. This is a left parenthesis, followed by
     * an expression, optionally followed by a right parenthesis.
     * 
     * @return an IntSupplier which returns the result of evaluating the expression found, or null if no expression
     *         could be parsed.
     */
    private IntSupplier parseParenthesisedExpression() throws ParseException
    {
        if (nextMatch(LEFT_PARENTHESIS) == null)
            return null;
        IntSupplier answer = parseExpression();
        if (answer == null)
            throw new ParseException(parsePosition,
                    "Left parenthesis must be followed by an expression");
        nextMatch(RIGHT_PARENTHESIS);
        return answer;
    }

    @SuppressWarnings("serial")
    public static class ParseException extends Exception
    {
        public ParseException(String message, Object... params)
        {
            super(String.format(message, params));
        }

        public ParseException(int position, String message, Object... params)
        {
            super(String.format("%s [position %d]", String.format(message, params), position));
        }
    }

    /**
     * An expression supplier. A custom functional interface is defined: (a) so that it can throw checked exceptions,
     * and (b) so that instances can be chained together so that the first one that returns a non-null result terminates
     * the chain and its result is returned.
     */
    @FunctionalInterface
    private static interface ExpressionSupplier
    {
        /**
         * Parses an expression.
         * 
         * @return an IntSupplier which returns the result of evaluating the expression, or null if no expression could
         *         be parsed.
         * @throws ParseException
         *             if the expression is invalid in format.
         */
        IntSupplier get() throws ParseException;

        /**
         * Chains this expression supplier together with another one. The first supplier to return a non-null result
         * terminates the chain and its result is returned.
         * 
         * @param other
         *            the other expression supplier.
         * @return a new expression supplier which chains the two suppliers together.
         */
        default ExpressionSupplier orIfNull(ExpressionSupplier other)
        {
            return () -> {
                IntSupplier myResult = this.get();
                if (myResult != null)
                    return myResult;
                return other.get();
            };
        }
    }

    private static enum Operation
    {
        ADD("+", (a, b) -> a + b),
        SUBTRACT("-", (a, b) -> a - b),
        MULTIPLY("*", (a, b) -> a * b),
        DIVIDE("/", (a, b) -> a / b);

        public final String symbol;
        public final IntBinaryOperator operation;

        private Operation(String symbol, IntBinaryOperator operation)
        {
            this.symbol = symbol;
            this.operation = operation;
        }
    }
}
