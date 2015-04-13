package us.ihmc.humanoidBehaviors.utilities;

import static org.junit.Assert.assertTrue;
import us.ihmc.communication.packets.behaviors.HumanoidBehaviorControlModePacket.HumanoidBehaviorControlModeEnum;
import us.ihmc.communication.subscribers.RobotDataReceiver;
import us.ihmc.humanoidBehaviors.behaviors.BehaviorInterface;
import us.ihmc.utilities.io.printing.PrintTools;
import us.ihmc.utilities.math.geometry.FramePose;
import us.ihmc.utilities.math.geometry.FramePose2d;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.math.geometry.RigidBodyTransform;

public class TrajectoryBasedStopThreadUpdatable extends StopThreadUpdatable
{
   private final boolean DEBUG = false;

   private final FramePose initialPose;
   private final FramePose currentPose;
   private final FramePose poseAtTrajectoryEnd;
   private boolean initialPoseHasBeenSet = false;

   private double trajectoryLength;

   private double elapsedTime = 0.0;
   private double elapsedTimeOld = 0.0;
   private double startTime = Double.NaN;
   private double pauseStartTime = Double.NaN;
   private double doneTime = Double.NaN;
   private double percentTrajectoryCompleted = 0.0;
   private double percentTrajectoryCompletedOld = 0.0;
   private final double pausePercent;
   private final double pauseDuration;
   private final double stopPercent;

   public TrajectoryBasedStopThreadUpdatable(RobotDataReceiver robotDataReceiver, BehaviorInterface behavior, double pausePercent, double pauseDuration,
         double stopPercent, FramePose2d pose2dAtTrajectoryEnd, ReferenceFrame frameToKeepTrackOf)
   {
      this(robotDataReceiver, behavior, pausePercent, pauseDuration, stopPercent, new FramePose(), frameToKeepTrackOf);

      RigidBodyTransform transformToWorldAtTrajectoryEnd = new RigidBodyTransform();
      pose2dAtTrajectoryEnd.getPose(transformToWorldAtTrajectoryEnd);
      poseAtTrajectoryEnd.setPoseIncludingFrame(worldFrame, transformToWorldAtTrajectoryEnd);
   }

   public TrajectoryBasedStopThreadUpdatable(RobotDataReceiver robotDataReceiver, BehaviorInterface behavior, double pausePercent, double pauseDuration,
         double stopPercent, FramePose poseAtTrajectoryEnd, ReferenceFrame frameToKeepTrackOf)
   {
      super(robotDataReceiver, behavior, frameToKeepTrackOf);

      this.initialPose = new FramePose();
      this.currentPose = new FramePose();
      this.poseAtTrajectoryEnd = poseAtTrajectoryEnd;

      this.pausePercent = pausePercent;
      this.pauseDuration = pauseDuration;
      this.stopPercent = stopPercent;
   }

   @Override
   public void update(double time)
   {
      if (Double.isNaN(startTime))
      {
         startTime = time;
      }
      elapsedTime = time - startTime;

      if (!initialPoseHasBeenSet)
      {
         getCurrentTestFramePose(initialPose);

         this.trajectoryLength = initialPose.getPositionDistance(poseAtTrajectoryEnd);
         initialPoseHasBeenSet = true;
      }

      getCurrentTestFramePose(currentPose);
      double trajectoryLengthCompleted = initialPose.getPositionDistance(currentPose);
      percentTrajectoryCompleted = 100.0 * trajectoryLengthCompleted / trajectoryLength;

      if (hasThresholdBeenCrossed(pausePercent))
      {
         PrintTools.debug(this, "Requesting Pause");
         setRequestedBehaviorControlMode(HumanoidBehaviorControlModeEnum.PAUSE);
         pauseStartTime = elapsedTime;
      }
      else if ((elapsedTimeOld - pauseStartTime) < pauseDuration && (elapsedTime - pauseStartTime) >= pauseDuration)
      {
         assertTrue(!behavior.isDone());

         PrintTools.debug(this, "Requesting Resume");
         setRequestedBehaviorControlMode(HumanoidBehaviorControlModeEnum.RESUME);
      }
      else if (hasThresholdBeenCrossed(stopPercent))
      {
         PrintTools.debug(this, "Requesting Stop");
         setRequestedBehaviorControlMode(HumanoidBehaviorControlModeEnum.STOP);
      }
      else if (behavior.isDone())
      {
         doneTime = time;
         setShouldBehaviorRunnerBeStopped(true);
      }
      else if (getRequestedBehaviorControlMode().equals(HumanoidBehaviorControlModeEnum.STOP))
      {
         if (Double.isNaN(doneTime))
         {
            doneTime = elapsedTime + 2.0;
         }
         else if (elapsedTime > doneTime)
         {
            setShouldBehaviorRunnerBeStopped(true);
         }
      }
      elapsedTimeOld = elapsedTime;
      percentTrajectoryCompletedOld = percentTrajectoryCompleted;
   }

   private boolean hasThresholdBeenCrossed(double percentageThreshold)
   {
      boolean ret = percentTrajectoryCompletedOld < percentageThreshold && percentTrajectoryCompleted >= percentageThreshold;
      return ret;
   }

   public double getPercentTrajectoryCompleted()
   {
      return percentTrajectoryCompleted;
   }
}