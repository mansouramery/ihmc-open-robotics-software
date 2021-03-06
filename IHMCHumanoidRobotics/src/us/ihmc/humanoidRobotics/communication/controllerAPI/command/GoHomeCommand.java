package us.ihmc.humanoidRobotics.communication.controllerAPI.command;

import java.util.EnumMap;

import org.apache.commons.lang3.mutable.MutableBoolean;

import us.ihmc.communication.controllerAPI.command.CompilableCommand;
import us.ihmc.humanoidRobotics.communication.packets.walking.GoHomeMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.GoHomeMessage.BodyPart;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;

/**
 * Upon receiving a {@link GoHomeCommand} the controller will bring the given part of the body back to a default configuration called 'home'.
 * It is useful to get back to a safe configuration before walking.
 * 
 * 
 * @author Sylvain
 *
 */
public class GoHomeCommand implements CompilableCommand<GoHomeCommand, GoHomeMessage>
{
   private final SideDependentList<EnumMap<BodyPart, MutableBoolean>> sideDependentBodyPartRequestMap = SideDependentList.createListOfEnumMaps(BodyPart.class);
   private final EnumMap<BodyPart, MutableBoolean> otherBodyPartRequestMap = new EnumMap<>(BodyPart.class);
   private double trajectoryTime = 1.0;

   /**
    * 
    */
   public GoHomeCommand()
   {
      for (BodyPart bodyPart : BodyPart.values)
      {
         if (bodyPart.isRobotSideNeeded())
         {
            for (RobotSide robotSide : RobotSide.values)
            {
               sideDependentBodyPartRequestMap.get(robotSide).put(bodyPart, new MutableBoolean(false));
            }
         }
         else
         {
            otherBodyPartRequestMap.put(bodyPart, new MutableBoolean(false));
         }
      }
   }

   /** {@inheritDoc} */
   @Override
   public void clear()
   {
      for (BodyPart bodyPart : BodyPart.values)
      {
         if (bodyPart.isRobotSideNeeded())
         {
            for (RobotSide robotSide : RobotSide.values)
            {
               sideDependentBodyPartRequestMap.get(robotSide).get(bodyPart).setFalse();
            }
         }
         else
         {
            otherBodyPartRequestMap.get(bodyPart).setFalse();
         }
      }
   }

   /** {@inheritDoc} */
   @Override
   public void set(GoHomeMessage message)
   {
      trajectoryTime = message.getTrajectoryTime();

      BodyPart bodyPart = message.getBodyPart();
      if (bodyPart.isRobotSideNeeded())
      {
         RobotSide robotSide = message.getRobotSide();
         sideDependentBodyPartRequestMap.get(robotSide).get(bodyPart).setTrue();
      }
      else
      {
         otherBodyPartRequestMap.get(bodyPart).setTrue();
      }
   }

   /** {@inheritDoc} */
   @Override
   public void set(GoHomeCommand other)
   {
      clear();
      compile(other);
   }

   /** {@inheritDoc} */
   @Override
   public void compile(GoHomeCommand other)
   {
      trajectoryTime = other.trajectoryTime;

      for (BodyPart bodyPart : BodyPart.values)
      {
         if (bodyPart.isRobotSideNeeded())
         {
            for (RobotSide robotSide : RobotSide.values)
            {
               sideDependentBodyPartRequestMap.get(robotSide).get(bodyPart).setValue(other.getRequest(robotSide, bodyPart));
            }
         }
         else
         {
            otherBodyPartRequestMap.get(bodyPart).setValue(other.getRequest(bodyPart));
         }
      }
   }

   /**
    * @return the duration for going back to the home configuration.
    */
   public double getTrajectoryTime()
   {
      return trajectoryTime;
   }

   /**
    * Get the request for going home for a given body part.
    * This method is to use only for body parts that are not side dependent, like the pelvis.
    * @param bodyPart body part to check the request for.
    * @return true if the go home is requested, false otherwise.
    * @throws RuntimeException if the robot side is need for the given body part.
    */
   public boolean getRequest(BodyPart bodyPart)
   {
      if (bodyPart.isRobotSideNeeded())
         throw new RuntimeException("Need to provide robotSide for the bodyPart: " + bodyPart);
      return otherBodyPartRequestMap.get(bodyPart).booleanValue();
   }

   /**
    * Get the request for going home for a given body part.
    * This method is to use for body parts that are side dependent, like a foot.
    * @param robotSide which side the body part belongs to.
    * @param bodyPart body part to check the request for.
    * @return true if the go home is requested, false otherwise.
    */
   public boolean getRequest(RobotSide robotSide, BodyPart bodyPart)
   {
      if (bodyPart.isRobotSideNeeded())
         return sideDependentBodyPartRequestMap.get(robotSide).get(bodyPart).booleanValue();
      else
         return getRequest(bodyPart);
   }

   /** {@inheritDoc} */
   @Override
   public Class<GoHomeMessage> getMessageClass()
   {
      return GoHomeMessage.class;
   }

   /** {@inheritDoc} */
   @Override
   public boolean isCommandValid()
   {
      return true;
   }
}
