package us.ihmc.humanoidRobotics.communication.packets.walking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import javax.vecmath.Quat4d;

import us.ihmc.communication.ros.generators.RosMessagePacket;
import us.ihmc.communication.ros.generators.RosExportedField;
import us.ihmc.communication.packets.Packet;
import us.ihmc.communication.packets.VisualizablePacket;
import us.ihmc.humanoidRobotics.communication.TransformableDataObject;
import us.ihmc.humanoidRobotics.communication.packets.ExecutionMode;
import us.ihmc.humanoidRobotics.communication.packets.PacketValidityChecker;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.random.RandomTools;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;

@RosMessagePacket(documentation =
      "This message commands the controller to execute a list of footsteps. See FootstepDataMessage for information about defining a footstep."
      + " A message with a unique id equals to 0 will be interpreted as invalid and will not be processed by the controller. This rule does not apply to the fields of this message.",
                  rosPackage = RosMessagePacket.CORE_IHMC_PACKAGE,
                  topic = "/control/footstep_list")
public class FootstepDataListMessage extends Packet<FootstepDataListMessage> implements TransformableDataObject<FootstepDataListMessage>, Iterable<FootstepDataMessage>, VisualizablePacket
{
   @RosExportedField(documentation = "Defines the list of footstep to perform.")
   public ArrayList<FootstepDataMessage> footstepDataList = new ArrayList<FootstepDataMessage>();

   @RosExportedField(documentation = "When OVERRIDE is chosen:"
         + "\n - All previously sent footstep lists will be overriden, regardless of their execution mode"
         + "\n - A footstep can be overridden up to end of transfer. Once the swing foot leaves the ground it cannot be overridden"
         + "\n When QUEUE is chosen:"
         + "\n This footstep list will be queued with previously sent footstep lists, regardless of their execution mode"
         + "\n - The trajectory time and swing time of all queued footsteps will be overwritten with this message's values.")
   public ExecutionMode executionMode = ExecutionMode.OVERRIDE;

   @RosExportedField(documentation = "swingTime is the time spent in single-support when stepping"
    + "\n - Queueing does not support varying swing times. If execution mode is QUEUE, all queued footsteps' swingTime will be overwritten")
   public double swingTime = 0.0;
   @RosExportedField(documentation = "transferTime is the time spent in double-support between steps"
   + "\n - Queueing does not support varying transfer times. If execution mode is QUEUE, all queued footsteps' transferTime will be overwritten")
   public double transferTime = 0.0;

   /**
    * Empty constructor for serialization.
    * Set the id of the message to {@link Packet#VALID_MESSAGE_DEFAULT_ID}.
    */
   public FootstepDataListMessage()
   {
      setUniqueId(VALID_MESSAGE_DEFAULT_ID);
   }

   /**
    * 
    * Set the id of the message to {@link Packet#VALID_MESSAGE_DEFAULT_ID}.
    * @param footstepDataList
    * @param swingTime
    * @param transferTime
    * @param executionMode
    */
   public FootstepDataListMessage(ArrayList<FootstepDataMessage> footstepDataList, double swingTime, double transferTime, ExecutionMode executionMode)
   {
      setUniqueId(VALID_MESSAGE_DEFAULT_ID);
      if(footstepDataList != null)
      {
         this.footstepDataList = footstepDataList;
      }
      this.swingTime = swingTime;
      this.transferTime = transferTime;
      this.executionMode = executionMode;
   }

   /**
    * 
    * Set the id of the message to {@link Packet#VALID_MESSAGE_DEFAULT_ID}. Set execution mode to OVERRIDE
    * @param swingTime
    * @param transferTime
    */
   public FootstepDataListMessage(double swingTime, double transferTime)
   {
      setUniqueId(VALID_MESSAGE_DEFAULT_ID);
      this.swingTime = swingTime;
      this.transferTime = transferTime;
      this.executionMode = ExecutionMode.OVERRIDE;
   }

   public ArrayList<FootstepDataMessage> getDataList()
   {
      return footstepDataList;
   }

   public void add(FootstepDataMessage footstepData)
   {
      this.footstepDataList.add(footstepData);
   }

   public void clear()
   {
      this.footstepDataList.clear();
   }

   public FootstepDataMessage get(int i)
   {
      return this.footstepDataList.get(i);
   }

   public int size()
   {
      return this.footstepDataList.size();
   }


   public boolean epsilonEquals(FootstepDataListMessage otherList, double epsilon)
   {
      for (int i = 0; i < size(); i++)
      {
         if (!otherList.get(i).epsilonEquals(get(i), epsilon))
         {
            return false;
         }
      }

      if (Math.abs(swingTime - otherList.swingTime) > epsilon)
      {
         return false;
      }

      if (Math.abs(transferTime - otherList.transferTime) > epsilon)
      {
         return false;
      }

      if (this.executionMode != otherList.executionMode)
      {
         return false;
      }

      return true;
   }

   public String toString()
   {
      String startingFootstep = "";
      int numberOfSteps = this.size();
      if (numberOfSteps > 0)
      {
         startingFootstep = this.get(0).getLocation().toString();
         Quat4d quat4d = this.get(0).getOrientation();

         FrameOrientation frameOrientation = new FrameOrientation(ReferenceFrame.getWorldFrame(), quat4d);
         startingFootstep = startingFootstep + ", ypr= " + Arrays.toString(frameOrientation.getYawPitchRoll());
      }

      if (this.size() == 1)
      {
         RobotSide footSide = footstepDataList.get(0).getRobotSide();
         String side = "Right Step";
         if (footSide.getSideNameFirstLetter().equals("L"))
            side = "Left Step";

         return (side);
      }
      else
      {
         return ("Starting Footstep: " + startingFootstep + "\n\tExecution Mode: " + this.executionMode + "\n\tTransfer Time: " + transferTime + "\n\tSwing Time: " + swingTime + "\n\tSize: " + this.size() + " Footsteps");
      }
   }

   public Iterator<FootstepDataMessage> iterator()
   {
      return footstepDataList.iterator();
   }

   public FootstepDataListMessage transform(RigidBodyTransform transform)
   {
      FootstepDataListMessage ret = new FootstepDataListMessage(swingTime, transferTime);

      for (FootstepDataMessage footstepData : footstepDataList)
      {
         FootstepDataMessage transformedFootstepData = footstepData.transform(transform);
         ret.add(transformedFootstepData);
      }

      return ret;
   }

   public void setSwingTime(double swingTime)
   {
      this.swingTime = swingTime;
   }

   public void setTransferTime(double transferTime)
   {
      this.transferTime = transferTime;
   }

   public void setExecutionMode(ExecutionMode executionMode)
   {
      this.executionMode = executionMode;
   }

   public FootstepDataListMessage(Random random)
   {
      setUniqueId(1L);
      int footstepListSize = random.nextInt(20);
      for (int i = 0; i < footstepListSize; i++)
      {
         footstepDataList.add(new FootstepDataMessage(random));
      }

      this.swingTime = RandomTools.generateRandomDoubleWithEdgeCases(random, 0.1);
      this.transferTime = RandomTools.generateRandomDoubleWithEdgeCases(random, 0.1);
      this.executionMode = RandomTools.generateRandomEnum(random, ExecutionMode.class);
   }

   /** {@inheritDoc} */
   @Override
   public String validateMessage()
   {
      return PacketValidityChecker.validateFootstepDataListMessage(this);
   }
}
