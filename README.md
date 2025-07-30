# CSK-Tests

This is a private repository of JUnit tests that were automatically produced from an original set of tests for VDMTools, created by CSK Corp. The CSK tests were converted to apply to the VDMJ tool using a JUnit3 framework.

The suite was created as a raw JUnit suite originally in 2008. It was subsequently converted to a Maven project in 2025.

The tests pass on the latest version of VDMJ (currently 4.7.0-SNAPSHOT).

The test suite can be executed with "mvn clean test" at the top level.

The main test class is called CSKTest, which has processXX methods to run tests in a particular dialect, These methods load a source
file from the Java resources, and call a common "process" method to parse/check/execute the VDM source, checking the result against
the processXX arguments.

There are typically three resource files related to each test:

    1. <name>.assert

    This file contains the expected VDMTools error numbers that the test will produce (eg. syntax or typechecker errors), or the function
    or operation call to make and its expected result. The syntax of the file is valid VDM-SL, eg. [12,322] or Test() = {1,2,3}.

    2. <name>.vdm or <name>.vpp

    These files contain the VDM-SL, VDM++ or VICE (ie. VDM-RT) source for the test.

    3. <name>.vdmj

    These files contain the expected error numbers for VDMJ. So these are very similar to <name>.assert files, except with VDMJ numbers.
    In addition, these files contain the full text output of the parse/check error messages found, which should match the numbers at the top.

The resources and corresponding tests fall into four main groups: csksltest, cskpptest, cskrttest, cskthreads.

Within each group, there are up to three subgroups:

    "cgip" tests relate to the code generator and interpreter. So the assert files here define how to invoke the test and what to
    expect as a result.

    "pog" tests relate to the proof obligation generator. The assert files here contain the POs in an internal form defined by
	VDMTools. The corresponding JUnit tests do not perform POG checks because the VDMJ POG did not exist at this point.

	"tc" tests relate to the type checker. The assert files contain a list of expected error numbers and the *.vdmj files contain
	the VDMJ equivalents, along with expanded error messages and locations.

Each subgroup has sub-structure folders within it, like src/test/resources/csksltest/cgip/SL/expr/recordexpr/recordexpr-01.assert

A boolean constant in CSKTest called "REBUILD_EXPECTED_RESULTS" will overwrite any *.vdmj file with the result of
running a test under VDMJ. This can be useful, when VDMJ is changed, to keep the expected results correct. If you forget
to set the constant back to "false", a test at the top level called test_rebuild_flag will fail.


