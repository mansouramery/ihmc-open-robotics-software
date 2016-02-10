package us.ihmc.humanoidRobotics.communication.packets;

import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import us.ihmc.communication.packetAnnotations.ClassDocumentation;
import us.ihmc.communication.packetAnnotations.FieldDocumentation;
import us.ihmc.robotics.MathTools;

@ClassDocumentation("This class is used to build trajectory messages in taskspace. It holds the only the rotational information for one waypoint (orientation & angular velocity). "
      + "Feel free to look at EuclideanWaypoint (translational) and SE3Waypoint (rotational AND translational)")
public class SO3Waypoint
{
   @FieldDocumentation("Time at which the waypoint has to be reached. The time is relative to when the trajectory starts.")
   public double time;
   @FieldDocumentation("Define the desired 3D orientation to be reached at this waypoint. It is expressed in world frame.")
   public Quat4d orientation;
   @FieldDocumentation("Define the desired 3D angular velocity to be reached at this waypoint. It is expressed in world frame.")
   public Vector3d angularVelocity;

   public SO3Waypoint()
   {
   }

   public SO3Waypoint(SO3Waypoint so3Waypoint)
   {
      time = so3Waypoint.time;
      if (so3Waypoint.orientation != null)
         orientation = new Quat4d(so3Waypoint.orientation);
      if (so3Waypoint.angularVelocity != null)
         angularVelocity = new Vector3d(so3Waypoint.angularVelocity);
   }

   public SO3Waypoint(double time, Quat4d orientation, Vector3d angularVelocity)
   {
      this.orientation = orientation;
      this.angularVelocity = angularVelocity;
      this.time = time;
   }

   public double getTime()
   {
      return time;
   }

   public void setTime(double time)
   {
      this.time = time;
   }

   public Quat4d getOrientation()
   {
      return orientation;
   }

   public void setOrientation(Quat4d orientation)
   {
      this.orientation = orientation;
   }

   public Vector3d getAngularVelocity()
   {
      return angularVelocity;
   }

   public void setAngularVelocity(Vector3d angularVelocity)
   {
      this.angularVelocity = angularVelocity;
   }

   public boolean epsilonEquals(SO3Waypoint other, double epsilon)
   {
      if (orientation == null && other.orientation != null)
         return false;
      if (orientation != null && other.orientation == null)
         return false;

      if (angularVelocity == null && other.angularVelocity != null)
         return false;
      if (angularVelocity != null && other.angularVelocity == null)
         return false;

      if (!MathTools.epsilonEquals(time, other.time, epsilon))
         return false;
      if (!orientation.epsilonEquals(other.orientation, epsilon))
         return false;
      if (!angularVelocity.epsilonEquals(other.angularVelocity, epsilon))
         return false;

      return true;
   }
}