/*
 * Copyright (c) 2013-2017 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package org.adoptopenjdk.jitwatch.model.assembly.x86;

import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_CLOSE_PARENTHESES;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_CLOSE_SQUARE_BRACKET;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_COMMA;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_OPEN_PARENTHESES;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_OPEN_SQUARE_BRACKET;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_SPACE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.DEBUG_LOGGING_ASSEMBLY;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_EMPTY;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_HEX_POSTFIX;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_HEX_PREFIX;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_OPEN_SQUARE_BRACKET;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_PERCENT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_SPACE;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adoptopenjdk.jitwatch.model.assembly.AbstractAssemblyParser;
import org.adoptopenjdk.jitwatch.model.assembly.Architecture;
import org.adoptopenjdk.jitwatch.model.assembly.AssemblyInstruction;
import org.adoptopenjdk.jitwatch.model.assembly.AssemblyLabels;
import org.adoptopenjdk.jitwatch.model.assembly.AssemblyUtil;

public final class AssemblyParserX86 extends AbstractAssemblyParser
{
	// http://www.delorie.com/djgpp/doc/brennan/brennan_att_inline_djgpp.html
	private static final String PART_ADDRESS = "(" + S_HEX_PREFIX + "[a-f0-9]+):";
	private static final String PART_INSTRUCTION = "([0-9a-zA-Z:_\\(\\)\\[\\]\\+\\*\\$,\\-%\\s]+)";
	private static final String PART_COMMENT = "([;#].*)?";

	private static final Pattern ASSEMBLY_CONSTANT = Pattern
			.compile("^([\\$]?(" + S_HEX_PREFIX + ")?[a-f0-9]+[" + S_HEX_POSTFIX + "]?)$");

	private static final Pattern PATTERN_ASSEMBLY_INSTRUCTION = Pattern
			.compile("^" + PART_ADDRESS + "\\s+" + PART_INSTRUCTION + PART_COMMENT);

	public final Architecture architecture;
	public AssemblyParserX86(Architecture architecture)
	{
		super(architecture);
		this.architecture = architecture;
	}
	
	@Override
	public AssemblyInstruction createInstruction(final AssemblyLabels labels, final String inLine)
	{
		if (DEBUG_LOGGING_ASSEMBLY)
		{
			logger.debug("Trying to parse instruction : {}", inLine);
		}

		String line = inLine;

		AssemblyInstruction instr = null;

		String annotation = S_EMPTY;

		if (!line.startsWith(S_HEX_PREFIX))
		{
			int addressIndex = line.indexOf(' ' + S_HEX_PREFIX);

			if (addressIndex != -1)
			{
				annotation = line.substring(0, addressIndex) + ' ';
				line = line.substring(addressIndex + 1);
			}
		}

		Matcher matcher = PATTERN_ASSEMBLY_INSTRUCTION.matcher(line);

		if (matcher.find())
		{
			if (DEBUG_LOGGING_ASSEMBLY)
			{
				for (int i = 1; i <= matcher.groupCount(); i++)
				{
					logger.debug("parts : '{}'='{}'", i, matcher.group(i));
				}
			}

			if (matcher.groupCount() == 3)
			{
				String address = matcher.group(1);
				String instructionString = matcher.group(2);
				String comment = matcher.group(3);

				if (DEBUG_LOGGING_ASSEMBLY)
				{
					logger.debug("Annotation : '{}'", annotation);
					logger.debug("Address    : '{}'", address);
					logger.debug("Instruction: '{}'", instructionString);
					logger.debug("Comment    : '{}'", comment);
				}

				long addressValue = AssemblyUtil.getValueFromAddress(address);

				if (instructionString != null && instructionString.trim().length() > 0)
				{
					instr = parseInstruction(instructionString, addressValue, comment, annotation, labels);
					labels.newInstruction(instr);
				}
			}
		}
		return instr;
	}

	@Override
	public AssemblyInstruction parseInstruction(String input, long address, String comment, String annotation,
			AssemblyLabels labels)
	{
		input = input.replaceAll("\\s+", S_SPACE).trim();

		int length = input.length();

		boolean inBrackets = false;

		String mnemonic = null;

		List<String> prefixes = new ArrayList<>();
		List<String> operands = new ArrayList<>();

		StringBuilder partBuilder = new StringBuilder();

		for (int pos = 0; pos < length; pos++)
		{
			char c = input.charAt(pos);

			if (c == C_OPEN_PARENTHESES || c == C_OPEN_SQUARE_BRACKET)
			{
				inBrackets = true;
			}
			else if (c == C_CLOSE_PARENTHESES || c == C_CLOSE_SQUARE_BRACKET)
			{
				inBrackets = false;
			}

			if (c == C_SPACE && mnemonic == null)
			{
				// end of part
				String part = partBuilder.toString();

				partBuilder.delete(0, partBuilder.length());

				if (DEBUG_LOGGING_ASSEMBLY)
				{
					logger.debug("part: '{}'", part);
				}

				if (mnemonic == null)
				{
					if ("data64".equals(part) || "data32".equals(part) || "data16".equals(part) || "data8".equals(part)
							|| "lock".equals(part))
					{
						prefixes.add(part);
					}
					else
					{
						mnemonic = part;
						if (DEBUG_LOGGING_ASSEMBLY)
						{
							logger.debug("mnemonic: '{}'", mnemonic);
						}
					}
				}
			}
			else if (c == C_COMMA && !inBrackets)
			{
				String operand = partBuilder.toString();
				partBuilder.delete(0, partBuilder.length());
				operands.add(operand);

				if (DEBUG_LOGGING_ASSEMBLY)
				{
					logger.debug("operand1: '{}'", operand);
				}
			}
			else
			{
				partBuilder.append(c);
			}
		}

		if (partBuilder.length() > 0)
		{
			String part = partBuilder.toString();
			partBuilder.delete(0, partBuilder.length() - 1);

			if (mnemonic == null)
			{
				mnemonic = part;
				if (DEBUG_LOGGING_ASSEMBLY)
				{
					logger.debug("mnemonic: '{}'", part);
				}
			}
			else
			{
				operands.add(part);
				if (DEBUG_LOGGING_ASSEMBLY)
				{
					logger.debug("operand2: '{}'", part);
				}
			}
		}

		return new AssemblyInstruction(annotation, address, prefixes, mnemonic, operands, comment, labels);
	}

	@Override
	public boolean isConstant(String mnemonic, String operand)
	{
		return ASSEMBLY_CONSTANT.matcher(operand).find() && !isJump(mnemonic);
	}

	@Override
	public boolean isRegister(String mnemonic, String operand)
	{
		return operand.startsWith(S_PERCENT) || operand.contains("(%") || operand.contains(S_OPEN_SQUARE_BRACKET)
				|| (!isConstant(mnemonic, operand) && !isAddress(mnemonic, operand));
	}

	@Override
	public boolean isJump(String mnemonic)
	{
		boolean result = false;

		if (mnemonic != null)
		{
			result = mnemonic.toLowerCase().startsWith("j") || mnemonic.toLowerCase().startsWith("call");
		}

		return result;
	}

	@Override
	public String extractRegisterName(final String input)
	{
		String regName = input;

		int indexOpenParentheses = input.indexOf(C_OPEN_PARENTHESES);
		int indexCloseParentheses = input.indexOf(C_CLOSE_PARENTHESES);

		if (indexOpenParentheses != -1 && indexCloseParentheses != -1)
		{
			regName = regName.substring(indexOpenParentheses + 1, indexCloseParentheses);
		}

		int indexOpenSquareBracket = regName.indexOf(C_OPEN_SQUARE_BRACKET);
		int indexCloseSquareBracket = regName.indexOf(C_CLOSE_SQUARE_BRACKET);

		if (indexOpenSquareBracket != -1 && indexCloseSquareBracket != -1)
		{
			regName = regName.substring(indexOpenSquareBracket + 1, indexCloseSquareBracket);
		}

		if (regName.startsWith("*"))
		{
			regName = regName.substring(1);
		}

		if (regName.startsWith(S_PERCENT))
		{
			regName = regName.substring(1);
		}

		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < regName.length(); i++)
		{
			char c = regName.charAt(i);

			if (Character.isAlphabetic(c) || Character.isDigit(c))
			{
				builder.append(c);
			}
			else
			{
				break;
			}
		}

		regName = builder.toString();

		return regName;
	}
}