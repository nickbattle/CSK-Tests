// Automatically generated JUnit stub for CSK tests in tc/fulltest/CORE/stmt/setforloopstmt

package cskjunit.sl.tc;

import com.fujitsu.vdmj.Release;
import com.fujitsu.vdmj.Settings;

public class CSKsetforloopstmtTest extends cskjunit.CSKTest
{
	public void test_setforloopstmt() throws Exception
	{
		Settings.release = Release.VDM_10;
		processSL("tc/fulltest/CORE/stmt/setforloopstmt/setforloopstmt", 0, 8, false, AssertType.ERRLIST);
	}

}
