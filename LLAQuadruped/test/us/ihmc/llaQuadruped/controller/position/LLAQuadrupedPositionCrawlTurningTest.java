package us.ihmc.llaQuadruped.controller.position;

import java.io.IOException;

import org.junit.Test;

import us.ihmc.llaQuadruped.LLAQuadrupedTestFactory;
import us.ihmc.quadrupedRobotics.QuadrupedTestFactory;
import us.ihmc.quadrupedRobotics.controller.position.QuadrupedPositionCrawlTurningTest;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.simulationconstructionset.util.simulationRunner.ControllerFailureException;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestClass;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;
import us.ihmc.tools.testing.TestPlanTarget;

@DeployableTestClass(targets = TestPlanTarget.InDevelopment)
public class LLAQuadrupedPositionCrawlTurningTest extends QuadrupedPositionCrawlTurningTest
{
   @Override
   public QuadrupedTestFactory createQuadrupedTestFactory()
   {
      return new LLAQuadrupedTestFactory();
   }
   
   @Override
   @DeployableTestMethod(estimatedDuration = 42.0)
   @Test(timeout = 800000)
   public void testYawingRightFastNinetyDegrees() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      super.testYawingRightFastNinetyDegrees();
   }
   
   @Override
   @DeployableTestMethod(estimatedDuration = 42.0)
   @Test(timeout = 800000)
   public void testYawingLeftFastNinetyDegrees() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      super.testYawingLeftFastNinetyDegrees();
   }
   
   @Override
   @DeployableTestMethod(estimatedDuration = 80.0)
   @Test(timeout = 800000)
   public void testYawingRightSlowNinetyDegrees() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      super.testYawingRightSlowNinetyDegrees();
   }

   @Override
   @DeployableTestMethod(estimatedDuration = 80.0)
   @Test(timeout = 800000)
   public void testYawingLeftSlowNinetyDegrees() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      super.testYawingLeftSlowNinetyDegrees();
   }
}
