package us.ihmc.simulationconstructionset.util.perturbance;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import us.ihmc.graphics3DAdapter.graphics.Graphics3DObject;
import us.ihmc.graphics3DAdapter.graphics.instructions.primitives.Graphics3DScaleInstruction;
import us.ihmc.simulationconstructionset.FloatingJoint;
import us.ihmc.simulationconstructionset.Link;
import us.ihmc.simulationconstructionset.Robot;

public class LaunchedBall extends FloatingJoint
{
   private static final long serialVersionUID = -1070304629726153858L;

   private boolean launched = false;

   private final Point3d finalPosition = new Point3d();
   private final Point3d currentPosition = new Point3d();
   private final Vector3d directionVector = new Vector3d();
   private final Vector3d velocityVector = new Vector3d();
   private final double collisionDistance;
   private final double density;

   private final Graphics3DScaleInstruction linkGraphicsScale;

   public LaunchedBall(String name, Robot robot, double collisionDistance, double density)
   {
      super(name, name, new Vector3d(), robot);

      setDynamic(false);

      setPositionAndVelocity(1000.0, 1000.0, -1000.0, 0.0, 0.0, 0.0); // Hide them away at the start.

      Link link = new Link(name);
      Graphics3DObject linkGraphics = new Graphics3DObject();
      linkGraphics.setChangeable(true);
      linkGraphicsScale = linkGraphics.scale(1.0);

      linkGraphics.addSphere(0.1);

      link.setLinkGraphics(linkGraphics);
      setLink(link);

      robot.addRootJoint(this);

      this.collisionDistance = collisionDistance;
      this.density = density;
   }

   public boolean isCloseToFinalPosition()
   {
      if (!launched)
         return false;

      this.getPosition(currentPosition);

      return currentPosition.epsilonEquals(finalPosition, collisionDistance);
   }

   public void launch(Point3d initialPosition, Point3d finalPosition, double mass, double velocityMagnitude)
   {
      updateBallSize(mass);
      updatePointsAndVectors(initialPosition, finalPosition, velocityMagnitude);
      setPosition(initialPosition);
      setVelocity(velocityVector);

      this.launched = true;
   }

   public void bounceAwayAfterCollision()
   {
      this.launched = false;
      velocityVector.scale(-1.0);
      final double zVelocityAfterBounce = -10.0;
      velocityVector.setZ(zVelocityAfterBounce);
      setVelocity(velocityVector);
   }

   private void updatePointsAndVectors(Point3d initialPosition, Point3d finalPosition, double velocityMagnitude)
   {
      this.finalPosition.set(finalPosition);

      directionVector.set(finalPosition);
      directionVector.sub(initialPosition);
      directionVector.normalize();

      velocityVector.set(directionVector);
      velocityVector.scale(velocityMagnitude);
   }

   private void updateBallSize(double mass)
   {
      double volume = mass / density;
      double radius = Math.pow(volume / (4.0 / 3.0 * Math.PI), 1.0 / 3.0);

      linkGraphicsScale.setScale(radius);
   }

   public Vector3d getDirection()
   {
      return directionVector;
   }
}
