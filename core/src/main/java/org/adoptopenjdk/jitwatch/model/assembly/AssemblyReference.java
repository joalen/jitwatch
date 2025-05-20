/*
 * Copyright (c) 2013-2021 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package org.adoptopenjdk.jitwatch.model.assembly;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser; 
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;


import org.xml.sax.helpers.DefaultHandler;

import com.chrisnewland.freelogj.Logger;
import com.chrisnewland.freelogj.LoggerFactory;

public final class AssemblyReference
{
	private static Map<String, String> x86MnemonicMap = null;
	private static Map<String, String> aarch64MnemonicMap = null;
	private static final Logger LOGGER = LoggerFactory.getLogger(AssemblyReference.class);

	private static final String ASM_REF_PATH_X86 = "/x86reference.xml";
	private static final String ASM_REF_PATH_AARCH64 = "/aarch64reference.xml";

	private AssemblyReference()
	{
	}

	private static class AssemblyReferenceHandler extends DefaultHandler
	{
		private final Map<String, String> resultMap = new HashMap<>();
		private final Set<String> currentMnemonics = new HashSet<>();
		private final StringBuilder txtBuffer = new StringBuilder();

		private boolean insideMnem = false;
		private boolean insideBrief = false;

		@Override
		public void startElement(String uri, String localname, String qname, Attributes attributes)
		{
			if ("mnem".equalsIgnoreCase(qname))
			{
				insideMnem = true;
				txtBuffer.setLength(0);
			}
			else if ("brief".equalsIgnoreCase(qname))
			{
				insideBrief = true;
				txtBuffer.setLength(0);
			}
		}

		@Override
		public void characters(char[] character, int start, int length)
		{
			txtBuffer.append(character, start, length);
		}

		@Override
		public void endElement(String uri, String localname, String qname)
		{
			if ("mnem".equalsIgnoreCase(qname))
			{
				currentMnemonics.add(txtBuffer.toString().trim().toLowerCase());
				insideMnem = false;
			}
			else if ("brief".equalsIgnoreCase(qname))
			{
				String brief = txtBuffer.toString().trim();
				for (String mnemonic : currentMnemonics)
				{
					resultMap.put(mnemonic, brief);
				}

				currentMnemonics.clear();
				insideBrief = false;
			}
		}

		public Map<String, String> getMnemonicMap()
		{
			return resultMap;
		}
	}

	// defer obtaining the x86 map unless we actually need it
	private static synchronized Map<String, String> getX86MnemonicMap()
	{
		if (x86MnemonicMap == null)
		{
			x86MnemonicMap = loadReferenceFile(ASM_REF_PATH_X86);

			// Patch up missing descriptions
			x86MnemonicMap.put("movabs", "Move a 64-bit value");
			if (x86MnemonicMap.get("retn") != null) x86MnemonicMap.put("ret", x86MnemonicMap.get("retn"));
			if (x86MnemonicMap.get("movsxd") != null) x86MnemonicMap.put("movslq", x86MnemonicMap.get("movsxd"));
		}
		return x86MnemonicMap;
	}

	// defer obtaining the aarch64 map unless we actually need it
	private static synchronized Map<String, String> getAarch64MnemonicMap()
	{
		if (aarch64MnemonicMap == null) aarch64MnemonicMap = loadReferenceFile(ASM_REF_PATH_AARCH64);
		return aarch64MnemonicMap;
	}

	private static Map<String, String> loadReferenceFile(String path)
	{
		try
		{
			InputStream asmRefInputStream = AssemblyReference.class.getResourceAsStream(path);

			if (asmRefInputStream == null)
			{
				LOGGER.error(
						"Could not find assembly reference {}. If launching from an IDE please add /src/main/resources to your classpath",
						path);
				return new HashMap<>();
			}

			// SAX parser to anticipate for better XML parsing and less pressure on the GC
			SAXParserFactory assemblyRefFactory = SAXParserFactory.newInstance();
			SAXParser xmlparser = assemblyRefFactory.newSAXParser();

			AssemblyReferenceHandler handler = new AssemblyReferenceHandler();
			xmlparser.parse(asmRefInputStream, handler);

			return handler.getMnemonicMap();

		} catch (IOException ioe)
		{
			LOGGER.error("Could not load assembly reference " + path, ioe);
		} catch (ParserConfigurationException pce)
		{
			LOGGER.error("The XML Parser suffered a malformed configuration when trying to load in the assembly reference " + path, pce);
		} catch (SAXException saxe)
		{
			LOGGER.error("The SAX XML Parser failed when trying to load in the assembly reference " + path, saxe);
		}

		return new HashMap<>();
	}

	// now, we are tailoring this more toward a specific architecture type
	public static String lookupMnemonic(String mnemonic, Architecture arch)
	{
		Map<String, String> mnemonicMap;

		if (arch == Architecture.ARM_32 || arch == Architecture.ARM_64)
		{
			mnemonicMap = getAarch64MnemonicMap();
		} else
		{
			mnemonicMap = getX86MnemonicMap();
		}

		String result = mnemonicMap.get(mnemonic);
		if (result == null) if (mnemonic.endsWith("b") || mnemonic.endsWith("w") || mnemonic.endsWith("l") || mnemonic.endsWith("q")) result = mnemonicMap.get(mnemonic.substring(0, mnemonic.length() - 1));

		return result;
	}

	// X86 by default -- overloaded if architecture is invalid or not supported
	public static String lookupMnemonic(String mnemonic)
	{
		return lookupMnemonic(mnemonic, Architecture.X86_64);
	}
}