package us.ihmc.humanoidRobotics.footstep.footstepSnapper;

import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage;
import us.ihmc.robotics.geometry.ConvexPolygon2d;

/**
 * Created by agrabertilton on 1/20/15.
 */
public class SimpleFootstepValueFunction implements FootstepValueFunction
{
   private FootstepSnappingParameters parameters;
   private double slopeGain = -0.25; //more slope is bad
   private double areaGain = 100; //more area is good

   public SimpleFootstepValueFunction(FootstepSnappingParameters parameters){
      this.parameters = parameters;
      updateFunction();
   }

   @Override
   public double getFootstepValue(FootstepDataMessage footstepData)
   {
      Matrix3d rotationMatrix = new Matrix3d();
      rotationMatrix.set(footstepData.getOrientation());
      Vector3d footstepNormal = new Vector3d();
      rotationMatrix.getColumn(2, footstepNormal);

      double offHorizontalAngle = Math.acos(footstepNormal.getZ());

      if (offHorizontalAngle > parameters.getMaxAngle() || footstepNormal.getZ() < 0)
         return Double.NEGATIVE_INFINITY;

      double value = slopeGain * offHorizontalAngle;
      if (footstepData.predictedContactPoints == null || footstepData.predictedContactPoints.isEmpty())
         return Double.NEGATIVE_INFINITY;

      ConvexPolygon2d supportPolygon = new ConvexPolygon2d(footstepData.getPredictedContactPoints());
      supportPolygon.update();
      double inPlaneArea = supportPolygon.getArea();
      double horizonalArea = inPlaneArea * footstepNormal.getZ();

      if (horizonalArea < parameters.getMinArea())
         return Double.NEGATIVE_INFINITY;

      value += horizonalArea * areaGain;
      return value;
   }

   @Override
   public void updateFunction()
   {
   }
}
