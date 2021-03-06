package us.ihmc.humanoidRobotics.communication.packets.walking;

import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import us.ihmc.communication.ros.generators.RosMessagePacket;
import us.ihmc.communication.packets.VisualizablePacket;
import us.ihmc.humanoidRobotics.communication.packets.AbstractSO3TrajectoryMessage;
import us.ihmc.humanoidRobotics.communication.packets.PacketValidityChecker;
import us.ihmc.robotics.geometry.RigidBodyTransform;

import java.util.Random;

@RosMessagePacket(documentation =
      "This message commands the controller to move in taskspace the chest to the desired orientation while going through the specified trajectory points."
      + " A hermite based curve (third order) is used to interpolate the orientations."
      + " To excute a simple trajectory to reach a desired chest orientation, set only one trajectory point with zero velocity and its time to be equal to the desired trajectory time."
      + " A message with a unique id equals to 0 will be interpreted as invalid and will not be processed by the controller. This rule does not apply to the fields of this message.",
                  rosPackage = RosMessagePacket.CORE_IHMC_PACKAGE,
                  topic = "/control/chest_trajectory")
public class ChestTrajectoryMessage extends AbstractSO3TrajectoryMessage<ChestTrajectoryMessage> implements VisualizablePacket
{
   /**
    * Empty constructor for serialization.
    */
   public ChestTrajectoryMessage()
   {
      super();
      setUniqueId(VALID_MESSAGE_DEFAULT_ID);
   }

   public ChestTrajectoryMessage(Random random)
   {
      super(random);
      setUniqueId(VALID_MESSAGE_DEFAULT_ID);
   }

   public ChestTrajectoryMessage(ChestTrajectoryMessage chestTrajectoryMessage)
   {
      super(chestTrajectoryMessage);
      setUniqueId(VALID_MESSAGE_DEFAULT_ID);
      setDestination(chestTrajectoryMessage.getDestination());
   }

   /**
    * Use this constructor to execute a simple interpolation in taskspace to the desired orientation.
    * @param trajectoryTime how long it takes to reach the desired orientation.
    * @param desiredOrientation desired chest orientation expressed in world frame.
    */
   public ChestTrajectoryMessage(double trajectoryTime, Quat4d desiredOrientation)
   {
      super(trajectoryTime, desiredOrientation);
      setUniqueId(VALID_MESSAGE_DEFAULT_ID);
   }

   /**
    * Use this constructor to build a message with more than one trajectory point.
    * This constructor only allocates memory for the trajectory points, you need to call {@link #setTrajectoryPoint(int, double, Quat4d, Vector3d)} for each trajectory point afterwards.
    * @param numberOfTrajectoryPoints number of trajectory points that will be sent to the controller.
    */
   public ChestTrajectoryMessage(int numberOfTrajectoryPoints)
   {
      super(numberOfTrajectoryPoints);
      setUniqueId(VALID_MESSAGE_DEFAULT_ID);
   }

   @Override
   public boolean epsilonEquals(ChestTrajectoryMessage other, double epsilon)
   {
      return super.epsilonEquals(other, epsilon);
   }

   @Override
   public ChestTrajectoryMessage transform(RigidBodyTransform transform)
   {
      ChestTrajectoryMessage transformedChestTrajectoryMessage = new ChestTrajectoryMessage(this);
      transformedChestTrajectoryMessage.applyTransform(transform);
      return transformedChestTrajectoryMessage;
   }

   @Override
   public String toString()
   {
      if (taskspaceTrajectoryPoints != null)
         return "Chest SO3 trajectory: number of SO3 trajectory points = " + getNumberOfTrajectoryPoints();
      else
         return "Chest SO3 trajectory: no SO3 trajectory points";
   }

   /** {@inheritDoc} */
   @Override
   public String validateMessage()
   {
      return PacketValidityChecker.validateChestTrajectoryMessage(this);
   }
}
