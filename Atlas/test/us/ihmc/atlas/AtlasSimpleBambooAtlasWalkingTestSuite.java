package us.ihmc.atlas;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import us.ihmc.utilities.code.unitTesting.JUnitTestSuiteGenerator;

@RunWith(Suite.class)
@Suite.SuiteClasses
({
	AtlasFlatGroundWalkingTest.class
})

public class AtlasSimpleBambooAtlasWalkingTestSuite
{
	public static void main(String[] args)
	{
	   JUnitTestSuiteGenerator.generateTestSuite(AtlasSimpleBambooAtlasWalkingTestSuite.class);
	}
}