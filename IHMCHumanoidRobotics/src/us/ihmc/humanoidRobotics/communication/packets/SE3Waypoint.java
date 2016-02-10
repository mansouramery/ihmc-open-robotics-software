package us.ihmc.humanoidRobotics.communication.packets;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import us.ihmc.communication.packetAnnotations.ClassDocumentation;
import us.ihmc.communication.packetAnnotations.FieldDocumentation;
import us.ihmc.robotics.MathTools;

@ClassDocumentation("This class is used to build trajectory messages in taskspace. It holds the necessary information for one waypoint. "
      + "Feel free to look at EuclideanWaypoint (translational) and SO3Waypoint (rotational)")
public class SE3Waypoint
{
   @FieldDocumentation("Time at which the waypoint has to be reached. The time is relative to when the trajectory starts.")
   public double time;
   @FieldDocumentation("Define the desired 3D position to be reached at this waypoint. It is expressed in world frame.")
   public Point3d position;
   @FieldDocumentation("Define the desired 3D orientation to be reached at this waypoint. It is expressed in world frame.")
   public Quat4d orientation;
   @FieldDocumentation("Define the desired 3D linear velocity to be reached at this waypoint. It is expressed in world frame.")
   public Vector3d linearVelocity;
   @FieldDocumentation("Define the desired 3D angular velocity to be reached at this waypoint. It is expressed in world frame.")
   public Vector3d angularVelocity;

   public SE3Waypoint()
   {
   }

   public SE3Waypoint(SE3Waypoint se3Waypoint)
   {
      if (se3Waypoint.position != null)
         position = new Point3d(se3Waypoint.position);
      if (se3Waypoint.orientation != null)
         orientation = new Quat4d(se3Waypoint.orientation);
      if (se3Waypoint.linearVelocity != null)
         linearVelocity = new Vector3d(se3Waypoint.linearVelocity);
      if (se3Waypoint.angularVelocity != null)
         angularVelocity = new Vector3d(se3Waypoint.angularVelocity);
      time = se3Waypoint.time;
   }

   public SE3Waypoint(double time, Point3d position, Quat4d orientation, Vector3d linearVelocity, Vector3d angularVelocity)
   {
      this.time = time;
      this.position = position;
      this.orientation = orientation;
      this.linearVelocity = linearVelocity;
      this.angularVelocity = angularVelocity;
   }

   public double getTime()
   {
      return time;
   }

   public void setTime(double time)
   {
      this.time = time;
   }

   public Point3d getPosition()
   {
      return position;
   }

   public void setPosition(Point3d position)
   {
      this.position = position;
   }

   public Quat4d getOrientation()
   {
      return orientation;
   }

   public void setOrientation(Quat4d orientation)
   {
      this.orientation = orientation;
   }

   public Vector3d getLinearVelocity()
   {
      return linearVelocity;
   }

   public void setLinearVelocity(Vector3d linearVelocity)
   {
      this.linearVelocity = linearVelocity;
   }

   public Vector3d getAngularVelocity()
   {
      return angularVelocity;
   }

   public void setAngularVelocity(Vector3d angularVelocity)
   {
      this.angularVelocity = angularVelocity;
   }

   public boolean epsilonEquals(SE3Waypoint other, double epsilon)
   {
      if (position == null && other.position != null)
         return false;
      if (position != null && other.position == null)
         return false;

      if (orientation == null && other.orientation != null)
         return false;
      if (orientation != null && other.orientation == null)
         return false;

      if (linearVelocity == null && other.linearVelocity != null)
         return false;
      if (linearVelocity != null && other.linearVelocity == null)
         return false;

      if (angularVelocity == null && other.angularVelocity != null)
         return false;
      if (angularVelocity != null && other.angularVelocity == null)
         return false;

      if (!MathTools.epsilonEquals(time, other.time, epsilon))
         return false;
      if (!position.epsilonEquals(other.position, epsilon))
         return false;
      if (!orientation.epsilonEquals(other.orientation, epsilon))
         return false;
      if (!linearVelocity.epsilonEquals(other.linearVelocity, epsilon))
         return false;
      if (!angularVelocity.epsilonEquals(other.angularVelocity, epsilon))
         return false;

      return true;
   }
}