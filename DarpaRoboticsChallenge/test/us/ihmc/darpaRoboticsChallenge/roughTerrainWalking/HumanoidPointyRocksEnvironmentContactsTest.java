package us.ihmc.darpaRoboticsChallenge.roughTerrainWalking;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import us.ihmc.commonWalkingControlModules.controlModules.foot.ExploreFootPolygonState.ExplorationMethod;
import us.ihmc.darpaRoboticsChallenge.DRCStartingLocation;
import us.ihmc.darpaRoboticsChallenge.MultiRobotTestInterface;
import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotModel;
import us.ihmc.darpaRoboticsChallenge.environment.CommonAvatarEnvironmentInterface;
import us.ihmc.darpaRoboticsChallenge.environment.PointyRocksWorld;
import us.ihmc.darpaRoboticsChallenge.environment.PointyRocksWorld.PointyRocksType;
import us.ihmc.darpaRoboticsChallenge.initialSetup.OffsetAndYawRobotInitialSetup;
import us.ihmc.darpaRoboticsChallenge.testTools.DRCSimulationTestHelper;
import us.ihmc.humanoidRobotics.communication.packets.TrajectoryPoint1DMessage;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.ArmTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.OneDoFJointTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataListMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage.FootstepOrigin;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.EnumYoVariable;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.simulationconstructionset.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.bambooTools.SimulationTestingParameters;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.tools.MemoryTools;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;
import us.ihmc.tools.testing.TestPlanTarget;
import us.ihmc.tools.thread.ThreadTools;

public abstract class HumanoidPointyRocksEnvironmentContactsTest implements MultiRobotTestInterface
{
   private SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromEnvironmentVariables();
   private OffsetAndYawRobotInitialSetup location = new OffsetAndYawRobotInitialSetup(new Vector3d(0.0, 0.0, 0.0), 0.0);
   private DRCSimulationTestHelper drcSimulationTestHelper;

   private BooleanYoVariable doFootExplorationInTransferToStanding;
   private DoubleYoVariable transferTime;
   private DoubleYoVariable swingTime;
   private DoubleYoVariable percentageChickenSupport;
   private DoubleYoVariable timeBeforeExploring;
   private SideDependentList<BooleanYoVariable> autoCropToLineAfterExploration = new SideDependentList<>();
   private SideDependentList<BooleanYoVariable> holdFlatDuringExploration = new SideDependentList<>();
   private SideDependentList<BooleanYoVariable> holdFlatDuringHoldPosition = new SideDependentList<>();
   private SideDependentList<BooleanYoVariable> smartHoldPosition = new SideDependentList<>();
   private SideDependentList<EnumYoVariable<ExplorationMethod>> explorationMethods = new SideDependentList<>();
   private BooleanYoVariable allowUpperBodyMomentumInSingleSupport;
   private BooleanYoVariable allowUpperBodyMomentumInDoubleSupport;
   private BooleanYoVariable allowUsingHighMomentumWeight;
   private BooleanYoVariable doToeOffIfPossible;
   private SideDependentList<BooleanYoVariable> requestExploration = new SideDependentList<>();
   private SideDependentList<BooleanYoVariable> doPartialDetection = new SideDependentList<>();
   private SideDependentList<BooleanYoVariable> cropToConvexHullOfCoPs = new SideDependentList<>();

   protected abstract DRCRobotModel getRobotModel(int xContactPoints, int yContactPoints, boolean createOnlyEdgePoints);

