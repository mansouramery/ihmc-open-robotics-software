package us.ihmc.humanoidBehaviors.behaviors.primitives;

import us.ihmc.communication.packets.PacketDestination;
import us.ihmc.humanoidBehaviors.behaviors.BehaviorInterface;
import us.ihmc.humanoidBehaviors.communication.OutgoingCommunicationBridgeInterface;
import us.ihmc.humanoidRobotics.communication.packets.walking.PelvisHeightTrajectoryMessage;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;

public class PelvisHeightTrajectoryBehavior extends BehaviorInterface
{
   private final BooleanYoVariable hasInputBeenSet = new BooleanYoVariable("hasInputBeenSet" + behaviorName, registry);
   private final BooleanYoVariable packetHasBeenSent = new BooleanYoVariable("packetHasBeenSent" + behaviorName, registry);
   private final BooleanYoVariable trajectoryTimeElapsed = new BooleanYoVariable(getName() + "TrajectoryTimeElapsed", registry);
   private PelvisHeightTrajectoryMessage outgoingPelvisHeightTrajectoryMessage;

   private final DoubleYoVariable yoTime;
   private final DoubleYoVariable startTime;
   private final DoubleYoVariable trajectoryTime;

   public PelvisHeightTrajectoryBehavior(OutgoingCommunicationBridgeInterface outgoingCommunicationBridge, DoubleYoVariable yoTime)
   {
      super(outgoingCommunicationBridge);
      startTime = new DoubleYoVariable(getName() + "StartTime", registry);
      startTime.set(Double.NaN);
      trajectoryTime = new DoubleYoVariable(getName() + "TrajectoryTime", registry);
      trajectoryTime.set(Double.NaN);
      this.yoTime = yoTime;
   }

   public void setInput(PelvisHeightTrajectoryMessage pelvisHeightTrajectoryMessage)
   {
      this.outgoingPelvisHeightTrajectoryMessage = pelvisHeightTrajectoryMessage;
      hasInputBeenSet.set(true);
   }

   @Override
   public void doControl()
   {
      if (!packetHasBeenSent.getBooleanValue() &&  hasInputBeenSet())
      {
         sendMessageToController();
      }
   }

   private void sendMessageToController()
   {
      if (!isPaused.getBooleanValue() && !isStopped.getBooleanValue())
      {      
         outgoingPelvisHeightTrajectoryMessage.setDestination(PacketDestination.UI);  
         sendPacketToController(outgoingPelvisHeightTrajectoryMessage);
         sendPacketToNetworkProcessor(outgoingPelvisHeightTrajectoryMessage);
         packetHasBeenSent.set(true);
         startTime.set(yoTime.getDoubleValue());
         trajectoryTime.set(outgoingPelvisHeightTrajectoryMessage.getTrajectoryTime());
      }
   }

   @Override
   public void initialize()
   {
      packetHasBeenSent.set(false);

      isPaused.set(false);
      isStopped.set(false);
      
      hasInputBeenSet.set(false);
      trajectoryTimeElapsed.set(false);
      
      hasBeenInitialized.set(true);
   }

   @Override
   public void doPostBehaviorCleanup()
   {
      packetHasBeenSent.set(false);
      hasInputBeenSet.set(false);
      trajectoryTimeElapsed.set(false);
      outgoingPelvisHeightTrajectoryMessage = null;

      startTime.set(Double.NaN);
      trajectoryTime.set(Double.NaN);
      
      isPaused.set(false);
      isStopped.set(false);
   }

   @Override
   public void stop()
   {
      isStopped.set(true);
   }

   @Override
   public void pause()
   {
      isPaused.set(true);
   }

   @Override
   public void resume()
   {
      isPaused.set(false);
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
      return hasInputBeenSet.getBooleanValue();
   }
}
