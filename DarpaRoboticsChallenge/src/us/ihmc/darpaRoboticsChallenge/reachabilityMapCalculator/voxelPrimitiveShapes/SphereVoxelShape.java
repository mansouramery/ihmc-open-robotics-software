package us.ihmc.darpaRoboticsChallenge.reachabilityMapCalculator.voxelPrimitiveShapes;

import java.awt.Color;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import us.ihmc.graphics3DAdapter.graphics.Graphics3DObject;
import us.ihmc.graphics3DAdapter.graphics.appearances.AppearanceDefinition;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.robotics.MathTools;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.geometry.SpiralBasedAlgorithm;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;

/**
 * SphereVoxelShape creates N points uniformly distributed on the surface of a sphere.
 * For each point, a ray, which goes from the point on the surface of the sphere to its origin, is generated.
 * For each ray M rotations are generated by computing the orientation aligning the x-axis to the ray and transforming this orientation by M rotations around the ray.
 * 
 * This class is meant to help discretizing the 3D space of orientations and also to simulate the different possibilities for grasping a spherical object.
 *
 */
public class SphereVoxelShape
{
   public enum SphereVoxelType {graspOrigin, graspAroundSphere};
   private final Quat4d[][] rotations;
   private final Point3d[] pointsOnSphere;
   /** Origin of the sphere in the current voxel coordinate. Should probably always be set to zero. */
   private final Point3d sphereOrigin = new Point3d();
   private final double voxelSize;

   private final int numberOfRays;
   private final int numberOfRotationsAroundRay;

   private final SphereVoxelType type;
   private final ReferenceFrame parentFrame;
   
   public SphereVoxelShape(ReferenceFrame parentFrame, double voxelSize, int numberOfRays, int numberOfRotationsAroundRay, SphereVoxelType type)
   {
      this.voxelSize = voxelSize;
      this.parentFrame = parentFrame;
      this.type = type;
      this.numberOfRays = numberOfRays;
      this.numberOfRotationsAroundRay = numberOfRotationsAroundRay;

      pointsOnSphere = SpiralBasedAlgorithm.generatePointsOnSphere(sphereOrigin, voxelSize, numberOfRays);
      rotations = SpiralBasedAlgorithm.generateOrientations(numberOfRays, numberOfRotationsAroundRay);
   }

   public int getNumberOfRays()
   {
      return numberOfRays;
   }

   public int getNumberOfRotationsAroundRay()
   {
      return numberOfRotationsAroundRay;
   }

   public void getRay(Vector3d rayToPack, int rayIndex)
   {
      MathTools.checkIfInRange(rayIndex, 0, numberOfRays - 1);

      rayToPack.sub(sphereOrigin, pointsOnSphere[rayIndex]);
      rayToPack.normalize();
   }

   public void getOrientation(FrameOrientation orientation, int rayIndex, int rotationAroundRayIndex)
   {
      MathTools.checkIfInRange(rayIndex, 0, numberOfRays - 1);
      MathTools.checkIfInRange(rotationAroundRayIndex, 0, numberOfRotationsAroundRay - 1);

      orientation.setIncludingFrame(parentFrame, rotations[rayIndex][rotationAroundRayIndex]);
   }

   public void getPose(FrameVector translationFromVoxelOrigin, FrameOrientation orientation, int rayIndex, int rotationAroundRayIndex)
   {
      MathTools.checkIfInRange(rayIndex, 0, numberOfRays - 1);
      MathTools.checkIfInRange(rotationAroundRayIndex, 0, numberOfRotationsAroundRay - 1);

      if (type == SphereVoxelType.graspAroundSphere)
         translationFromVoxelOrigin.setIncludingFrame(parentFrame, pointsOnSphere[rayIndex]);
      else
         translationFromVoxelOrigin.setToZero(parentFrame);
      orientation.setIncludingFrame(parentFrame, rotations[rayIndex][rotationAroundRayIndex]);
   }

   public Point3d[] getPointsOnSphere()
   {
      return pointsOnSphere;
   }

   public Graphics3DObject createVisualization(FramePoint voxelLocation, double scale, double reachabilityValue)
   {
      ReferenceFrame originalFrame = voxelLocation.getReferenceFrame();
      voxelLocation.changeFrame(ReferenceFrame.getWorldFrame());

      Graphics3DObject voxelViz = new Graphics3DObject();

      AppearanceDefinition appearance = YoAppearance.RGBColorFromHex(Color.HSBtoRGB((float) (0.7 * reachabilityValue), 1.0f, 1.0f));

      voxelViz.translate(voxelLocation.getX(), voxelLocation.getY(), voxelLocation.getZ());
      voxelViz.addSphere(scale * voxelSize / 2.0, appearance);

      voxelLocation.changeFrame(originalFrame);

      return voxelViz;
   }
}
