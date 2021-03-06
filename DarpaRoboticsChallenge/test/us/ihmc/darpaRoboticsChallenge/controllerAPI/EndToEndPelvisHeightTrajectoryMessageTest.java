package us.ihmc.darpaRoboticsChallenge.controllerAPI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import javax.vecmath.Point3d;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import us.ihmc.SdfLoader.SDFFullHumanoidRobotModel;
import us.ihmc.darpaRoboticsChallenge.DRCObstacleCourseStartingLocation;
import us.ihmc.darpaRoboticsChallenge.MultiRobotTestInterface;
import us.ihmc.darpaRoboticsChallenge.testTools.DRCSimulationTestHelper;
import us.ihmc.humanoidRobotics.communication.packets.walking.PelvisHeightTrajectoryMessage;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.random.RandomTools;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.bambooTools.SimulationTestingParameters;
import us.ihmc.tools.MemoryTools;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;
import us.ihmc.tools.thread.ThreadTools;

public abstract class EndToEndPelvisHeightTrajectoryMessageTest implements MultiRobotTestInterface
{
   private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromEnvironmentVariables();

   private static final boolean DEBUG = false;

   private DRCSimulationTestHelper drcSimulationTestHelper;

   @DeployableTestMethod(estimatedDuration = 15.2)
   @Test(timeout = 76000)
   public void testSingleWaypoint() throws Exception
   {
      BambooTools.reportTestStartedMessage(simulationTestingParameters.getShowWindows());

      Random random = new Random(564574L);
      double epsilon = 1.0e-4;

      DRCObstacleCourseStartingLocation selectedLocation = DRCObstacleCourseStartingLocation.DEFAULT;

      drcSimulationTestHelper = new DRCSimulationTestHelper(getClass().getSimpleName(), selectedLocation, simulationTestingParameters, getRobotModel());

      ThreadTools.sleep(1000);
      boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(0.5);
      assertTrue(success);

      SDFFullHumanoidRobotModel fullRobotModel = drcSimulationTestHelper.getControllerFullRobotModel();

      double trajectoryTime = 1.0;
      RigidBody pelvis = fullRobotModel.getPelvis();

      FramePoint desiredRandomPelvisPosition = new FramePoint(pelvis.getBodyFixedFrame());
      desiredRandomPelvisPosition.set(RandomTools.generateRandomPoint(random, 0.10, 0.20, 0.05));
      desiredRandomPelvisPosition.setZ(desiredRandomPelvisPosition.getZ() - 0.1);
      Point3d desiredPosition = new Point3d();

      desiredRandomPelvisPosition.get(desiredPosition);
      if (DEBUG)
      {
         System.out.println(desiredPosition);
      }

      desiredRandomPelvisPosition.changeFrame(ReferenceFrame.getWorldFrame());

      desiredRandomPelvisPosition.get(desiredPosition);
      if (DEBUG)
      {
         System.out.println(desiredPosition);
      }

      PelvisHeightTrajectoryMessage pelvisHeightTrajectoryMessage = new PelvisHeightTrajectoryMessage(trajectoryTime, desiredPosition.getZ());

      drcSimulationTestHelper.send(pelvisHeightTrajectoryMessage);

      success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(1.0 + trajectoryTime);
      assertTrue(success);

      SimulationConstructionSet scs = drcSimulationTestHelper.getSimulationConstructionSet();

      // Hard to figure out how to verify the desired there
//      trajOutput = scs.getVariable("pelvisHeightOffsetSubTrajectoryCubicPolynomialTrajectoryGenerator", "pelvisHeightOffsetSubTrajectoryCurrentValue").getValueAsDouble();
//      assertEquals(desiredPosition.getZ(), trajOutput, epsilon);
      // Ending up doing a rough check on the actual height
      double pelvisHeight = scs.getVariable("PelvisLinearStateUpdater", "estimatedRootJointPositionZ").getValueAsDouble();
      assertEquals(desiredPosition.getZ(), pelvisHeight, 0.01);
   }

   @Before
   public void showMemoryUsageBeforeTest()
   {
      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " before test.");
   }

   @After
   public void destroySimulationAndRecycleMemory()
   {
      if (simulationTestingParameters.getKeepSCSUp())
      {
         ThreadTools.sleepForever();
      }

      // Do this here in case a test fails. That way the memory will be recycled.
      if (drcSimulationTestHelper != null)
      {
         drcSimulationTestHelper.destroySimulation();
         drcSimulationTestHelper = null;
      }

      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " after test.");
   }
}
