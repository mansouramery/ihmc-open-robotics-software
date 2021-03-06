package us.ihmc.humanoidRobotics.communication.packets;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import us.ihmc.SdfLoader.SDFFullHumanoidRobotModelFactory;
import us.ihmc.SdfLoader.models.FullHumanoidRobotModel;
import us.ihmc.SdfLoader.models.FullRobotModelUtils;
import us.ihmc.SdfLoader.partNames.LimbName;
import us.ihmc.communication.packets.KinematicsToolboxOutputStatus;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.ArmTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.HandTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.HandTrajectoryMessage.BaseForControl;
import us.ihmc.humanoidRobotics.communication.packets.walking.ChestTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.PelvisTrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.wholebody.WholeBodyTrajectoryMessage;
import us.ihmc.robotics.MathTools;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.nameBasedHashCode.NameBasedHashCodeTools;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.screwTheory.OneDoFJoint;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.ScrewTools;
import us.ihmc.robotics.screwTheory.SixDoFJoint;

public class KinematicsToolboxOutputConverter
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final FullHumanoidRobotModel fullRobotModelToUseForConversion;
   private final SixDoFJoint rootJoint;
   private final OneDoFJoint[] oneDoFJoints;
   private final int jointsHashCode;

   public KinematicsToolboxOutputConverter(SDFFullHumanoidRobotModelFactory fullRobotModelFactory)
   {
      this.fullRobotModelToUseForConversion = fullRobotModelFactory.createFullRobotModel();
      rootJoint = fullRobotModelToUseForConversion.getRootJoint();
      oneDoFJoints = FullRobotModelUtils.getAllJointsExcludingHands(fullRobotModelToUseForConversion);
      jointsHashCode = (int) NameBasedHashCodeTools.computeArrayHashCode(oneDoFJoints);
   }

   public void updateFullRobotModel(KinematicsToolboxOutputStatus solution)
   {
      if (jointsHashCode != solution.jointNameHash)
         throw new RuntimeException("Hashes are different.");

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         float q = solution.getJointAngles()[i];
         OneDoFJoint joint = oneDoFJoints[i];
         joint.setQ(q);
      }
      Vector3f translation = solution.getPelvisTranslation();
      rootJoint.setPosition(translation.x, translation.y, translation.z);
      Quat4f orientation = solution.getPelvisOrientation();
      rootJoint.setRotation(orientation.x, orientation.y, orientation.z, orientation.w);
      fullRobotModelToUseForConversion.updateFrames();
   }

   private WholeBodyTrajectoryMessage output;

   public void setMessageToCreate(WholeBodyTrajectoryMessage message)
   {
      output = message;
   }

   private double trajectoryTime = Double.NaN;

   public void setTrajectoryTime(double trajectoryTime)
   {
      this.trajectoryTime = trajectoryTime;
   }

   public void computeArmTrajectoryMessages()
   {
      for (RobotSide robotSide : RobotSide.values)
         computeArmTrajectoryMessage(robotSide);
   }

   public void computeArmTrajectoryMessage(RobotSide robotSide)
   {
      RigidBody hand = fullRobotModelToUseForConversion.getHand(robotSide);
      RigidBody chest = fullRobotModelToUseForConversion.getChest();
      OneDoFJoint[] armJoints = ScrewTools.createOneDoFJointPath(chest, hand);
      int numberOfArmJoints = armJoints.length;
      double[] desiredJointPositions = new double[numberOfArmJoints];
      for (int i = 0; i < numberOfArmJoints; i++)
      {
         OneDoFJoint armJoint = armJoints[i];
         desiredJointPositions[i] = MathTools.clipToMinMax(armJoint.getQ(), armJoint.getJointLimitLower(), armJoint.getJointLimitUpper());
      }
      ArmTrajectoryMessage armTrajectoryMessage = new ArmTrajectoryMessage(robotSide, trajectoryTime, desiredJointPositions);
      output.setArmTrajectoryMessage(armTrajectoryMessage);
   }

   public void computeHandTrajectoryMessages()
   {
      for (RobotSide robotSide : RobotSide.values)
         computeHandTrajectoryMessage(robotSide);
   }

   public void computeHandTrajectoryMessage(RobotSide robotSide)
   {
      checkIfDataHasBeenSet();

      BaseForControl baseForControl = BaseForControl.WORLD;
      Point3d desiredPosition = new Point3d();
      Quat4d desiredOrientation = new Quat4d();
      ReferenceFrame handControlFrame = fullRobotModelToUseForConversion.getHandControlFrame(robotSide);
      FramePose desiredHandPose = new FramePose(handControlFrame);
      desiredHandPose.changeFrame(worldFrame);
      desiredHandPose.getPose(desiredPosition, desiredOrientation);
      HandTrajectoryMessage handTrajectoryMessage = new HandTrajectoryMessage(robotSide, baseForControl, trajectoryTime, desiredPosition, desiredOrientation);
      output.setHandTrajectoryMessage(handTrajectoryMessage);
   }

   public void computeChestTrajectoryMessage()
   {
      checkIfDataHasBeenSet();

      ReferenceFrame chestFrame = fullRobotModelToUseForConversion.getChest().getBodyFixedFrame();
      Quat4d desiredQuaternion = new Quat4d();
      FrameOrientation desiredOrientation = new FrameOrientation(chestFrame);
      desiredOrientation.changeFrame(worldFrame);
      desiredOrientation.getQuaternion(desiredQuaternion);
      ChestTrajectoryMessage chestTrajectoryMessage = new ChestTrajectoryMessage(trajectoryTime, desiredQuaternion);
      output.setChestTrajectoryMessage(chestTrajectoryMessage);
   }

   public void computePelvisTrajectoryMessage()
   {
      checkIfDataHasBeenSet();

      Point3d desiredPosition = new Point3d();
      Quat4d desiredOrientation = new Quat4d();
      ReferenceFrame pelvisFrame = fullRobotModelToUseForConversion.getRootJoint().getFrameAfterJoint();
      FramePose desiredPelvisPose = new FramePose(pelvisFrame);
      desiredPelvisPose.changeFrame(worldFrame);
      desiredPelvisPose.getPose(desiredPosition, desiredOrientation);
      PelvisTrajectoryMessage pelvisTrajectoryMessage = new PelvisTrajectoryMessage(trajectoryTime, desiredPosition, desiredOrientation);
      output.setPelvisTrajectoryMessage(pelvisTrajectoryMessage);
   }

   public void computeFootTrajectoryMessages()
   {
      for (RobotSide robotSide : RobotSide.values)
         computeFootTrajectoryMessage(robotSide);
   }

   public void computeFootTrajectoryMessage(RobotSide robotSide)
   {
      checkIfDataHasBeenSet();

      Point3d desiredPosition = new Point3d();
      Quat4d desiredOrientation = new Quat4d();
      ReferenceFrame footFrame = fullRobotModelToUseForConversion.getEndEffectorFrame(robotSide, LimbName.LEG);
      FramePose desiredFootPose = new FramePose(footFrame);
      desiredFootPose.changeFrame(worldFrame);
      desiredFootPose.getPose(desiredPosition, desiredOrientation);
      FootTrajectoryMessage footTrajectoryMessage = new FootTrajectoryMessage(robotSide, trajectoryTime, desiredPosition, desiredOrientation);
      output.setFootTrajectoryMessage(footTrajectoryMessage);
   }
   
   private void checkIfDataHasBeenSet()
   {
      if (output == null)
         throw new RuntimeException("Need to call setMessageToCreate() first.");
      if (Double.isNaN(trajectoryTime))
         throw new RuntimeException("Need to call setTrajectoryTime() first.");
   }
}
