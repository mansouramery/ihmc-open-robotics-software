package us.ihmc.robotics.referenceFrames;

import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.geometry.RigidBodyTransform;

import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

public class MidFrameZUpFrame extends ReferenceFrame
{
   private static final long serialVersionUID = -2229169827166093475L;
   private final ReferenceFrame parentZUpFrame, frameOne, frameTwo;

   private final FramePoint originOneInParent;
   private final FramePoint originTwoInParent;
   private final FrameVector vectorBetweenFrames;
   private final Vector2d vectorBetweenFrames2d = new Vector2d();
   private final Vector3d parentToMidpointVector3d = new Vector3d();
   private final RigidBodyTransform transform3D = new RigidBodyTransform();

   public MidFrameZUpFrame(String name, ReferenceFrame parentZUpFrame, ReferenceFrame frameOne, ReferenceFrame frameTwo)
   {
      super(name, parentZUpFrame, false, false, true);

      if (!parentZUpFrame.isZupFrame())
      {
         throw new RuntimeException("!parentZUpFrame.isZupFrame()");
      }

      this.parentZUpFrame = parentZUpFrame;
      this.frameOne = frameOne;
      this.frameTwo = frameTwo;

      originOneInParent = new FramePoint(frameOne);
      originTwoInParent = new FramePoint(frameTwo);
      vectorBetweenFrames = new FrameVector(parentZUpFrame);
   }

   @Override
   protected void updateTransformToParent(RigidBodyTransform transformToParent)
   {
      // Everything is based on the feet positions in the body frame:
      originOneInParent.setToZero(frameOne);
      originOneInParent.changeFrame(parentZUpFrame);

      originTwoInParent.setToZero(frameTwo);
      originTwoInParent.changeFrame(parentZUpFrame);

      // Rotation is to yaw onto the line between the feet:

      vectorBetweenFrames.sub(originTwoInParent, originOneInParent);

      vectorBetweenFrames2d.set(vectorBetweenFrames.getX(), vectorBetweenFrames.getY());


      // Translation is average between the two feet:
      parentToMidpointVector3d.add(originOneInParent.getPoint(), originTwoInParent.getPoint());
      parentToMidpointVector3d.scale(0.5);

      // Create the transform:
      transform3D.setIdentity();

      if (vectorBetweenFrames2d.lengthSquared() < 1e-7)
         return;

      vectorBetweenFrames2d.normalize();
      transform3D.setRotationYawAndZeroTranslation(Math.PI / 2.0 + Math.atan2(vectorBetweenFrames2d.y, vectorBetweenFrames2d.x));

      transform3D.setTranslation(parentToMidpointVector3d);

      transformToParent.set(transform3D);
   }
}