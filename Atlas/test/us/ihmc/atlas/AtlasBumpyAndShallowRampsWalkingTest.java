package us.ihmc.atlas;

import org.junit.Test;

import us.ihmc.darpaRoboticsChallenge.DRCBumpyAndShallowRampsWalkingTest;
import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotModel;
import us.ihmc.simulationconstructionset.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.util.simulationRunner.ControllerFailureException;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestClass;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;
import us.ihmc.tools.testing.TestPlanTarget;

@DeployableTestClass(targets = {TestPlanTarget.Fast, TestPlanTarget.Video})
public class AtlasBumpyAndShallowRampsWalkingTest extends DRCBumpyAndShallowRampsWalkingTest
{
   @Override
   public DRCRobotModel getRobotModel()
   {
      return new AtlasRobotModel(AtlasRobotVersion.ATLAS_UNPLUGGED_V5_NO_HANDS, DRCRobotModel.RobotTarget.SCS, false);
   }

   @Override
   public String getSimpleRobotName()
   {
      return BambooTools.getSimpleRobotNameFor(BambooTools.SimpleRobotNameKeys.ATLAS);
   }
   
   @DeployableTestMethod(estimatedDuration = 95.7)
   @Test(timeout = 480000)
   @Override
   public void testDRCBumpyGroundWalking() throws SimulationExceededMaximumTimeException, ControllerFailureException
   {
      super.testDRCBumpyGroundWalking();
   }
   
   // This has never worked. Would be nice if we can get it to work.")
   @DeployableTestMethod(estimatedDuration = 0.1, targets = TestPlanTarget.Exclude)
   @Test(timeout=300000)
   @Override
   public void testDRCOverRandomBlocks() throws SimulationExceededMaximumTimeException, ControllerFailureException
   {
      super.testDRCOverRandomBlocks();
   }
   
   @DeployableTestMethod(estimatedDuration = 71.3)
   @Test(timeout = 360000)
   @Override
   public void testDRCOverShallowRamp() throws SimulationExceededMaximumTimeException, ControllerFailureException
   {
      super.testDRCOverShallowRamp();
   }
}
