/*******************************************************************************
 *
 *	Copyright (c) 2008 Fujitsu Services Ltd.
 *
 *	Author: Nick Battle
 *
 *	This file is part of VDMJ.
 *
 *	VDMJ is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	VDMJ is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with VDMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package cskjunit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.List;
import java.util.Vector;

import com.fujitsu.vdmj.Release;
import com.fujitsu.vdmj.Settings;
import com.fujitsu.vdmj.ast.definitions.ASTBUSClassDefinition;
import com.fujitsu.vdmj.ast.definitions.ASTCPUClassDefinition;
import com.fujitsu.vdmj.ast.definitions.ASTClassList;
import com.fujitsu.vdmj.ast.modules.ASTModuleList;
import com.fujitsu.vdmj.config.Properties;
import com.fujitsu.vdmj.mapper.ClassMapper;
import com.fujitsu.vdmj.in.INNode;
import com.fujitsu.vdmj.in.definitions.INClassList;
import com.fujitsu.vdmj.in.modules.INModuleList;
import com.fujitsu.vdmj.lex.Dialect;
import com.fujitsu.vdmj.lex.LexTokenReader;
import com.fujitsu.vdmj.messages.Console;
import com.fujitsu.vdmj.messages.InternalException;
import com.fujitsu.vdmj.messages.VDMMessage;
import com.fujitsu.vdmj.po.PONode;
import com.fujitsu.vdmj.po.definitions.POClassList;
import com.fujitsu.vdmj.po.modules.POModuleList;
import com.fujitsu.vdmj.pog.ProofObligationList;
import com.fujitsu.vdmj.runtime.ClassInterpreter;
import com.fujitsu.vdmj.runtime.Interpreter;
import com.fujitsu.vdmj.runtime.ModuleInterpreter;
import com.fujitsu.vdmj.runtime.ValueException;
import com.fujitsu.vdmj.syntax.ClassReader;
import com.fujitsu.vdmj.syntax.ModuleReader;
import com.fujitsu.vdmj.tc.TCNode;
import com.fujitsu.vdmj.tc.definitions.TCClassList;
import com.fujitsu.vdmj.tc.modules.TCModuleList;
import com.fujitsu.vdmj.typechecker.ClassTypeChecker;
import com.fujitsu.vdmj.typechecker.ModuleTypeChecker;
import com.fujitsu.vdmj.typechecker.TypeChecker;
import com.fujitsu.vdmj.values.BooleanValue;
import com.fujitsu.vdmj.values.SeqValue;
import com.fujitsu.vdmj.values.UndefinedValue;
import com.fujitsu.vdmj.values.Value;
import com.fujitsu.vdmj.values.VoidValue;

import junit.framework.TestCase;

abstract public class CSKTest extends TestCase
{
	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		Properties.init();
		Settings.release = Release.CLASSIC;		// Several of the old CSK tests depend on this!
	}

	@Override
	protected void tearDown() throws Exception
	{
		super.tearDown();
	}

	protected static enum AssertType
	{
		TRUE, VOID, UNDEFINED, ERRLIST, POG, SKIP
	}

	// Set this to TRUE to re-create vdmj expected files from actual errors
	protected final static boolean REBUILD_EXPECTED_RESULTS = false;


	protected void processSL(String rpath, int syn, int tc, boolean rt, AssertType at)
		throws Exception
	{
		URL rurl = getClass().getResource("/csksltest/" + rpath + ".vdm");
		String vdmpath = rurl.getPath();
		String path = vdmpath.substring(0, vdmpath.lastIndexOf('.'));
		Settings.dialect = Dialect.VDM_SL;

		Console.out.println("Processing " + path + "...");
		LexTokenReader ltr = new LexTokenReader(new File(vdmpath), Dialect.VDM_SL);

		TypeChecker.clearErrors();
		ModuleReader mr = new ModuleReader(ltr);
		ASTModuleList parsed = mr.readModules();
		mr.close();

		assertEquals("Syntax errors", syn, mr.getErrorCount());
		assertEquals("Type check errors", 0, TypeChecker.getErrorCount());

		if (syn == 0)
		{
			TCModuleList checked = ClassMapper.getInstance(TCNode.MAPPINGS).init().convert(parsed);
			parsed = null;
			TypeChecker typeChecker = new ModuleTypeChecker(checked);
			typeChecker.typeCheck();
			TypeChecker.printErrors(Console.out);
			TypeChecker.printWarnings(Console.out);

			switch (at)
			{
				case SKIP:
					assertEquals("Type check errors", tc, TypeChecker.getErrorCount());
					break;

				case POG:
					assertEquals("Type check errors", tc, TypeChecker.getErrorCount());

					if (tc == 0)
					{
						POModuleList polist = ClassMapper.getInstance(PONode.MAPPINGS).init().convert(checked);
						checked = null;
						ProofObligationList obligations = polist.getProofObligations();
						obligations.renumber();

						if (REBUILD_EXPECTED_RESULTS)
						{
							update(path + ".pog", obligations);
						}
						else
						{
							compareObligations(path + ".pog", obligations);
						}
					}
       				break;

				case ERRLIST:
    				try
    				{
    					List<VDMMessage> actual = new Vector<VDMMessage>();
    					actual.addAll(TypeChecker.getErrors());
    					actual.addAll(TypeChecker.getWarnings());

    					if (REBUILD_EXPECTED_RESULTS)
    					{
    						update(path + ".vdmj", actual);
    					}

    					Interpreter interpreter = new ModuleInterpreter(new INModuleList(), new TCModuleList());
    					interpreter.init();

    					Value assertions =
    						interpreter.execute(new File(path + ".vdmj"));

    					assertTrue("Expecting error list", assertions instanceof SeqValue);

    					List<Long> expected = new Vector<Long>();

    					for (Value ex: assertions.seqValue(null))
    					{
    						expected.add(longOf(ex));
    					}

    					Console.out.print("Expected: [");
    					String sep = "";

    					for (Long m: expected)
    					{
    						Console.out.print(sep + m);
    						sep = ", ";
    					}

    					Console.out.println("]");
    					Console.out.print("Actually: [");
    					List<Long> actNums = new Vector<Long>();
    					sep = "";

    					for (VDMMessage m: actual)
    					{
    						Console.out.print(sep + m.number);
    						actNums.add((long)m.number);
    						sep = ", ";
    					}

    					Console.out.println("]");
    					assertTrue("Actual errors not as expected", actNums.equals(expected));
    				}
    				catch (Exception e)
    				{
    					Console.out.print("Caught: " + e);
    					Console.out.println(" in " + path + ".assert");
    					if (!rt) throw e;
    				}
    				break;

				default:
					assertEquals("Type check errors", tc, TypeChecker.getErrorCount());

					if (tc != 0)
					{
						break;		// Can't run these...
					}

					try
    				{
						INModuleList runnable = ClassMapper.getInstance(INNode.MAPPINGS).init().convert(checked);
    					Interpreter interpreter = new ModuleInterpreter(runnable, checked);
    					interpreter.init();

    					Value actual = interpreter.execute(new File(path + ".assert"));

    					Console.out.println("Result = " + actual);
    					Value expected = null;

    					switch (at)
    					{
    						case TRUE:
    							expected = new BooleanValue(true);
    	    					assertEquals("Evaluation error", expected, actual);
    							break;

    						case VOID:
    							expected = new VoidValue();
    	    					assertEquals("Evaluation error", expected, actual);
    							break;

    						case UNDEFINED:
    							expected = new UndefinedValue();
    	    					assertTrue("Evaluation error", actual.isUndefined());
    							break;
    							
							default:
								break;
    					}

    					assertTrue("Expecting runtime error", !rt);
    				}
    				catch (Exception e)
    				{
    					Console.out.print("Caught: " + e);
    					Console.out.println(" in " + path + ".assert");
    					if (!rt) throw e;
    				}
    				break;
			}
		}

		Console.out.println("Passed " + path + "...");
	}

	protected void processPP(String path, int syn, int tc, boolean rt, AssertType at)
	throws Exception
	{
		Settings.dialect = Dialect.VDM_PP;
		process("/cskpptest/" + path, syn, tc, rt, at);
	}

	protected void processTH(String path, int syn, int tc, boolean rt, AssertType at)
	throws Exception
	{
		Settings.dialect = Dialect.VDM_PP;
		process("/cskthreads/" + path, syn, tc, rt, at);
	}

	protected void processRT(String path, int syn, int tc, boolean rt, AssertType at)
	throws Exception
	{
		Settings.dialect = Dialect.VDM_RT;
		process("/cskrttest/" + path, syn, tc, rt, at);
	}

	private void process(String rpath, int syn, int tc, boolean rt, AssertType at)
	throws Exception
	{
		URL rurl = getClass().getResource(rpath + ".vpp");

		if (rurl == null)
		{
			throw new Exception("Cannot find " + rpath + ".vpp");
		}

		String vpppath = rurl.getPath();
		String path = vpppath.substring(0, vpppath.lastIndexOf('.'));

		Console.out.println("Processing " + path + "...");

		TypeChecker.clearErrors();
		ASTClassList parsed = parseClasses(vpppath, syn);

		if (Settings.dialect == Dialect.VDM_RT)
		{
    		try
    		{
    			parsed.add(new ASTCPUClassDefinition());
      			parsed.add(new ASTBUSClassDefinition());
    		}
    		catch (Exception e)
    		{
    			throw new InternalException(11, "CPU or BUS creation failure");
    		}
		}

		if (syn == 0)
		{
			TCClassList checked = ClassMapper.getInstance(TCNode.MAPPINGS).init().convert(parsed);
			parsed = null;
			TypeChecker typeChecker = new ClassTypeChecker(checked);
			typeChecker.typeCheck();
			TypeChecker.printErrors(Console.out);
			TypeChecker.printWarnings(Console.out);

			switch (at)
			{
				case SKIP:
					assertEquals("Type check errors", tc, TypeChecker.getErrorCount());
					break;

				case POG:
					assertEquals("Type check errors", tc, TypeChecker.getErrorCount());

					POClassList polist = ClassMapper.getInstance(PONode.MAPPINGS).init().convert(checked);
					ProofObligationList obligations = polist.getProofObligations();
					obligations.renumber();

					if (REBUILD_EXPECTED_RESULTS)
					{
						update(path + ".pog", obligations);
					}
					else
					{
						compareObligations(path + ".pog", obligations);
					}
					break;

				case ERRLIST:
    				try
    				{
    					List<VDMMessage> actual = new Vector<VDMMessage>();
    					actual.addAll(TypeChecker.getErrors());
    					actual.addAll(TypeChecker.getWarnings());

    					if (REBUILD_EXPECTED_RESULTS)
    					{
    						update(path + ".vdmj", actual);
    					}

    					Interpreter interpreter = new ClassInterpreter(new INClassList(), new TCClassList());
    					interpreter.init();

    					Value assertions =
    						interpreter.execute(new File(path + ".vdmj"));

    					assertTrue("Expecting error list", assertions instanceof SeqValue);

    					List<Long> expected = new Vector<Long>();

    					for (Value ex: assertions.seqValue(null))
    					{
    						expected.add(longOf(ex));
    					}

    					Console.out.print("Expected: [");
    					String sep = "";

    					for (Long m: expected)
    					{
    						Console.out.print(sep + m);
    						sep = ", ";
    					}

    					Console.out.println("]");
    					Console.out.print("Actually: [");
    					List<Long> actNums = new Vector<Long>();
    					sep = "";

    					for (VDMMessage m: actual)
    					{
    						Console.out.print(sep + m.number);
    						actNums.add((long)m.number);
    						sep = ", ";
    					}

    					Console.out.println("]");
    					assertTrue("Actual errors not as expected", actNums.equals(expected));
    				}
    				catch (Exception e)
    				{
    					Console.out.print("Caught: " + e);
    					Console.out.println(" in " + path + ".assert");
    					if (!rt) throw e;
    				}
    				break;

				default:
					assertEquals("Type check errors", tc, TypeChecker.getErrorCount());

    				if (tc != 0)
    				{
    					break;		// Can't run these...
    				}

    				try
    				{
						INClassList runnable = ClassMapper.getInstance(INNode.MAPPINGS).init().convert(checked);
    					Interpreter interpreter = new ClassInterpreter(runnable, checked);
    					interpreter.init();
    					Value actual = interpreter.execute(new File(path + ".assert"));

    					Console.out.println("Result = " + actual);
    					Value expected = null;

    					switch (at)
    					{
    						case TRUE:
    							expected = new BooleanValue(true);
    	    					assertEquals("Evaluation error", expected, actual);
    							break;

    						case VOID:
    							expected = new VoidValue();
    	    					assertEquals("Evaluation error", expected, actual);
    							break;

    						case UNDEFINED:
    							expected = new UndefinedValue();
    	    					assertTrue("Evaluation error", actual.isUndefined());
    							break;
    							
							default:
								break;
     					}

    					assertTrue("Expecting runtime error", !rt);
    				}
    				catch (Exception e)
    				{
    					Console.out.print("Caught: " + e);
    					Console.out.println(" in " + path + ".assert");
    					if (!rt) throw e;
    				}
    				break;
			}
		}

		Console.out.println("Passed " + path + "...");
	}

	private ASTClassList parseClasses(String vpppath, int syn)
	{
		ASTClassList classes = null;
		int errs = 0;

		LexTokenReader ltr = new LexTokenReader(new File(vpppath), Settings.dialect);
		ClassReader cr = new ClassReader(ltr);
		classes = cr.readClasses();
		cr.close();
		errs = cr.getErrorCount();

		if (errs > 0)
		{
			cr.printErrors(Console.out);
		}

		assertEquals("Syntax errors", syn, errs);
		assertEquals("Type check errors", 0, TypeChecker.getErrorCount());

		return classes;
	}

	/**
	 * Update the expected results file passed with the actual results.
	 */
	private void update(String filename, List<VDMMessage> actual)
	{
		try
		{
			// Maven build's resources will be located in test-classes, not src/test/resources.
			// But there's no point in updating the test-classes, because they are temporary!
			// So we fix the name first...

			int p = filename.indexOf("target/test-classes");

			if (p < 0)
			{
				return;		// We can't edit these files, if we can't find them?
			}

			// Make the test-class filename point to the resources source.
			filename = filename.replace("target/test-classes", "src/test/resources");

			File vdmj = new File(filename);
			FileWriter fw = new FileWriter(vdmj);
			String prefix = "[";

			if (actual.isEmpty())
			{
				fw.write(prefix);
			}
			else
			{
				for (VDMMessage m: actual)
				{
					fw.write(prefix);
					fw.write("" + m.number);
					prefix = ", ";
				}
			}

			fw.write("]\n--\n");

			for (VDMMessage m: actual)
			{
				fw.write(m.toString());
				fw.write("\n");
			}

			fw.close();
		}
		catch (IOException e)
		{
			fail("IO: " + e);
		}
	}

	/**
	 * Update a POG results file with the list of obligations passed.
	 */
	private void update(String filename, ProofObligationList obligations)
	{
		String poString = obligations.toString();

		try
		{
			// Maven build's resources will be located in test-classes, not src/test/resources.
			// But there's no point in updating the test-classes, because they are temporary!
			// So we fix the name first...

			int p = filename.indexOf("target/test-classes");

			if (p < 0)
			{
				return;		// We can't edit these files, if we can't find them?
			}

			// Make the test-class filename point to the resources source.
			filename = filename.replace("target/test-classes", "src/test/resources");

			File pog = new File(filename);
			FileWriter fw = new FileWriter(pog);
			fw.write(poString);
			fw.close();
		}
		catch (IOException e)
		{
			fail("IO: " + e);
		}
	}

	private Long longOf(Value v) throws ValueException
	{
		Object x = v.intValue(null);
		
		if (x instanceof BigInteger)
		{
			return ((BigInteger)x).longValue();
		}
		else
		{
			return ((Long)x).longValue();
		}
	}

	private void compareObligations(String path, ProofObligationList obligations)
	{
		try
		{
			String expected = readFile(new File(path));
			String actual = obligations.toString();

			if (!expected.equals(actual))
			{
				System.err.println("Actual POG list:");
				System.err.println(actual);
			}

			assertEquals("POs not as expected", expected, actual);
		}
		catch (IOException e)
		{
			fail("IO: " + e);
		}
	}

	private String readFile(File input) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(input));
		StringBuilder sb = new StringBuilder();
		String line = br.readLine();
		
		while (line != null)
		{
			sb.append(line);
			sb.append("\n");
			line = br.readLine();
		}
		
		br.close();
		return sb.toString();
	}
}