   @Ignore
   @DeployableTestMethod(estimatedDuration = 130.0, targets = {TestPlanTarget.Slow, TestPlanTarget.Video})
   @Test(timeout = 400000)
   public void testBalanceOnLine() throws SimulationExceededMaximumTimeException
   {
      PointyRocksWorld world = new PointyRocksWorld(PointyRocksType.SINGLE_LINE_BALANCE, 0);
      setupTest(world, false);
      doPartialDetection.get(RobotSide.RIGHT).set(true);

      Point3d cameraFix = new Point3d();
      Point3d cameraPosition = new Point3d();
      world.setupCamera(cameraFix, cameraPosition);
      drcSimulationTestHelper.setupCameraForUnitTest(cameraFix, cameraPosition);

      // enable the use of body momentum in the controller
      allowUpperBodyMomentumInSingleSupport.set(true);
      allowUpperBodyMomentumInDoubleSupport.set(true);
      allowUsingHighMomentumWeight.set(true);

      // change the walking parameters
      for (RobotSide robotSide : RobotSide.values)
      {
         autoCropToLineAfterExploration.get(robotSide).set(true);
         holdFlatDuringExploration.get(robotSide).set(true);
         holdFlatDuringHoldPosition.get(robotSide).set(true);
         smartHoldPosition.get(robotSide).set(false);
         explorationMethods.get(robotSide).set(ExplorationMethod.FAST_LINE);
      }

      doFootExplorationInTransferToStanding.set(true);
      percentageChickenSupport.set(0.4);
      timeBeforeExploring.set(1.0);
      transferTime.set(0.15);
      swingTime.set(0.8);

      armsUp();

      FootstepDataListMessage message = new FootstepDataListMessage();
      FootstepDataMessage footstepData = new FootstepDataMessage();
      Point3d position = new Point3d(0.4, -0.16, 0.0);
      footstepData.setLocation(position);
      footstepData.setOrientation(new Quat4d(0.0, 0.0, 0.0, 1.0));
      footstepData.setRobotSide(RobotSide.RIGHT);
      footstepData.setOrigin(FootstepOrigin.AT_SOLE_FRAME);
      message.add(footstepData);
      drcSimulationTestHelper.send(message);
      drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(6.0);

      Point3d desiredPosition = new Point3d(0.4, 0.16, 0.3);
      Quat4d desiredOrientation = new Quat4d();
      FootTrajectoryMessage footTrajectoryMessage = new FootTrajectoryMessage(RobotSide.LEFT, 1.0, desiredPosition, desiredOrientation);
      drcSimulationTestHelper.send(footTrajectoryMessage);

      drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(30.0);
   }

   @DeployableTestMethod(estimatedDuration = 137.5, targets = {TestPlanTarget.Slow, TestPlanTarget.Video})
   @Test(timeout = 690000)
   public void testWalkingOnLinesInEnvironment() throws SimulationExceededMaximumTimeException
   {
      PointyRocksWorld world = new PointyRocksWorld(PointyRocksType.LINES, 6);
      setupTest(world, true);

      Point3d cameraFix = new Point3d();
      Point3d cameraPosition = new Point3d();
      world.setupCamera(cameraFix, cameraPosition);
      drcSimulationTestHelper.setupCameraForUnitTest(cameraFix, cameraPosition);

      armsUp();

      // enable the use of body momentum in the controller
      allowUpperBodyMomentumInSingleSupport.set(true);
      allowUpperBodyMomentumInDoubleSupport.set(true);
      allowUsingHighMomentumWeight.set(true);

      // change the walking parameters
      for (RobotSide robotSide : RobotSide.values)
      {
         autoCropToLineAfterExploration.get(robotSide).set(true);
         holdFlatDuringExploration.get(robotSide).set(true);
         holdFlatDuringHoldPosition.get(robotSide).set(true);
         smartHoldPosition.get(robotSide).set(false);
         explorationMethods.get(robotSide).set(ExplorationMethod.FAST_LINE);
      }

      doFootExplorationInTransferToStanding.set(true);
      percentageChickenSupport.set(0.4);
      timeBeforeExploring.set(1.0);
      transferTime.set(0.15);
      swingTime.set(0.8);
      doToeOffIfPossible.set(false);

      ArrayList<FramePoint> stepLocations = world.getStepLocations();
      for (int i = 0; i < stepLocations.size(); i++)
      {
         if (i == stepLocations.size()-2)
         {
            percentageChickenSupport.set(0.5);
            doFootExplorationInTransferToStanding.set(false);
         }

         FootstepDataListMessage message = new FootstepDataListMessage();
         FootstepDataMessage footstepData = new FootstepDataMessage();

         Point3d position = stepLocations.get(i).getPointCopy();
         RobotSide robotSide = position.getY() > 0.0 ? RobotSide.LEFT : RobotSide.RIGHT;
         footstepData.setLocation(position);
         footstepData.setOrientation(new Quat4d(0.0, 0.0, 0.0, 1.0));
         footstepData.setRobotSide(robotSide);
         footstepData.setOrigin(FootstepOrigin.AT_SOLE_FRAME);
         message.add(footstepData);

         drcSimulationTestHelper.send(message);
         boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(6.0);
         assertTrue(success);
      }

      drcSimulationTestHelper.createVideo(getSimpleRobotName(), 1);
      drcSimulationTestHelper.checkNothingChanged();
   }

