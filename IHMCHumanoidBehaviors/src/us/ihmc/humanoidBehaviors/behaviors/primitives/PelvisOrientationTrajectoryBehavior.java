package us.ihmc.humanoidBehaviors.behaviors.primitives;

import org.apache.commons.lang3.StringUtils;

import us.ihmc.communication.packets.PacketDestination;
import us.ihmc.humanoidBehaviors.behaviors.BehaviorInterface;
import us.ihmc.humanoidBehaviors.communication.OutgoingCommunicationBridgeInterface;
import us.ihmc.humanoidRobotics.communication.packets.walking.PelvisOrientationTrajectoryMessage;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;

public class PelvisOrientationTrajectoryBehavior extends BehaviorInterface
{
   private PelvisOrientationTrajectoryMessage outgoingPelvisOrientationTrajectoryMessage;

   private final BooleanYoVariable hasPacketBeenSent;
   private final DoubleYoVariable yoTime;
   private final DoubleYoVariable startTime;
   private final DoubleYoVariable trajectoryTime;
   private final BooleanYoVariable trajectoryTimeElapsed;

   public PelvisOrientationTrajectoryBehavior(OutgoingCommunicationBridgeInterface outgoingCommunicationBridge, DoubleYoVariable yoTime)
   {
      super(outgoingCommunicationBridge);

      this.yoTime = yoTime;
      String behaviorNameFirstLowerCase = StringUtils.uncapitalize(getName());
      hasPacketBeenSent = new BooleanYoVariable(behaviorNameFirstLowerCase + "HasPacketBeenSent", registry);
      startTime = new DoubleYoVariable(behaviorNameFirstLowerCase + "StartTime", registry);
      startTime.set(Double.NaN);
      trajectoryTime = new DoubleYoVariable(behaviorNameFirstLowerCase + "TrajectoryTime", registry);
      trajectoryTime.set(Double.NaN);
      trajectoryTimeElapsed = new BooleanYoVariable(behaviorNameFirstLowerCase + "TrajectoryTimeElapsed", registry);
   }

   public void setInput(PelvisOrientationTrajectoryMessage pelvisOrientationTrajectoryMessage)
   {
      this.outgoingPelvisOrientationTrajectoryMessage = pelvisOrientationTrajectoryMessage;
   }

   @Override
   public void doControl()
   {
      if (!hasPacketBeenSent.getBooleanValue() && (outgoingPelvisOrientationTrajectoryMessage != null))
      {
         sendPelvisPosePacketToController();
      }
   }

   private void sendPelvisPosePacketToController()
   {
      if (!isPaused.getBooleanValue() && !isStopped.getBooleanValue())
      {
         outgoingPelvisOrientationTrajectoryMessage.setDestination(PacketDestination.UI);
         sendPacketToNetworkProcessor(outgoingPelvisOrientationTrajectoryMessage);
         sendPacketToController(outgoingPelvisOrientationTrajectoryMessage);
         hasPacketBeenSent.set(true);
         startTime.set(yoTime.getDoubleValue());
         trajectoryTime.set(outgoingPelvisOrientationTrajectoryMessage.getTrajectoryTime());
      }
   }

   @Override
   public void initialize()
   {
      hasPacketBeenSent.set(false);

      isPaused.set(false);
      isStopped.set(false);
      
      hasBeenInitialized.set(true);
   }

   @Override
   public void doPostBehaviorCleanup()
   {
      hasPacketBeenSent.set(false);
      outgoingPelvisOrientationTrajectoryMessage = null;

      isPaused.set(false);
      isStopped.set(false);

      startTime.set(Double.NaN);
      trajectoryTime.set(Double.NaN);
   }

   @Override //TODO: Not currently implemented for this behavior
   public void stop()
   {
      isStopped.set(true);
   }

   @Override //TODO: Not currently implemented for this behavior
   public void pause()
   {
      isPaused.set(true);
   }

   @Override //TODO: Not currently implemented for this behavior
   public void resume()
   {
      isPaused.set(false);
      startTime.set(yoTime.getDoubleValue());
   }

   @Override
   public boolean isDone()
   {
      if (Double.isNaN(startTime.getDoubleValue()) || Double.isNaN(trajectoryTime.getDoubleValue()))
         trajectoryTimeElapsed.set(false);
      else
         trajectoryTimeElapsed.set(yoTime.getDoubleValue() - startTime.getDoubleValue() > trajectoryTime.getDoubleValue());

      return trajectoryTimeElapsed.getBooleanValue() && !isPaused.getBooleanValue();
   }

   @Override
   public void enableActions()
   {
   }

   @Override
   protected void passReceivedNetworkProcessorObjectToChildBehaviors(Object object)
   {
   }

   @Override
   protected void passReceivedControllerObjectToChildBehaviors(Object object)
   {
   }

   @Override
   public boolean hasInputBeenSet()
   {
      return outgoingPelvisOrientationTrajectoryMessage != null;
   }
}
