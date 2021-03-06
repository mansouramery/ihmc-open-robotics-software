package us.ihmc.humanoidBehaviors.behaviors;

import java.util.ArrayList;

import us.ihmc.SdfLoader.models.FullHumanoidRobotModel;
import us.ihmc.SdfLoader.models.FullRobotModel;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.humanoidBehaviors.behaviors.primitives.FootstepListBehavior;
import us.ihmc.humanoidBehaviors.communication.OutgoingCommunicationBridgeInterface;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.humanoidRobotics.footstep.footstepGenerator.SimplePathParameters;
import us.ihmc.humanoidRobotics.footstep.footstepGenerator.TurnInPlaceFootstepGenerator;
import us.ihmc.humanoidRobotics.frames.HumanoidReferenceFrames;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.geometry.FrameOrientation2d;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.robotics.screwTheory.RigidBody;

public class TurnInPlaceBehavior extends BehaviorInterface
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private final boolean DEBUG = false;
   private final FullRobotModel fullRobotModel;
   private final HumanoidReferenceFrames referenceFrames;

   private double swingTime;
   private double transferTime;

   private final BooleanYoVariable hasTargetBeenProvided = new BooleanYoVariable("hasTargetBeenProvided", registry);
   private final BooleanYoVariable haveFootstepsBeenGenerated = new BooleanYoVariable("haveFootstepsBeenGenerated", registry);

   private SimplePathParameters pathType;// = new SimplePathParameters(0.4, 0.30, 0.0, Math.toRadians(10.0), Math.toRadians(5.0), 0.4);
   
   private ArrayList<Footstep> footsteps = new ArrayList<Footstep>();
   private FootstepListBehavior footstepListBehavior;

   private final SideDependentList<RigidBody> feet = new SideDependentList<RigidBody>();
   private final SideDependentList<ReferenceFrame> soleFrames = new SideDependentList<ReferenceFrame>();
   private FrameOrientation2d targetOrientationInWorldFrame;

   public TurnInPlaceBehavior(OutgoingCommunicationBridgeInterface outgoingCommunicationBridge, FullHumanoidRobotModel fullRobotModel,
         HumanoidReferenceFrames referenceFrames, WalkingControllerParameters walkingControllerParameters)
   {
      super(outgoingCommunicationBridge);

      this.fullRobotModel = fullRobotModel;
      this.referenceFrames = referenceFrames;

      this.swingTime = walkingControllerParameters.getDefaultSwingTime();
      this.transferTime = walkingControllerParameters.getDefaultTransferTime();

      this.pathType = new SimplePathParameters(walkingControllerParameters.getMaxStepLength(), walkingControllerParameters.getInPlaceWidth(), 0.0,
            Math.toRadians(20.0), Math.toRadians(10.0), 0.4); // 10 5 0.4
      footstepListBehavior = new FootstepListBehavior(outgoingCommunicationBridge, walkingControllerParameters);

      for (RobotSide robotSide : RobotSide.values)
      {
         feet.put(robotSide, fullRobotModel.getFoot(robotSide));
         soleFrames.put(robotSide, fullRobotModel.getSoleFrame(robotSide));
      }
   }

   public void setTarget(double desiredYaw)
   {
      targetOrientationInWorldFrame = new FrameOrientation2d(referenceFrames.getMidFeetZUpFrame());
      targetOrientationInWorldFrame.setYaw(desiredYaw);
      targetOrientationInWorldFrame.changeFrame(worldFrame);
      
      hasTargetBeenProvided.set(true);
      generateFootsteps();
   }

   public void setSwingTime(double swingTime)
   {
      this.swingTime = swingTime;
   }

   public void setTransferTime(double transferTime)
   {
      this.transferTime = transferTime;
   }

   @Override
   public void initialize()
   {
      hasTargetBeenProvided.set(false);
      haveFootstepsBeenGenerated.set(false);
      footstepListBehavior.initialize();
   }

   public int getNumberOfFootSteps()
   {
      return footsteps.size();
   }

   public ArrayList<Footstep> getFootSteps()
   {
      return footsteps;
   }

   private void generateFootsteps()
   {
      footsteps.clear();
      
      TurnInPlaceFootstepGenerator footstepGenerator = new TurnInPlaceFootstepGenerator(feet, soleFrames, targetOrientationInWorldFrame, pathType);
      footstepGenerator.initialize();
      
      footsteps.addAll(footstepGenerator.generateDesiredFootstepList());

      FramePoint midFeetPoint = new FramePoint();
      midFeetPoint.setToZero(referenceFrames.getMidFeetZUpFrame());
      midFeetPoint.changeFrame(worldFrame);
      
      for(Footstep footstep : footsteps)
      {
         footstep.setZ(midFeetPoint.getZ());
      }
      
      footstepListBehavior.set(footsteps, swingTime, transferTime);
      haveFootstepsBeenGenerated.set(true);
      
   }

   @Override
   public void doControl()
   {
      if (!hasTargetBeenProvided.getBooleanValue())
         return;
      if (!haveFootstepsBeenGenerated.getBooleanValue())
         generateFootsteps();
      footstepListBehavior.doControl();
   }

   @Override
   protected void passReceivedNetworkProcessorObjectToChildBehaviors(Object object)
   {
      if (footstepListBehavior != null)
         footstepListBehavior.consumeObjectFromNetworkProcessor(object);
   }

   @Override
   protected void passReceivedControllerObjectToChildBehaviors(Object object)
   {
      if (footstepListBehavior != null)
         footstepListBehavior.consumeObjectFromController(object);
   }

   @Override
   public void stop()
   {
      footstepListBehavior.stop();
      isStopped.set(true);
   }

   @Override
   public void enableActions()
   {
      // TODO Auto-generated method stub

   }

   @Override
   public void pause()
   {
      footstepListBehavior.pause();
      isPaused.set(true);
   }

   @Override
   public void resume()
   {
      footstepListBehavior.resume();
      isPaused.set(false);

   }

   @Override
   public boolean isDone()
   {
      if (!haveFootstepsBeenGenerated.getBooleanValue() || !hasTargetBeenProvided.getBooleanValue())
         return false;
      if (haveFootstepsBeenGenerated.getBooleanValue() && footsteps.size() == 0)
         return true;
      return footstepListBehavior.isDone();
   }

   @Override
   public void doPostBehaviorCleanup()
   {
      isPaused.set(false);
      isStopped.set(false);
      hasTargetBeenProvided.set(false);
      haveFootstepsBeenGenerated.set(false);
      footstepListBehavior.doPostBehaviorCleanup();
   }

   public boolean hasInputBeenSet()
   {
      if (haveFootstepsBeenGenerated.getBooleanValue())
         return true;
      else
         return false;
   }

   public void setFootstepLength(double footstepLength)
   {
      pathType.setStepLength(footstepLength);
   }
}