   @DeployableTestMethod(estimatedDuration = 69.7, targets = {TestPlanTarget.Slow, TestPlanTarget.Video})
   @Test(timeout = 350000)
   public void testWalkingOnPointInEnvironment() throws SimulationExceededMaximumTimeException
   {
      PointyRocksWorld world = new PointyRocksWorld(PointyRocksType.POINT, 0);
      setupTest(world, false);

      Point3d cameraFix = new Point3d();
      Point3d cameraPosition = new Point3d();
      world.setupCamera(cameraFix, cameraPosition);
      drcSimulationTestHelper.setupCameraForUnitTest(cameraFix, cameraPosition);

      armsUp();

      // enable the use of body momentum in the controller
      allowUpperBodyMomentumInSingleSupport.set(true);
      allowUpperBodyMomentumInDoubleSupport.set(true);
      allowUsingHighMomentumWeight.set(true);

      // change the walking parameters
      for (RobotSide robotSide : RobotSide.values)
      {
         autoCropToLineAfterExploration.get(robotSide).set(false);
         holdFlatDuringExploration.get(robotSide).set(true);
         holdFlatDuringHoldPosition.get(robotSide).set(true);
         smartHoldPosition.get(robotSide).set(false);
         explorationMethods.get(robotSide).set(ExplorationMethod.FAST_LINE);
      }

      doFootExplorationInTransferToStanding.set(true);
      percentageChickenSupport.set(0.3);
      timeBeforeExploring.set(1.0);
      transferTime.set(0.15);
      swingTime.set(0.4);
      doToeOffIfPossible.set(false);

      ArrayList<FramePoint> stepLocations = world.getStepLocations();
      for (int i = 0; i < stepLocations.size(); i++)
      {
         if (i == stepLocations.size()-2)
         {
            percentageChickenSupport.set(0.7);
            doFootExplorationInTransferToStanding.set(false);
         }

         FootstepDataListMessage message = new FootstepDataListMessage();
         FootstepDataMessage footstepData = new FootstepDataMessage();

         Point3d position = stepLocations.get(i).getPointCopy();
         RobotSide robotSide = position.getY() > 0.0 ? RobotSide.LEFT : RobotSide.RIGHT;
         footstepData.setLocation(position);
         footstepData.setOrientation(new Quat4d(0.0, 0.0, 0.0, 1.0));
         footstepData.setRobotSide(robotSide);
         footstepData.setOrigin(FootstepOrigin.AT_SOLE_FRAME);
         message.add(footstepData);

         drcSimulationTestHelper.send(message);
         boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(4.0);
         assertTrue(success);
      }

      drcSimulationTestHelper.createVideo(getSimpleRobotName(), 1);
      drcSimulationTestHelper.checkNothingChanged();
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

      simulationTestingParameters = null;
      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " after test.");
   }

   private void setupTest(CommonAvatarEnvironmentInterface environment, boolean onlyEdgeContacts)
   {
      BambooTools.reportTestStartedMessage(simulationTestingParameters.getShowWindows());

      // create simulation test helper
      String className = getClass().getSimpleName();
      DRCStartingLocation startingLocation = new DRCStartingLocation()
      {
         @Override
         public OffsetAndYawRobotInitialSetup getStartingLocationOffset()
         {
            return location;
         }
      };
      DRCRobotModel robotModel = getRobotModel(16, 8, onlyEdgeContacts);
      drcSimulationTestHelper = new DRCSimulationTestHelper(environment, className, startingLocation, simulationTestingParameters, robotModel);
      drcSimulationTestHelper.getSimulationConstructionSet().hideAllDynamicGraphicObjects();

      // increase ankle damping to match the real robot better
      DoubleYoVariable damping_l_akx = (DoubleYoVariable) drcSimulationTestHelper.getYoVariable("b_damp_l_leg_akx");
      DoubleYoVariable damping_l_aky = (DoubleYoVariable) drcSimulationTestHelper.getYoVariable("b_damp_l_leg_aky");
      DoubleYoVariable damping_r_akx = (DoubleYoVariable) drcSimulationTestHelper.getYoVariable("b_damp_r_leg_akx");
      DoubleYoVariable damping_r_aky = (DoubleYoVariable) drcSimulationTestHelper.getYoVariable("b_damp_r_leg_aky");
      damping_l_akx.set(1.0);
      damping_l_aky.set(1.0);
      damping_r_akx.set(1.0);
      damping_r_aky.set(1.0);

      // get a bunch of relevant variables
      doFootExplorationInTransferToStanding = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable("doFootExplorationInTransferToStanding");
      transferTime = (DoubleYoVariable) drcSimulationTestHelper.getYoVariable("transferTime");
      swingTime = (DoubleYoVariable) drcSimulationTestHelper.getYoVariable("swingTime");
      percentageChickenSupport = (DoubleYoVariable) drcSimulationTestHelper.getYoVariable("PercentageChickenSupport");
      timeBeforeExploring = (DoubleYoVariable) drcSimulationTestHelper.getYoVariable("ExplorationState_TimeBeforeExploring");
      for (RobotSide robotSide : RobotSide.values)
      {
         String footName = drcSimulationTestHelper.getControllerFullRobotModel().getFoot(robotSide).getName();
         String longFootName = robotSide.getLowerCaseName() + "Foot";
         String footControlNamespace = robotSide.getLowerCaseName() + "FootControlModule";
         String partialfootControlNamespace = footName + "PartialFootholdControlModule";

         BooleanYoVariable autoCrop = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable(footName + "AutoCropToLineAfterExploration");
         autoCropToLineAfterExploration.put(robotSide, autoCrop);
         BooleanYoVariable holdFlatDuringExploration = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable(footControlNamespace, footName + "DoHoldFootFlatOrientation");
         this.holdFlatDuringExploration.put(robotSide, holdFlatDuringExploration);
         BooleanYoVariable holdFlatDuringHoldPosition = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable("ExploreFootPolygon", footName + "DoHoldFootFlatOrientation");
         this.holdFlatDuringHoldPosition.put(robotSide, holdFlatDuringHoldPosition);
         BooleanYoVariable smartHoldPosition = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable(footControlNamespace, footName + "DoSmartHoldPosition");
         this.smartHoldPosition.put(robotSide, smartHoldPosition);
         EnumYoVariable<ExplorationMethod> explorationMethod = (EnumYoVariable<ExplorationMethod>) drcSimulationTestHelper.getYoVariable(footName + "ExplorationMethod");
         explorationMethods.put(robotSide, explorationMethod);
         BooleanYoVariable requestExplorationForFoot = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable(footControlNamespace, longFootName + "RequestExploration");
         requestExploration.put(robotSide, requestExplorationForFoot);
         BooleanYoVariable doPartialDetectionForFoot = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable(partialfootControlNamespace, footName + "DoPartialFootholdDetection");
         doPartialDetection.put(robotSide, doPartialDetectionForFoot);
         BooleanYoVariable cropToConvexHullOfCoPsForFoot = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable(partialfootControlNamespace, footName + "CropToConvexHullOfCoPs");
         cropToConvexHullOfCoPs.put(robotSide, cropToConvexHullOfCoPsForFoot);
      }
      allowUpperBodyMomentumInSingleSupport = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable("allowUpperBodyMomentumInSingleSupport");
      allowUpperBodyMomentumInDoubleSupport = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable("allowUpperBodyMomentumInDoubleSupport");
      allowUsingHighMomentumWeight = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable("allowUsingHighMomentumWeight");
      doToeOffIfPossible = (BooleanYoVariable) drcSimulationTestHelper.getYoVariable("doToeOffIfPossible");

      ThreadTools.sleep(1000);
   }

   private static final double[] rightHandStraightSideJointAngles = new double[] {-0.5067668142160446, -0.3659876546358431, 1.7973796317575155, -1.2398714600960365, -0.005510224629709242, 0.6123343067479899, 0.12524505635696856};
   private static final double[] leftHandStraightSideJointAngles = new double[] {0.61130147334225, 0.22680071472282162, 1.6270339908033258, 1.2703560974484844, 0.10340544060719102, -0.6738299572358809, 0.13264785356924128};
   private static final SideDependentList<double[]> straightArmConfigs = new SideDependentList<>();
   static
   {
      straightArmConfigs.put(RobotSide.LEFT, leftHandStraightSideJointAngles);
      straightArmConfigs.put(RobotSide.RIGHT, rightHandStraightSideJointAngles);
   }

   private void armsUp() throws SimulationExceededMaximumTimeException
   {
      drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(0.5);

      // bring the arms in a stretched position
      for (RobotSide robotSide : RobotSide.values)
      {
         ArmTrajectoryMessage armTrajectoryMessage = new ArmTrajectoryMessage();
         armTrajectoryMessage.robotSide = robotSide;
         double[] armConfig = straightArmConfigs.get(robotSide);
         armTrajectoryMessage.jointTrajectoryMessages = new OneDoFJointTrajectoryMessage[armConfig.length];
         for (int i = 0; i < armConfig.length; i++)
         {
            TrajectoryPoint1DMessage trajectoryPoint = new TrajectoryPoint1DMessage();
            trajectoryPoint.position = armConfig[i];
            trajectoryPoint.time = 1.0;
            OneDoFJointTrajectoryMessage jointTrajectory = new OneDoFJointTrajectoryMessage();
            jointTrajectory.trajectoryPoints = new TrajectoryPoint1DMessage[] {trajectoryPoint};
            armTrajectoryMessage.jointTrajectoryMessages[i] = jointTrajectory;
         }
         drcSimulationTestHelper.send(armTrajectoryMessage);
      }

      drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(1.1);
   }
}
