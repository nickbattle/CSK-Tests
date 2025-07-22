// Automatically generated JUnit stub for CSK tests in tc/fulltest/PP/parallel/permissionstmt

package cskjunit.threads.tc;

public class CSKpermissionstmtTest extends cskjunit.CSKTest
{
	public void test_permissionstmt_03() throws Exception
	{
		processTH("tc/fulltest/PP/parallel/permissionstmt/permissionstmt-03", 0, 1, false, AssertType.ERRLIST);
	}

	public void test_permissionstmt() throws Exception
	{
		processTH("tc/fulltest/PP/parallel/permissionstmt/permissionstmt", 0, 6, false, AssertType.ERRLIST);
	}

}
