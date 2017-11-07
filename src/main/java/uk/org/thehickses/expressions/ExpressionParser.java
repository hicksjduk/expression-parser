package uk.org.thehickses.expressions;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
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

    @SuppressWarnings("serial")
    public static class ParseException extends Exception
    {
        public ParseException(String message, Object... params)
        {
            super(String.format(message, params));
        }
    }

    @FunctionalInterface
    private static interface ExpressionSupplier
    {
        IntSupplier get() throws ParseException;
    }

    private static enum Operation
    {
        ADD("+", (a, b) -> a + b), SUBTRACT("-", (a, b) -> a - b), MULTIPLY("*",
                (a, b) -> a * b), DIVIDE("/", (a, b) -> a / b);

        public final String symbol;
        public final IntBinaryOperator operation;

        private Operation(String symbol, IntBinaryOperator operation)
        {
            this.symbol = symbol;
            this.operation = operation;
        }
    }

    /**
     * Parses the specified string as an expression.
     * 
     * @param expression
     *            the string.
     * @return an IntSupplier whose getAsInt() method returns the result of evaluating the expression.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    public static IntSupplier parse(String expression) throws ParseException
    {
        logStart(() -> LOG.debug("Parsing expression: '{}'", expression));
        try
        {
            IntSupplier answer = new ExpressionParser(expression).parse();
            logEnd(() -> LOG.debug("Parsed expression '{}' is valid and has value {}", expression,
                    answer.getAsInt()));
            return answer;
        }
        catch (ParseException ex)
        {
            logEnd(() -> LOG.debug("Parsed expression '{}' is invalid", expression, ex));
            throw ex;
        }
    }

    /**
     * Logs the start of parsing an expression.
     * 
     * @param logAction
     *            the logging action specific to the expression.
     */
    private static void logStart(Runnable logAction)
    {
        LOG.debug(StringUtils.repeat('>', 30));
        logAction.run();
    }

    /**
     * Logs the end of parsing an expression.
     * 
     * @param logAction
     *            the logging action specific to the expression.
     */
    private static void logEnd(Runnable logAction)
    {
        logAction.run();
        LOG.debug(StringUtils.repeat('<', 30));
    }

    /**
     * A parser for low-priority expressions.
     */
    private final Supplier<IntBinaryOperator> lowPriorityOperationParser = operationParser(
            Operation.ADD, Operation.SUBTRACT);

    /**
     * A parser for high-priority expressions.
     */
    private final Supplier<IntBinaryOperator> highPriorityOperationParser = operationParser(
            Operation.MULTIPLY, Operation.DIVIDE);

    /**
     * The characters of the input.
     */
    private final Deque<String> characters;

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
        {
            throw new ParseException("No expression specified");
        }
        if (!expression.matches("(\\d|\\s|[()+\\-*/])+"))
        {
            throw new ParseException("Input expression contains invalid characters");
        }
        characters = new ArrayDeque<>(Arrays.asList(expression.split("")));
    }

    /**
     * Parses the expression string.
     * 
     * @return an IntSupplier whose getAsInt() method returns the result of evaluating the expression.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parse() throws ParseException
    {
        IntSupplier answer = parseExpression();
        if (answer == null)
        {
            throw new ParseException("No expression specified");
        }
        // If there are any characters after the parsed expression, this is OK as long as they are whitespace or
        // (mismatched) right parentheses. Anything else is invalid.
        getNextMatch("(\\s|\\))+");
        if (!characters.isEmpty())
        {
            throw new ParseException("Expression contains extraneous characters");
        }
        return answer;
    }

    /**
     * Parses an expression at the current position in the input.
     * 
     * @return an IntSupplier whose getAsInt() method returns the result of evaluating the expression, or null if no
     *         expression could be parsed.
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
     * @return an IntSupplier whose getAsInt() method returns the result of evaluating the expression, or null if no
     *         expression could be parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseLowPriorityExpression() throws ParseException
    {
        return parseExpression(this::parseHighPriorityExpression, lowPriorityOperationParser);
    }

    /**
     * Parses a high-priority expression at the current position in the input. A high-priority expression consists of an
     * atomic expression, followed by zero or more sequences of a high-priority operator and an atomic expression.
     * 
     * @return an IntSupplier whose getAsInt() method returns the result of evaluating the expression, or null if no
     *         expression could be parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseHighPriorityExpression() throws ParseException
    {
        return parseExpression(this::parseAtomicExpression, highPriorityOperationParser);
    }

    /**
     * Parses an expression at the current position in the input, using the specified operand and operator parsers. The
     * expression consists of an operand, followed by zero or more sequences of an operator and an operand.
     * 
     * @param operandParser
     *            the operand parser.
     * @param operatorParser
     *            the operator parser.
     * @return an IntSupplier whose getAsInt() method returns the result of evaluating the expression, or null if no
     *         expression could be parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseExpression(ExpressionSupplier operandParser,
            Supplier<IntBinaryOperator> operatorParser) throws ParseException
    {
        IntSupplier answer = operandParser.get();
        while (answer != null)
        {
            IntBinaryOperator operator = operatorParser.get();
            if (operator == null)
            {
                break;
            }
            IntSupplier rightOperand = operandParser.get();
            if (rightOperand == null)
            {
                throw new ParseException("Operator must be followed by an expression");
            }
            IntSupplier leftOperand = answer;
            answer = () -> operator.applyAsInt(leftOperand.getAsInt(), rightOperand.getAsInt());
        }
        return answer;
    }

    /**
     * Parses an atomic expression at the current position in the input. This is either a number or a parenthesised
     * expression, optionally surrounded by whitespace.
     * 
     * @return an IntSupplier whose getAsInt() method returns the result of evaluating the expression, or null if no
     *         expression could be parsed.
     * @throws ParseException
     *             if the expression is invalid in format.
     */
    private IntSupplier parseAtomicExpression() throws ParseException
    {
        getNextMatch("\\s+");
        IntSupplier answer = parseNumber();
        if (answer == null)
        {
            answer = parseParenthesisedExpression();
        }
        if (answer != null)
        {
            getNextMatch("\\s+");
        }
        return answer;
    }

    /**
     * Gets an operation parser for the specified operations.
     * 
     * @param operations
     *            the operations.
     * @return an operation parser.
     */
    private Supplier<IntBinaryOperator> operationParser(Operation... operations)
    {
        Map<String, IntBinaryOperator> opsBySymbol = Stream
                .of(operations)
                .collect(Collectors.toMap(o -> o.symbol, o -> o.operation));
        String regex = opsBySymbol
                .keySet()
                .stream()
                .collect(() -> new StringBuilder("["), StringBuilder::append, StringBuilder::append)
                .append("]")
                .toString();
        return () -> opsBySymbol.get(getNextMatch(regex));
    }

    /**
     * Looks for a string of characters at the current position in the input, that matches the specified regular
     * expression. If a matching string is found, the character(s) are consumed and the string is returned; otherwise no
     * characters are consumed and the result is null.
     * 
     * @param regex
     *            the regular expression.
     * @return the matching character(s), or null if no match was found.
     */
    private String getNextMatch(String regex)
    {
        String answer = null;
        while (!characters.isEmpty())
        {
            String candidate = (answer == null ? "" : answer) + characters.peek();
            if (!candidate.matches(regex))
            {
                break;
            }
            answer = candidate;
            characters.pop();
        }
        if (answer != null)
        {
            LOG.debug("Found character(s) matching '{}': '{}'", regex, answer);
        }
        else
        {
            LOG.debug("No match found for '{}'", regex);
        }
        return answer;
    }

    /**
     * Parses a number at the current position in the input. This is a string of one or more digits.
     * 
     * @return an IntSupplier whose applyAsInt() method returns the number found, or null if no number could be parsed.
     */
    private IntSupplier parseNumber()
    {
        IntSupplier answer = null;
        String token = getNextMatch("\\d+");
        if (token != null)
        {
            int num = Integer.parseInt(token);
            answer = () -> num;
        }
        return answer;
    }

    /**
     * Parses a parenthesised expression at the current position in the input. This is a left parenthesis, followed by
     * an expression, optionally followed by a right parenthesis.
     * 
     * @return an IntSupplier whose applyAsInt() method returns the result of evaluating the expression found, or null
     *         if no expression could be parsed.
     */
    private IntSupplier parseParenthesisedExpression() throws ParseException
    {
        IntSupplier answer = null;
        if (getNextMatch("\\(") != null)
        {
            answer = parseExpression();
            if (answer == null)
            {
                throw new ParseException("Left parenthesis must be followed by an expression");
            }
            getNextMatch("\\)");
        }
        return answer;
    }
}
