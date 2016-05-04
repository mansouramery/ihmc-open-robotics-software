package us.ihmc.quadrupedRobotics.estimator;

import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.geometry.LeastSquaresZPlaneFitter;
import us.ihmc.robotics.geometry.shapes.Plane3d;
import us.ihmc.robotics.math.frames.YoFramePoint;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.QuadrantDependentList;
import us.ihmc.robotics.robotSide.RobotQuadrant;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.List;

public class GroundPlaneEstimator
{
   private final static int MAX_GROUND_PLANE_POINTS = 100;
   private final Plane3d groundPlane = new Plane3d();
   private final Vector3d groundPlaneNormal = new Vector3d();
   private final Vector3d groundPlanePoint = new Vector3d();
   private final ArrayList<Point3d> groundPlanePoints = new ArrayList<>(MAX_GROUND_PLANE_POINTS);
   private final LeastSquaresZPlaneFitter planeFitter = new LeastSquaresZPlaneFitter();
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
   private final YoFrameVector yoGroundPlaneNormal = new YoFrameVector("groundPlaneNormal", ReferenceFrame.getWorldFrame(), registry);
   private final YoFramePoint yoGroundPlanePoint = new YoFramePoint("groundPlanePoint", ReferenceFrame.getWorldFrame(), registry);;

   public GroundPlaneEstimator()
   {
      this(null);
   }

   public GroundPlaneEstimator(YoVariableRegistry parentRegistry)
   {

      if (parentRegistry != null)
      {
         parentRegistry.addChild(registry);
      }
   }

   /**
    * @return pitch angle of ground plane in World Frame
    */
   public double getPitch()
   {
      return getPitch(0);
   }

   /**
    * @return pitch angle of ground plane in World Frame
    */
   public double getRoll()
   {
      return getRoll(0);
   }

   /**
    * @param yaw : angle Of ZUp frame relative to World Frame
    * @return pitch angle of ground plane in ZUp Frame
    */
   public double getPitch(double yaw)
   {
      groundPlane.getNormal(groundPlaneNormal);
      return Math.atan2(Math.cos(yaw) * groundPlaneNormal.getX() - Math.sin(yaw) * groundPlaneNormal.getY(), groundPlaneNormal.getZ());
   }

   /**
    * @param yaw : angle Of ZUp frame relative to World Frame
    * @return roll angle of ground plane in ZUp Frame
    */
   public double getRoll(double yaw)
   {
      groundPlane.getNormal(groundPlaneNormal);
      return Math.atan2(Math.sin(yaw) * groundPlaneNormal.getX() + Math.cos(yaw) * groundPlaneNormal.getY(), groundPlaneNormal.getZ());
   }

   /**
    * @param plane3d : ground plane in World Frame
    */
   public void getPlane(Plane3d plane3d)
   {
      plane3d.set(groundPlane);
   }

   /**
    * @param point : ground plane point in World Frame
    */
   public void getPlanePoint(FramePoint point)
   {
      point.changeFrame(ReferenceFrame.getWorldFrame());
      groundPlane.getPoint(point.getPoint());
   }

   /**
    * @param normal : ground plane normal in World Frame
    */
   public void getPlaneNormal(FrameVector normal)
   {
      normal.changeFrame(ReferenceFrame.getWorldFrame());
      groundPlane.getNormal(normal.getVector());
   }

   /**
    * @param point : point to be vertically projected onto ground plane
    */
   public void projectZ(FramePoint point)
   {
      point.changeFrame(ReferenceFrame.getWorldFrame());
      point.setZ(groundPlane.getZOnPlane(point.getX(), point.getY()));
   }

   /**
    * @param point : point to be orthogonally projected onto ground plane
    */
   public void projectOrthogonal(FramePoint point)
   {
      point.changeFrame(ReferenceFrame.getWorldFrame());
      groundPlane.orthogonalProjection(point.getPoint());
   }

   /**
    * @param contactPoints : list of ground contact points
    */
   public void compute(List<FramePoint> contactPoints)
   {
      int nPoints = Math.min(contactPoints.size(), MAX_GROUND_PLANE_POINTS);
      groundPlanePoints.clear();
      for (int i = 0; i < nPoints; i++)
      {
         contactPoints.get(i).changeFrame(ReferenceFrame.getWorldFrame());
         groundPlanePoints.add(contactPoints.get(i).getPoint());
      }
      compute();
   }

   /**
    * @param contactPoints : contact points during quad support
    */
   public void compute(QuadrantDependentList<FramePoint> contactPoints)
   {
      groundPlanePoints.clear();
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         contactPoints.get(robotQuadrant).changeFrame(ReferenceFrame.getWorldFrame());
         groundPlanePoints.add(contactPoints.get(robotQuadrant).getPoint());
      }
      compute();
   }

   private void compute()
   {
      planeFitter.fitPlaneToPoints(groundPlanePoints, groundPlane);
      groundPlane.getNormal(groundPlaneNormal);
      yoGroundPlaneNormal.set(groundPlaneNormal);
      groundPlane.getNormal(groundPlanePoint);
      yoGroundPlanePoint.set(groundPlanePoint);
   }
}