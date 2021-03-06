package us.ihmc.darpaRoboticsChallenge.roughTerrainWalking;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import us.ihmc.SdfLoader.SDFFullHumanoidRobotModel;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.darpaRoboticsChallenge.DRCObstacleCourseStartingLocation;
import us.ihmc.darpaRoboticsChallenge.MultiRobotTestInterface;
import us.ihmc.darpaRoboticsChallenge.environment.CinderBlockFieldEnvironment;
import us.ihmc.darpaRoboticsChallenge.testTools.DRCSimulationTestHelper;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataListMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.PelvisHeightTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage.FootstepOrigin;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.simulationconstructionset.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.bambooTools.SimulationTestingParameters;
import us.ihmc.tools.MemoryTools;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;
import us.ihmc.tools.thread.ThreadTools;

public abstract class EndToEndCinderBlockFieldTest implements MultiRobotTestInterface
{
   private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromEnvironmentVariables();
   
   private DRCSimulationTestHelper simulationTestHelper;

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
      if (simulationTestHelper != null)
      {
         simulationTestHelper.destroySimulation();
         simulationTestHelper = null;
      }
     
      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " after test.");
   }

   @DeployableTestMethod(estimatedDuration = 167.7)
   @Test(timeout = 840000)
   public void testWalkingOverCinderBlockField() throws Exception
   {
      BambooTools.reportTestStartedMessage(simulationTestingParameters.getShowWindows());

      CinderBlockFieldEnvironment cinderBlockFieldEnvironment = new CinderBlockFieldEnvironment();
      FootstepDataListMessage footsteps = generateFootstepsForCinderBlockField(cinderBlockFieldEnvironment.getCinderBlockPoses());
      
      DRCObstacleCourseStartingLocation selectedLocation = DRCObstacleCourseStartingLocation.DEFAULT;

      simulationTestHelper = new DRCSimulationTestHelper(cinderBlockFieldEnvironment, "EndToEndCinderBlockFieldTest", selectedLocation, simulationTestingParameters, getRobotModel());

      ThreadTools.sleep(1000);
      boolean success = simulationTestHelper.simulateAndBlockAndCatchExceptions(0.5);
      assertTrue(success);

      SDFFullHumanoidRobotModel fullRobotModel = simulationTestHelper.getControllerFullRobotModel();
      FramePoint pelvisPosition = new FramePoint(fullRobotModel.getPelvis().getBodyFixedFrame());
      pelvisPosition.changeFrame(ReferenceFrame.getWorldFrame());
      pelvisPosition.add(0.0, 0., 0.05);
      double desiredHeight = pelvisPosition.getZ();
      simulationTestHelper.send(new PelvisHeightTrajectoryMessage(0.5, desiredHeight));

      simulationTestHelper.send(footsteps);

      WalkingControllerParameters walkingControllerParameters = getRobotModel().getWalkingControllerParameters();
      double stepTime = walkingControllerParameters.getDefaultSwingTime() + walkingControllerParameters.getDefaultTransferTime();
      double initialFinalTransfer = getRobotModel().getCapturePointPlannerParameters().getDoubleSupportInitialTransferDuration();

      success = simulationTestHelper.simulateAndBlockAndCatchExceptions(footsteps.size() * stepTime + 2.0 * initialFinalTransfer + 1.0);
      assertTrue(success);

   }

   private static FootstepDataListMessage generateFootstepsForCinderBlockField(List<List<FramePose>> cinderBlockPoses)
   {
      FootstepDataListMessage footsteps = new FootstepDataListMessage();

      int numberOfColumns = cinderBlockPoses.get(0).size();

      int indexForLeftSide = (numberOfColumns - 1) / 2;
      int indexForRightSide = indexForLeftSide + 1;
      SideDependentList<List<FramePose>> columns = extractColumns(cinderBlockPoses, indexForLeftSide, indexForRightSide);

      for (int row = 0; row < cinderBlockPoses.size(); row++)
      {
         for (RobotSide robotSide : RobotSide.values)
         {
            FramePose cinderBlockPose = columns.get(robotSide).get(row);
            Point3d location = new Point3d();
            Quat4d orientation = new Quat4d();
            cinderBlockPose.getPose(location, orientation);
            FootstepDataMessage footstep = new FootstepDataMessage(robotSide, location, orientation);
            footstep.setOrigin(FootstepOrigin.AT_SOLE_FRAME);
            footsteps.add(footstep);
         }
      }

      return footsteps;
   }

   private static SideDependentList<List<FramePose>> extractColumns(List<List<FramePose>> cinderBlockPoses, int indexForLeftSide, int indexForRightSide)
   {
      SideDependentList<Integer> columnIndices = new SideDependentList<Integer>(indexForLeftSide, indexForRightSide);
      SideDependentList<List<FramePose>> sideDependentColumns = new SideDependentList<List<FramePose>>(new ArrayList<FramePose>(), new ArrayList<FramePose>());
      
      for (RobotSide robotSide : RobotSide.values)
      {
         int column = columnIndices.get(robotSide);

         for (int row = 0; row < cinderBlockPoses.size(); row++)
            sideDependentColumns.get(robotSide).add(cinderBlockPoses.get(row).get(column));
      }

      return sideDependentColumns;
   }
}
