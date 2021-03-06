package us.ihmc.humanoidBehaviors.behaviors;

import java.util.ArrayList;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;

import us.ihmc.SdfLoader.SDFFullHumanoidRobotModel;
import us.ihmc.humanoidBehaviors.behaviors.primitives.EndEffectorLoadBearingBehavior;
import us.ihmc.humanoidBehaviors.behaviors.primitives.FootTrajectoryBehavior;
import us.ihmc.humanoidBehaviors.communication.OutgoingCommunicationBridgeInterface;
import us.ihmc.humanoidBehaviors.taskExecutor.BehaviorTask;
import us.ihmc.humanoidBehaviors.taskExecutor.FootTrajectoryTask;
import us.ihmc.humanoidRobotics.communication.packets.walking.EndEffectorLoadBearingMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.EndEffectorLoadBearingMessage.EndEffector;
import us.ihmc.humanoidRobotics.communication.packets.walking.EndEffectorLoadBearingMessage.LoadBearingRequest;
import us.ihmc.humanoidRobotics.frames.HumanoidReferenceFrames;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FramePoint2d;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.tools.taskExecutor.PipeLine;

public class KickBehavior extends BehaviorInterface
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private final DoubleYoVariable yoTime;
   private final ReferenceFrame midZupFrame;
   private BooleanYoVariable hasInputBeenSet = new BooleanYoVariable("hasInputBeenSet", registry);
   private final FootTrajectoryBehavior footTrajectoryBehavior;

   private FramePoint2d objectToKickPose;

   private final ArrayList<BehaviorInterface> behaviors = new ArrayList<BehaviorInterface>();

   private final PipeLine<BehaviorInterface> pipeLine = new PipeLine<>();
   private final DoubleYoVariable trajectoryTime;
   private final SideDependentList<ReferenceFrame> ankleZUpFrames;

   public KickBehavior(OutgoingCommunicationBridgeInterface outgoingCommunicationBridge, DoubleYoVariable yoTime, BooleanYoVariable yoDoubleSupport,
         SDFFullHumanoidRobotModel fullRobotModel, HumanoidReferenceFrames referenceFrames)
   {
      super(outgoingCommunicationBridge);
      this.yoTime = yoTime;
      midZupFrame = referenceFrames.getMidFeetZUpFrame();
      trajectoryTime = new DoubleYoVariable("kickTrajectoryTime", registry);
      trajectoryTime.set(0.5);
      ankleZUpFrames = referenceFrames.getAnkleZUpReferenceFrames();

      footTrajectoryBehavior = new FootTrajectoryBehavior(outgoingCommunicationBridge, yoTime, yoDoubleSupport);
      registry.addChild(footTrajectoryBehavior.getYoVariableRegistry());
   }

   @Override public void doControl()
   {
      if (!hasInputBeenSet.getBooleanValue())
      {
         // setObjectToKickPoint(new
         // FramePoint(ReferenceFrame.getWorldFrame(),0,0,0));
         checkInput();
      }
      else
      {
         pipeLine.doControl();
      }
   }

   public void setObjectToKickPoint(FramePoint2d objectToKickPose)
   {
      this.objectToKickPose = objectToKickPose;
   }

   private void checkInput()
   {
      if (objectToKickPose != null)
      {
         hasInputBeenSet.set(true);
         setupPipeline();
      }
   }

   private void setupPipeline()
   {
      final RobotSide kickFoot = RobotSide.LEFT;

      // submitFootPosition(kickFoot.getOppositeSide(), new
      // FramePoint(ankleZUpFrames.get(kickFoot), 0.0,
      // kickFoot.getOppositeSide().negateIfRightSide(0.25), 0.227));
      //
      // submitFootPosition(kickFoot.getOppositeSide(), new
      // FramePoint(ankleZUpFrames.get(kickFoot), 0.0,
      // kickFoot.getOppositeSide().negateIfRightSide(0.35), 0.227));
      //
      //
      //
      // submitFootPosition(kickFoot.getOppositeSide(), new
      // FramePoint(ankleZUpFrames.get(kickFoot), 0.0,
      // kickFoot.getOppositeSide().negateIfRightSide(0.35), 0));

      submitFootPosition(kickFoot, new FramePoint(ankleZUpFrames.get(kickFoot.getOppositeSide()), 0.0, kickFoot.negateIfRightSide(0.25), 0.227));

      submitFootPosition(kickFoot, new FramePoint(ankleZUpFrames.get(kickFoot.getOppositeSide()), -0.2, kickFoot.negateIfRightSide(0.15), 0.127));

      submitFootPosition(kickFoot, new FramePoint(ankleZUpFrames.get(kickFoot.getOppositeSide()), 0.3, kickFoot.negateIfRightSide(0.15), 0.05));

      // submitFootPosition(kickFoot, new
      // FramePoint(objectToKickPose.getReferenceFrame(),
      // objectToKickPose.getX(), objectToKickPose.getY(), 0.127));

      submitFootPosition(kickFoot, new FramePoint(ankleZUpFrames.get(kickFoot.getOppositeSide()), 0.0, kickFoot.negateIfRightSide(0.25), 0.127));

      submitFootPosition(kickFoot, new FramePoint(ankleZUpFrames.get(kickFoot.getOppositeSide()), 0.0, kickFoot.negateIfRightSide(0.25), 0));

      final EndEffectorLoadBearingBehavior footStateBehavior = new EndEffectorLoadBearingBehavior(outgoingCommunicationBridge);
      pipeLine.submitSingleTaskStage(new BehaviorTask(footStateBehavior, yoTime)
      {

         @Override protected void setBehaviorInput()
         {
            EndEffectorLoadBearingMessage message = new EndEffectorLoadBearingMessage(kickFoot, EndEffector.FOOT, LoadBearingRequest.LOAD);
            footStateBehavior.setInput(message);

         }
      });
   }

   private void submitFootPosition(RobotSide robotSide, FramePoint desiredFootPosition)
   {
      FrameOrientation desiredFootOrientation = new FrameOrientation(desiredFootPosition.getReferenceFrame());
      FramePose desiredFootPose = new FramePose(desiredFootPosition, desiredFootOrientation);
      submitFootPose(robotSide, desiredFootPose);
   }

   private void submitFootPose(RobotSide robotSide, FramePose desiredFootPose)
   {
      desiredFootPose.changeFrame(worldFrame);
      Point3d desiredFootPosition = new Point3d();
      Quat4d desiredFootOrientation = new Quat4d();
      desiredFootPose.getPose(desiredFootPosition, desiredFootOrientation);
      FootTrajectoryTask task = new FootTrajectoryTask(robotSide, desiredFootPosition, desiredFootOrientation, yoTime, footTrajectoryBehavior,
            trajectoryTime.getDoubleValue());
      pipeLine.submitSingleTaskStage(task);
   }

   @Override public void initialize()
   {
      defaultInitialize();

      for (BehaviorInterface behavior : behaviors)
      {
         behavior.initialize();
      }
   }

   @Override public void doPostBehaviorCleanup()
   {
      hasInputBeenSet.set(false);
      defaultPostBehaviorCleanup();

      for (BehaviorInterface behavior : behaviors)
      {
         behavior.doPostBehaviorCleanup();
      }
   }

   @Override public void stop()
   {
      defaultStop();

      for (BehaviorInterface behavior : behaviors)
      {
         behavior.stop();
      }
   }

   @Override public void pause()
   {
      defaultPause();

      for (BehaviorInterface behavior : behaviors)
      {
         behavior.pause();
      }
   }

   @Override public boolean isDone()
   {
      return hasInputBeenSet() && defaultIsDone() && pipeLine.isDone();
   }

   @Override public void enableActions()
   {
   }

   @Override public void resume()
   {
      defaultResume();

      for (BehaviorInterface behavior : behaviors)
      {
         behavior.resume();
      }
   }

   @Override protected void passReceivedNetworkProcessorObjectToChildBehaviors(Object object)
   {
      for (BehaviorInterface behavior : behaviors)
      {
         behavior.consumeObjectFromNetworkProcessor(object);
      }
   }

   @Override protected void passReceivedControllerObjectToChildBehaviors(Object object)
   {
      for (BehaviorInterface behavior : behaviors)
      {
         behavior.consumeObjectFromController(object);
      }
   }

   public boolean hasInputBeenSet()
   {
      return hasInputBeenSet.getBooleanValue();
   }
}
