package us.ihmc.SdfLoader;

import java.io.FileNotFoundException;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.junit.Test;

import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;

public class SDFRobotTest
{
	@DeployableTestMethod(estimatedDuration = 0.6)
   @Test(timeout = 30000)
   public void testNullJointMap() throws FileNotFoundException, JAXBException
   {
      JaxbSDFLoader loader = new JaxbSDFLoader(getClass().getClassLoader().getResourceAsStream("sdfRobotTest.sdf"), (List<String>) null, null);
      
      GeneralizedSDFRobotModel generalizedSDFRobotModel = loader.getGeneralizedSDFRobotModel("atlas");
      new SDFHumanoidRobot(generalizedSDFRobotModel, null, null, true);
   }

}
